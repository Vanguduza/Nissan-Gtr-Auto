"""Catalog APK embedded Python entry — Chaquopy zero-host worker."""

from __future__ import annotations

import json
import logging
import runpy
import sys
import threading
import time
from pathlib import Path
from typing import Any

logger = logging.getLogger("catalog_worker")

EXIT_OK = 0
EXIT_FAIL = 1
EXIT_PAUSED = 2


def _ensure_logging() -> None:
    if not logging.getLogger().handlers:
        logging.basicConfig(
            level=logging.INFO,
            format="%(asctime)s %(levelname)s %(name)s: %(message)s",
        )


def _write_heartbeat(path: str | None, payload: dict[str, Any]) -> None:
    if not path:
        return
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(payload) + "\n", encoding="utf-8")


def _as_str_list(argv: Any) -> list[str]:
    """Normalize Chaquopy Java ArrayList / arrays into a real Python list[str].

    ``list(java.util.ArrayList)`` raises ``TypeError: 'ArrayList' object is not
    iterable`` under Chaquopy — index via ``.size()`` / ``.get(i)`` instead.
    """
    if argv is None:
        return []
    if isinstance(argv, (list, tuple)):
        return [str(x) for x in argv]
    size_fn = getattr(argv, "size", None)
    get_fn = getattr(argv, "get", None)
    if callable(size_fn) and callable(get_fn):
        n = int(size_fn())
        return [str(get_fn(i)) for i in range(n)]
    try:
        return [str(x) for x in argv]
    except TypeError:
        return [str(argv)]


def _out_root_from_argv(args: list[str]) -> Path | None:
    try:
        idx = args.index("--out-root")
        return Path(args[idx + 1])
    except (ValueError, IndexError):
        return None


def _read_json(path: Path) -> dict[str, Any] | None:
    try:
        if not path.is_file():
            return None
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else None
    except (OSError, json.JSONDecodeError):
        return None


def _collect_progress(out_root: Path | None) -> dict[str, Any]:
    """Best-effort progress from orchestrator artifacts (no DB dependency)."""
    progress: dict[str, Any] = {}
    if out_root is None:
        return progress

    try:
        checkpoints = sorted(out_root.glob("*/bundle/checkpoints/page-*/checkpoint_meta.json"))
        if checkpoints:
            meta = _read_json(checkpoints[-1])
            if meta:
                progress["pages_done"] = meta.get("pages_done")
                progress["pending"] = meta.get("pending")
                progress["vehicles"] = meta.get("vehicles")
                progress["fitments"] = meta.get("fitments")
                progress["diagrams"] = meta.get("diagrams")
                progress["checkpoint"] = checkpoints[-1].parent.name
    except OSError:
        pass

    try:
        for meta_path in out_root.glob("*/meta.json"):
            meta = _read_json(meta_path)
            if not meta:
                continue
            last = meta.get("last_run") if isinstance(meta.get("last_run"), dict) else meta
            phases = last.get("phases") if isinstance(last, dict) else None
            if isinstance(phases, dict):
                crawl = phases.get("crawl") if isinstance(phases.get("crawl"), dict) else {}
                filt = phases.get("filter") if isinstance(phases.get("filter"), dict) else {}
                if crawl:
                    progress["pages_fetched"] = crawl.get("pages_fetched")
                    q = crawl.get("queue")
                    if isinstance(q, dict):
                        progress["queue"] = q
                if filt:
                    progress["diagrams"] = filt.get("diagrams") or filt.get("diagrams_out")
                    progress["fitments"] = filt.get("fitments_total") or filt.get("fitments_out")
            if meta.get("status"):
                progress["status"] = meta.get("status")
            break
    except OSError:
        pass

    try:
        pngs = list(out_root.glob("*/diagrams/**/*.png")) + list(out_root.glob("**/*.png"))
        progress["png_count"] = len({p.resolve() for p in pngs if p.is_file()})
    except OSError:
        pass

    # Live quality strip from quality_report when present
    try:
        for qpath in out_root.glob("*/bundle/quality_report.json"):
            q = _read_json(qpath)
            if q:
                progress["publishable"] = q.get("publishable")
                progress["variants_publishable"] = q.get("variants_publishable")
                if q.get("diagrams") is not None:
                    progress["diagrams"] = q.get("diagrams")
                if q.get("fitments_complete") is not None:
                    progress["fitments"] = q.get("fitments_complete")
            break
    except OSError:
        pass

    return progress


