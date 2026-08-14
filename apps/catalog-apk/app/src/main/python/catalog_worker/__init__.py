"""Catalog APK embedded Python entry — Chaquopy zero-host worker."""

from __future__ import annotations

import json
import logging
import runpy
import sys
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


def run_job(
    argv: list[str] | None = None,
    pause_flag: str | None = None,
    heartbeat_path: str | None = None,
) -> int:
    """Run orchestrator argv inside the embedded interpreter."""
    _ensure_logging()
    args = list(argv or [])
    if pause_flag and "--pause-flag" not in args:
        args.extend(["--pause-flag", pause_flag])

    _write_heartbeat(heartbeat_path, {"phase": "start", "argv": args})

    if len(args) < 2 or args[0] != "-m":
        logger.error("Expected argv starting with -m <module>, got %s", args)
        return EXIT_FAIL

    module = args[1]
    module_argv = args[2:]
    sys.argv = [module, *module_argv]
    try:
        mod = __import__(module, fromlist=["main"])
        main_fn = getattr(mod, "main", None)
        if callable(main_fn):
            code = int(main_fn(module_argv) or 0)
        else:
            runpy.run_module(module, run_name="__main__", alter_sys=True)
            code = EXIT_OK
    except SystemExit as exc:
        code = int(exc.code or 0) if isinstance(exc.code, int) else EXIT_FAIL
    except Exception as exc:  # noqa: BLE001
        logger.exception("catalog_worker failed: %s", exc)
        _write_heartbeat(heartbeat_path, {"phase": "error", "error": str(exc)})
        return EXIT_FAIL

    if code == EXIT_PAUSED:
        _write_heartbeat(heartbeat_path, {"phase": "paused", "code": code})
        return EXIT_PAUSED

    _write_heartbeat(heartbeat_path, {"phase": "done", "code": code})
    return code


def ping() -> str:
    return "catalog_worker_ok"