def run_job(
    argv: Any = None,
    pause_flag: str | None = None,
    heartbeat_path: str | None = None,
) -> int:
    """Run orchestrator argv inside the embedded interpreter."""
    _ensure_logging()
    args = _as_str_list(argv)
    if pause_flag and "--pause-flag" not in args:
        args.extend(["--pause-flag", pause_flag])

    _write_heartbeat(heartbeat_path, {"phase": "start", "argv": args, "ts": time.time()})

    if len(args) < 2 or args[0] != "-m":
        logger.error("Expected argv starting with -m <module>, got %s", args)
        return EXIT_FAIL

    module = args[1]
    module_argv = args[2:]
    sys.argv = [module, *module_argv]

    stop = threading.Event()
    out_root = _out_root_from_argv(args)

    def _pulse() -> None:
        while not stop.wait(5.0):
            payload: dict[str, Any] = {
                "phase": "running",
                "module": module,
                "ts": time.time(),
            }
            payload.update(_collect_progress(out_root))
            _write_heartbeat(heartbeat_path, payload)

    pulse_thread = threading.Thread(target=_pulse, name="catalog-heartbeat", daemon=True)
    pulse_thread.start()

    try:
        try:
            mod = __import__(module, fromlist=["main"])
        except Exception as exc:  # noqa: BLE001
            logger.exception("catalog_worker import failed: %s", exc)
            _write_heartbeat(
                heartbeat_path,
                {"phase": "error", "error": str(exc), "ts": time.time()},
            )
            return EXIT_FAIL

        main_fn = getattr(mod, "main", None)
        try:
            if callable(main_fn):
                code = int(main_fn(module_argv) or 0)
            else:
                runpy.run_module(module, run_name="__main__", alter_sys=True)
                code = EXIT_OK
        except SystemExit as exc:
            code = int(exc.code or 0) if isinstance(exc.code, int) else EXIT_FAIL
        except Exception as exc:  # noqa: BLE001
            logger.exception("catalog_worker failed: %s", exc)
            _write_heartbeat(
                heartbeat_path,
                {"phase": "error", "error": str(exc), "ts": time.time()},
            )
            return EXIT_FAIL
    finally:
        stop.set()
        pulse_thread.join(timeout=2.0)

    if code == EXIT_PAUSED:
        payload = {"phase": "paused", "code": code, "ts": time.time()}
        payload.update(_collect_progress(out_root))
        _write_heartbeat(heartbeat_path, payload)
        return EXIT_PAUSED

    if code != EXIT_OK:
        reason = _fail_reason_from_argv(args)
        payload = {"phase": "done", "code": code, "ts": time.time()}
        if reason:
            payload["error"] = reason
        payload.update(_collect_progress(out_root))
        _write_heartbeat(heartbeat_path, payload)
        return code

    # Hard quality gate: exit 0 only when bundle meets diagram/fitment publish rules.
    gate_error: str | None = None
    gate_meta: dict[str, Any] = {}
    if out_root is not None:
        try:
            from data_pipeline.bundle_quality_gate import assert_out_root_publishable

            gate_meta = assert_out_root_publishable(
                out_root,
                argv_hint=" ".join(args),
            )
        except Exception as exc:  # noqa: BLE001
            gate_error = str(exc)
            try:
                (out_root / "fail_reason.txt").write_text(
                    f"quality gate: {gate_error}\n",
                    encoding="utf-8",
                )
                (out_root / "pipeline_error.txt").write_text(
                    f"quality gate: {gate_error}\n",
                    encoding="utf-8",
                )
            except OSError:
                pass

    if gate_error:
        payload = {
            "phase": "quality_fail",
            "code": EXIT_FAIL,
            "error": gate_error,
            "ts": time.time(),
            "publishable": False,
        }
        payload.update(_collect_progress(out_root))
        if gate_meta:
            payload["diagrams"] = gate_meta.get("diagrams")
            payload["fitments_out"] = gate_meta.get("fitments_out")
        _write_heartbeat(heartbeat_path, payload)
        logger.error("catalog_worker quality gate FAIL: %s", gate_error)
        return EXIT_FAIL

    payload = {
        "phase": "done",
        "code": code,
        "ts": time.time(),
        "publishable": True,
        "gate_ok": True,
    }
    payload.update(_collect_progress(out_root))
    if gate_meta:
        payload["diagrams"] = gate_meta.get("diagrams")
        payload["fitments_out"] = gate_meta.get("fitments_out")
    _write_heartbeat(heartbeat_path, payload)
    return code


def _fail_reason_from_argv(args: list[str]) -> str | None:
    """Best-effort: read orchestrator fail_reason.txt next to --out-root."""
    try:
        idx = args.index("--out-root")
        out_root = Path(args[idx + 1])
    except (ValueError, IndexError):
        return None
    for name in ("fail_reason.txt", "pipeline_error.txt", "bundle_quality.txt"):
        p = out_root / name
        if p.is_file():
            text = p.read_text(encoding="utf-8", errors="replace").strip()
            if text:
                return text[:1500]
    return None


def ping() -> str:
    return "catalog_worker_ok"
