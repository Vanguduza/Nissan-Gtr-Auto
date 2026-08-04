"""Orchestrate PartSouq multi-make catalog: scrape + cache parse in real time.

Per maker, starts ``amayama_catalog_auto`` (crawl) and ``cache_parse_worker --watch``
together. Vid → chassis → vin_prefix mapping, stamp/backfill, and missed-cache
catch-up run **inside** the parse watcher — no separate mapping process.

Usage (from ``data-pipeline/``)::

  python -m data_pipeline.partsouq_catalog_orchestrator --makers all
  python -m data_pipeline.partsouq_catalog_orchestrator --makers Toyota
  python -m data_pipeline.partsouq_catalog_orchestrator --makers Toyota,Honda,Nissan

Default: makers sequential (queue); scrape + parse concurrent within each maker.
``--parallel-makers N`` runs up to N makers at once (high FlareSolverr/disk cost).
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import signal
import subprocess
import sys
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urljoin

from data_pipeline.catalogue_watchdog import process_alive

logger = logging.getLogger("data_pipeline.partsouq_catalog_orchestrator")

PACKAGE_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_SCRAPE_CONFIG = PACKAGE_ROOT / "config" / "scrape.json"
DEFAULT_MAKERS_FILE = PACKAGE_ROOT / "config" / "partsouq_makers.json"
DEFAULT_OUT_ROOT = Path("out/makers")
DEFAULT_FLARESOLVERR_HEALTH = "http://127.0.0.1:8191"
DEFAULT_BASE_URL = "https://partsouq.com"
DEFAULT_LOCATE_PATH = "/en/catalog/genuine/locate"

CRAWL_MODULE = "data_pipeline.amayama_catalog_auto"
PARSE_MODULE = "data_pipeline.cache_parse_worker"


# ---------------------------------------------------------------------------
# Paths / maker selection
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class MakerPaths:
    """Isolated filesystem layout for one manufacturer catalog."""

    maker: str
    slug: str
    root: Path
    state_db: Path
    cache_dir: Path
    out_dir: Path
    parse_db: Path
    diagrams_dir: Path
    session_dir: Path
    scrape_config: Path
    crawl_pid: Path
    parse_pid: Path
    crawl_log: Path
    crawl_err_log: Path
    parse_log: Path
    parse_err_log: Path
    meta_json: Path

    @property
    def diagram_storage_prefix(self) -> str:
        return f"partsouq/{self.slug}"

    @property
    def start_url(self) -> str:
        return f"{DEFAULT_BASE_URL}{DEFAULT_LOCATE_PATH}?c={self.maker}"

    def as_manifest_entry(self) -> dict[str, Any]:
        return {
            "maker": self.maker,
            "slug": self.slug,
            "root": str(self.root.as_posix()),
            "state_db": str(self.state_db.as_posix()),
            "cache_dir": str(self.cache_dir.as_posix()),
            "out_dir": str(self.out_dir.as_posix()),
            "parse_db": str(self.parse_db.as_posix()),
            "diagrams_dir": str(self.diagrams_dir.as_posix()),
            "scrape_config": str(self.scrape_config.as_posix()),
            "diagram_storage_prefix": self.diagram_storage_prefix,
            "start_url": self.start_url,
            "allowed_brand": self.slug,
        }


def maker_slug(name: str) -> str:
    """Filesystem-safe lowercase slug (``Mercedes-Benz`` → ``mercedes-benz``)."""
    text = name.strip().lower()
    text = re.sub(r"[^\w\s-]", "", text, flags=re.UNICODE)
    text = re.sub(r"[\s_]+", "-", text).strip("-")
    return text or "unknown"


def build_maker_paths(maker: str, out_root: Path) -> MakerPaths:
    slug = maker_slug(maker)
    root = out_root / slug
    return MakerPaths(
        maker=maker.strip(),
        slug=slug,
        root=root,
        state_db=root / "crawler_state.db",
        cache_dir=root / "cache",
        out_dir=root / "bundle",
        parse_db=root / "cache_parse_state.db",
        diagrams_dir=root / "diagrams",
        session_dir=root / "browser_session",
        scrape_config=root / "scrape.json",
        crawl_pid=root / "crawl.pid",
        parse_pid=root / "parse.pid",
        crawl_log=root / "crawl.log",
        crawl_err_log=root / "crawl.err.log",
        parse_log=root / "parse.log",
        parse_err_log=root / "parse.err.log",
        meta_json=root / "meta.json",
    )


def load_makers_file(path: Path) -> tuple[list[str], dict[str, Any]]:
    if not path.exists():
        raise FileNotFoundError(f"Makers file not found: {path}")
    raw = path.read_text(encoding="utf-8")
    if path.suffix.lower() in {".yaml", ".yml"}:
        try:
            import yaml  # type: ignore[import-untyped]
        except ImportError as exc:
            raise RuntimeError(
                "PyYAML required for .yaml makers files; use config/partsouq_makers.json"
            ) from exc
        data = yaml.safe_load(raw)
    else:
        data = json.loads(raw)
    if isinstance(data, list):
        makers = [str(m).strip() for m in data if str(m).strip()]
        return makers, {"makers": makers}
    if not isinstance(data, dict):
        raise ValueError(f"Makers file must be a list or object: {path}")
    makers_raw = data.get("makers") or data.get("brands") or []
    makers = [str(m).strip() for m in makers_raw if str(m).strip()]
    if not makers:
        raise ValueError(f"No makers listed in {path}")
    return makers, data


def normalize_maker_token(token: str) -> str:
    return re.sub(r"[\s_]+", "-", token.strip().lower())


def resolve_makers(selection: str, available: list[str]) -> list[str]:
    """Resolve ``all`` | ``Toyota`` | ``Toyota,Honda,Nissan`` against *available*."""
    text = (selection or "").strip()
    if not text:
        raise ValueError("--makers requires a value (all | Name | Name,Name,...)")

    by_slug = {normalize_maker_token(name): name for name in available}

    if normalize_maker_token(text) == "all":
        return list(available)

    requested = [p.strip() for p in text.split(",") if p.strip()]
    if not requested:
        raise ValueError("--makers list is empty")

    resolved: list[str] = []
    seen: set[str] = set()
    unknown: list[str] = []
    for token in requested:
        key = normalize_maker_token(token)
        match = by_slug.get(key)
        if match is None:
            match = token.strip()
            unknown.append(match)
        nkey = normalize_maker_token(match)
        if nkey in seen:
            continue
        seen.add(nkey)
        resolved.append(match)

    if unknown:
        logger.warning(
            "Makers not in curated list (will still run with c=<Name>): %s",
            ", ".join(unknown),
        )
    return resolved


# ---------------------------------------------------------------------------
# Config / manifest
# ---------------------------------------------------------------------------


def write_maker_scrape_config(
    *,
    base_config_path: Path,
    paths: MakerPaths,
    flaresolverr_url: str | None = None,
) -> Path:
    if base_config_path.exists():
        with base_config_path.open(encoding="utf-8") as fh:
            data = json.load(fh)
    else:
        data = {}

    data["start_url"] = paths.start_url
    data["allowed_brand"] = paths.slug
    data["diagram_storage_prefix"] = paths.diagram_storage_prefix
    data["session_dir"] = str(paths.session_dir.as_posix())
    data["flaresolverr_session_name"] = f"gtr-catalog-{paths.slug}"
    # Absolute path so maker cwd / worker always finds multi-make VIN maps
    catalogs = PACKAGE_ROOT / "config" / "chassis_catalogs.json"
    data["chassis_catalogs_path"] = str(catalogs.resolve())
    if flaresolverr_url:
        data["flaresolverr_url"] = flaresolverr_url
    proxies = data.get("proxies_file") or "config/proxies.json"
    if not Path(proxies).is_absolute():
        pkg_proxies = PACKAGE_ROOT / proxies
        if pkg_proxies.exists():
            data["proxies_file"] = str(pkg_proxies.resolve())

    paths.root.mkdir(parents=True, exist_ok=True)
    paths.scrape_config.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    return paths.scrape_config


def write_manifest(
    out_root: Path,
    entries: list[dict[str, Any]],
    *,
    extra: dict[str, Any] | None = None,
) -> Path:
    out_root.mkdir(parents=True, exist_ok=True)
    payload: dict[str, Any] = {
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "out_root": str(out_root.as_posix()),
        "makers": entries,
    }
    if extra:
        payload.update(extra)
    path = out_root / "manifest.json"
    path.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    return path


def write_maker_meta(paths: MakerPaths, **fields: Any) -> Path:
    paths.root.mkdir(parents=True, exist_ok=True)
    payload = {
        **paths.as_manifest_entry(),
        "updated_at": datetime.now(timezone.utc).isoformat(),
        **fields,
    }
    paths.meta_json.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    return paths.meta_json


# ---------------------------------------------------------------------------
# FlareSolverr + discovery
# ---------------------------------------------------------------------------


def flaresolverr_health_sync(base_url: str = DEFAULT_FLARESOLVERR_HEALTH) -> bool:
    try:
        import httpx
    except ImportError:
        logger.warning("httpx not installed; cannot probe FlareSolverr")
        return False

    base = base_url.rstrip("/")
    if base.endswith("/v1"):
        base = base[: -len("/v1")]
    try:
        with httpx.Client(timeout=5.0) as client:
            return client.get(f"{base}/health").status_code == 200
    except Exception as exc:  # noqa: BLE001
        logger.debug("FlareSolverr health failed: %s", exc)
        return False


def extract_makers_from_locate_html(html: str) -> list[str]:
    found: list[str] = []
    seen: set[str] = set()
    for match in re.finditer(r"""[?&]c=([^"'&\s<>]+)""", html, flags=re.IGNORECASE):
        raw = match.group(1).strip()
        if not raw or len(raw) > 40 or re.search(r"\d{6,}", raw):
            continue
        key = normalize_maker_token(raw)
        if key in seen or key in {"", "all"}:
            continue
        seen.add(key)
        found.append(raw)
    return found


def discover_makers_via_flaresolverr(
    *,
    base_url: str = DEFAULT_BASE_URL,
    locate_path: str = DEFAULT_LOCATE_PATH,
    flaresolverr_api: str = "http://127.0.0.1:8191/v1",
) -> list[str]:
    import asyncio

    from data_pipeline.amayama_catalog_auto import flaresolverr_request

    url = urljoin(base_url.rstrip("/") + "/", locate_path.lstrip("/"))
    solution = asyncio.run(flaresolverr_request(url, api_url=flaresolverr_api))
    html = (solution or {}).get("response") or ""
    makers = extract_makers_from_locate_html(html)
    if not makers:
        raise RuntimeError("Discovery returned no brand tokens from locate HTML")
    return makers


# ---------------------------------------------------------------------------
# Process supervision (PID files; never kill unrelated PIDs)
# ---------------------------------------------------------------------------


def read_pid(path: Path) -> int | None:
    if not path.exists():
        return None
    try:
        return int(path.read_text(encoding="utf-8").strip())
    except (OSError, ValueError):
        return None


def write_pid(path: Path, pid: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(str(pid), encoding="utf-8")


def clear_pid(path: Path) -> None:
    try:
        path.unlink(missing_ok=True)  # type: ignore[call-arg]
    except TypeError:
        if path.exists():
            path.unlink()


def assert_no_stale_owned_process(pid_path: Path, label: str) -> None:
    pid = read_pid(pid_path)
    if pid is None:
        return
    if process_alive(pid):
        raise RuntimeError(
            f"{label} appears already running (pid={pid}, file={pid_path}). "
            "Stop it first or remove the PID file if it is stale."
        )
    clear_pid(pid_path)


def _popen_kwargs() -> dict[str, Any]:
    kwargs: dict[str, Any] = {}
    if sys.platform == "win32":
        kwargs["creationflags"] = subprocess.CREATE_NEW_PROCESS_GROUP  # type: ignore[attr-defined]
    return kwargs


def start_logged_process(
    argv: list[str],
    *,
    cwd: Path,
    log_path: Path,
    err_path: Path,
) -> subprocess.Popen[Any]:
    log_path.parent.mkdir(parents=True, exist_ok=True)
    out_fh = log_path.open("a", encoding="utf-8")
    err_fh = err_path.open("a", encoding="utf-8")
    try:
        return subprocess.Popen(  # noqa: S603
            argv,
            cwd=str(cwd),
            stdout=out_fh,
            stderr=err_fh,
            **_popen_kwargs(),
        )
    finally:
        out_fh.close()
        err_fh.close()


def terminate_owned(
    proc: subprocess.Popen[Any] | None,
    pid_path: Path,
    *,
    grace_seconds: float = 15.0,
) -> None:
    """Terminate only a process we started (Popen handle). Never sweep by name."""
    if proc is None:
        clear_pid(pid_path)
        return
    if proc.poll() is not None:
        clear_pid(pid_path)
        return

    pid = proc.pid
    logger.info("Stopping owned process pid=%s", pid)
    try:
        if sys.platform == "win32":
            proc.terminate()
        else:
            proc.send_signal(signal.SIGTERM)
    except OSError as exc:
        logger.warning("terminate failed for pid=%s: %s", pid, exc)

    deadline = time.monotonic() + grace_seconds
    while time.monotonic() < deadline and proc.poll() is None:
        time.sleep(0.25)

    if proc.poll() is None:
        logger.warning("Force-killing owned pid=%s after grace period", pid)
        try:
            proc.kill()
        except OSError:
            pass
        try:
            proc.wait(timeout=5)
        except subprocess.TimeoutExpired:
            pass
    clear_pid(pid_path)


# ---------------------------------------------------------------------------
# argv builders (pure — unit-tested without spawning)
# ---------------------------------------------------------------------------


@dataclass
class OrchestratorOptions:
    makers: list[str]
    out_root: Path = DEFAULT_OUT_ROOT
    base_config: Path = DEFAULT_SCRAPE_CONFIG
    cwd: Path = field(default_factory=Path.cwd)
    parallel_makers: int = 1
    poll_seconds: float = 20.0
    max_pages: int | None = None
    until_complete: bool = True
    local_ip: bool = True
    skip_flaresolverr_check: bool = False
    flaresolverr_health_url: str = DEFAULT_FLARESOLVERR_HEALTH
    flaresolverr_api_url: str = "http://127.0.0.1:8191/v1"
    download_diagrams: bool = False
    upload_diagrams: bool = False
    import_dry_run: bool = False
    live_import: bool = False
    transform_after: bool = True
    parse_watch: bool = True
    workers: int | None = None
    max_concurrent: int | None = None
    verbose: bool = False
    dry_run: bool = False


def build_crawl_argv(paths: MakerPaths, opts: OrchestratorOptions) -> list[str]:
    argv = [
        sys.executable,
        "-m",
        CRAWL_MODULE,
        "--config",
        str(paths.scrape_config),
        "--state-db",
        str(paths.state_db),
        "--cache-dir",
        str(paths.cache_dir),
        "--out-dir",
        str(paths.out_dir),
        "--diagrams-dir",
        str(paths.diagrams_dir),
        "--session-dir",
        str(paths.session_dir),
        "--session-name",
        f"gtr-catalog-{paths.slug}",
        "--start-url",
        paths.start_url,
        "--crawl-only",
        "--retry-failed",
    ]
    if opts.local_ip:
        argv.append("--local-ip")
    if opts.until_complete and opts.max_pages is None:
        argv.append("--until-complete")
    if opts.max_pages is not None:
        argv.extend(["--max-pages", str(opts.max_pages)])
    if opts.workers is not None:
        argv.extend(["--workers", str(opts.workers)])
    if opts.max_concurrent is not None:
        argv.extend(["--max-concurrent", str(opts.max_concurrent)])
    if opts.verbose:
        argv.append("-v")
    return argv


def build_parse_watch_argv(paths: MakerPaths, opts: OrchestratorOptions) -> list[str]:
    argv = [
        sys.executable,
        "-m",
        PARSE_MODULE,
        "--watch",
        "--poll-seconds",
        str(opts.poll_seconds),
        "--state-db",
        str(paths.state_db),
        "--cache-dir",
        str(paths.cache_dir),
        "--parse-db",
        str(paths.parse_db),
        "--out-dir",
        str(paths.out_dir),
        "--brand",
        paths.slug,
        "--scrape-config",
        str(paths.scrape_config),
    ]
    if opts.verbose:
        argv.append("-v")
    return argv


def build_parse_once_argv(paths: MakerPaths, opts: OrchestratorOptions) -> list[str]:
    argv = [
        sys.executable,
        "-m",
        PARSE_MODULE,
        "--once",
        "--state-db",
        str(paths.state_db),
        "--cache-dir",
        str(paths.cache_dir),
        "--parse-db",
        str(paths.parse_db),
        "--out-dir",
        str(paths.out_dir),
        "--brand",
        paths.slug,
        "--scrape-config",
        str(paths.scrape_config),
    ]
    if opts.verbose:
        argv.append("-v")
    return argv


def build_transform_argv(paths: MakerPaths, opts: OrchestratorOptions) -> list[str]:
    argv = [
        sys.executable,
        "-m",
        CRAWL_MODULE,
        "--config",
        str(paths.scrape_config),
        "--transform-only",
        "--state-db",
        str(paths.state_db),
        "--cache-dir",
        str(paths.cache_dir),
        "--out-dir",
        str(paths.out_dir),
        "--diagrams-dir",
        str(paths.diagrams_dir),
    ]
    if opts.download_diagrams or opts.upload_diagrams:
        argv.append("--download-diagrams")
    if opts.upload_diagrams:
        argv.append("--upload-diagrams")
    if opts.import_dry_run:
        argv.append("--import-dry-run")
    if opts.live_import:
        argv.append("--live-import")
    if opts.verbose:
        argv.append("-v")
    return argv


# ---------------------------------------------------------------------------
# Per-maker job
# ---------------------------------------------------------------------------


@dataclass
class MakerJobResult:
    maker: str
    slug: str
    ok: bool
    crawl_exit: int | None = None
    parse_final_exit: int | None = None
    transform_exit: int | None = None
    error: str | None = None
    paths: dict[str, Any] = field(default_factory=dict)


def prepare_maker(paths: MakerPaths, opts: OrchestratorOptions) -> None:
    paths.root.mkdir(parents=True, exist_ok=True)
    for d in (paths.cache_dir, paths.out_dir, paths.diagrams_dir, paths.session_dir):
        d.mkdir(parents=True, exist_ok=True)
    write_maker_scrape_config(
        base_config_path=opts.base_config,
        paths=paths,
        flaresolverr_url=opts.flaresolverr_api_url,
    )
    write_maker_meta(paths, status="prepared")


def run_maker_job(paths: MakerPaths, opts: OrchestratorOptions) -> MakerJobResult:
    """Crawl without interruption; one parse watcher handles identity + catch-up."""
    result = MakerJobResult(
        maker=paths.maker,
        slug=paths.slug,
        ok=False,
        paths=paths.as_manifest_entry(),
    )
    if opts.dry_run:
        prepare_maker(paths, opts)
        result.ok = True
        write_maker_meta(paths, status="dry_run", crawl_argv=build_crawl_argv(paths, opts))
        logger.info("[%s] dry-run prepared at %s", paths.maker, paths.root)
        return result

    prepare_maker(paths, opts)
    assert_no_stale_owned_process(paths.crawl_pid, f"crawl:{paths.slug}")
    assert_no_stale_owned_process(paths.parse_pid, f"parse:{paths.slug}")

    parse_proc: subprocess.Popen[Any] | None = None
    crawl_proc: subprocess.Popen[Any] | None = None
    write_maker_meta(paths, status="running", started_at=datetime.now(timezone.utc).isoformat())
    logger.info(
        "[%s] scrape + cache_parse_worker (vid/chassis/vin + catch-up inside watcher) → %s",
        paths.maker,
        paths.root,
    )

    try:
        # Watcher first so early cache files are not missed.
        if opts.parse_watch:
            parse_proc = start_logged_process(
                build_parse_watch_argv(paths, opts),
                cwd=opts.cwd,
                log_path=paths.parse_log,
                err_path=paths.parse_err_log,
            )
            write_pid(paths.parse_pid, parse_proc.pid)
            logger.info("[%s] parse watcher pid=%s", paths.maker, parse_proc.pid)

        crawl_proc = start_logged_process(
            build_crawl_argv(paths, opts),
            cwd=opts.cwd,
            log_path=paths.crawl_log,
            err_path=paths.crawl_err_log,
        )
        write_pid(paths.crawl_pid, crawl_proc.pid)
        logger.info("[%s] crawl pid=%s (runs until complete; not interrupted for parse)", paths.maker, crawl_proc.pid)

        result.crawl_exit = crawl_proc.wait()
        clear_pid(paths.crawl_pid)
        logger.info("[%s] crawl exited code=%s", paths.maker, result.crawl_exit)

        # Crawl finished on its own — stop only our watcher, then final catch-up.
        terminate_owned(parse_proc, paths.parse_pid)
        parse_proc = None

        once = subprocess.run(  # noqa: S603
            build_parse_once_argv(paths, opts),
            cwd=str(opts.cwd),
            check=False,
        )
        result.parse_final_exit = once.returncode

        if opts.transform_after:
            tr = subprocess.run(  # noqa: S603
                build_transform_argv(paths, opts),
                cwd=str(opts.cwd),
                check=False,
            )
            result.transform_exit = tr.returncode

        result.ok = (
            result.crawl_exit == 0
            and (result.parse_final_exit in (0, None))
            and (result.transform_exit in (0, None))
        )
        write_maker_meta(
            paths,
            status="completed" if result.ok else "failed",
            crawl_exit=result.crawl_exit,
            parse_final_exit=result.parse_final_exit,
            transform_exit=result.transform_exit,
            finished_at=datetime.now(timezone.utc).isoformat(),
        )
    except Exception as exc:  # noqa: BLE001
        result.error = str(exc)
        result.ok = False
        write_maker_meta(paths, status="error", error=str(exc))
        logger.exception("[%s] job failed: %s", paths.maker, exc)
        # On abort only: stop owned crawl if still running (do not touch unrelated PIDs).
        if crawl_proc is not None and crawl_proc.poll() is None:
            terminate_owned(crawl_proc, paths.crawl_pid)
    finally:
        terminate_owned(parse_proc, paths.parse_pid)

    return result


# ---------------------------------------------------------------------------
# Orchestrator
# ---------------------------------------------------------------------------


def run_orchestrator(opts: OrchestratorOptions) -> int:
    if not opts.makers:
        logger.error("No makers selected")
        return 2

    if not opts.skip_flaresolverr_check and not opts.dry_run:
        if not flaresolverr_health_sync(opts.flaresolverr_health_url):
            logger.error(
                "FlareSolverr not healthy at %s — start it first:\n"
                "  docker compose -f docker-compose.satellites.yml --profile scrape up -d\n"
                "Or pass --skip-flaresolverr-check (not recommended).",
                opts.flaresolverr_health_url,
            )
            return 3

    path_list = [build_maker_paths(m, opts.out_root) for m in opts.makers]
    write_manifest(
        opts.out_root,
        [p.as_manifest_entry() for p in path_list],
        extra={
            "parallel_makers": opts.parallel_makers,
            "mode": "dry_run" if opts.dry_run else "live",
            "identity_note": (
                "vid→chassis→vin_prefix + backfill + catch-up run inside "
                "cache_parse_worker per maker (no separate mapping process)."
            ),
            "resource_note": (
                "Default parallel_makers=1 queues makers one-at-a-time; within each, "
                "scrape + parse-watch run together. Raising parallel_makers multiplies "
                "FlareSolverr sessions, CPU, and disk I/O."
            ),
        },
    )

    results: list[MakerJobResult] = []
    parallel = max(1, opts.parallel_makers)

    if parallel == 1:
        for paths in path_list:
            results.append(run_maker_job(paths, opts))
    else:
        logger.warning(
            "Running up to %s makers in parallel — high FlareSolverr/disk cost",
            parallel,
        )
        with ThreadPoolExecutor(max_workers=parallel) as pool:
            futures = {pool.submit(run_maker_job, p, opts): p for p in path_list}
            for fut in as_completed(futures):
                results.append(fut.result())

    by_slug = {r.slug: r for r in results}
    ordered = [by_slug[p.slug] for p in path_list if p.slug in by_slug]
    write_manifest(
        opts.out_root,
        [
            {
                **(r.paths or {}),
                "ok": r.ok,
                "crawl_exit": r.crawl_exit,
                "parse_final_exit": r.parse_final_exit,
                "transform_exit": r.transform_exit,
                "error": r.error,
            }
            for r in ordered
        ],
        extra={
            "parallel_makers": opts.parallel_makers,
            "finished_at": datetime.now(timezone.utc).isoformat(),
            "ok_count": sum(1 for r in ordered if r.ok),
            "fail_count": sum(1 for r in ordered if not r.ok),
        },
    )

    failed = [r for r in ordered if not r.ok]
    if failed:
        logger.error(
            "Completed with failures: %s",
            ", ".join(f"{r.maker}({r.error or r.crawl_exit})" for r in failed),
        )
        return 1

    logger.info("All %s maker job(s) completed OK → %s", len(ordered), opts.out_root)
    return 0


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        description=(
            "Orchestrate PartSouq multi-make catalog: scrape + cache_parse_worker "
            "in real time (vid mapping inside watcher), isolated under out/makers/<slug>/."
        ),
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
examples:
  python -m data_pipeline.partsouq_catalog_orchestrator --makers all
  python -m data_pipeline.partsouq_catalog_orchestrator --makers Toyota
  python -m data_pipeline.partsouq_catalog_orchestrator --makers Toyota,Honda,Nissan
  python -m data_pipeline.partsouq_catalog_orchestrator --makers all --parallel-makers 2
  python -m data_pipeline.partsouq_catalog_orchestrator --makers Toyota --max-pages 30 --import-dry-run

resource notes:
  Default --parallel-makers 1 queues makers. Within each maker, crawl and
  parse-watch run together; identity/backfill/catch-up are inside the watcher.
  Parallel makers multiply FlareSolverr load — use sparingly.
""",
    )
    p.add_argument("--makers", required=True, help="all | Toyota | Toyota,Honda,Nissan")
    p.add_argument("--makers-file", type=Path, default=DEFAULT_MAKERS_FILE)
    p.add_argument(
        "--discover-makers",
        action="store_true",
        help="Try FlareSolverr locate-page discovery; fall back to --makers-file on failure",
    )
    p.add_argument("--out-root", type=Path, default=DEFAULT_OUT_ROOT)
    p.add_argument("--config", type=Path, default=DEFAULT_SCRAPE_CONFIG)
    p.add_argument("--parallel-makers", type=int, default=1)
    p.add_argument("--poll-seconds", type=float, default=20.0)
    p.add_argument("--max-pages", type=int, default=None)
    p.add_argument("--no-until-complete", action="store_true")
    p.add_argument("--workers", type=int, default=None)
    p.add_argument("--max-concurrent", type=int, default=None)
    p.add_argument("--no-local-ip", action="store_true")
    p.add_argument("--skip-flaresolverr-check", action="store_true")
    p.add_argument("--flaresolverr-url", default=DEFAULT_FLARESOLVERR_HEALTH)
    p.add_argument("--download-diagrams", action="store_true")
    p.add_argument("--upload-diagrams", action="store_true")
    p.add_argument("--import-dry-run", action="store_true")
    p.add_argument("--live-import", action="store_true")
    p.add_argument("--no-transform-after", action="store_true")
    p.add_argument("--no-parse-watch", action="store_true")
    p.add_argument("--dry-run", action="store_true", help="Prepare dirs/configs/manifest only")
    p.add_argument("--list-makers", action="store_true")
    p.add_argument("-v", "--verbose", action="store_true")
    return p


def options_from_args(args: argparse.Namespace, makers: list[str]) -> OrchestratorOptions:
    api = args.flaresolverr_url.rstrip("/")
    if not api.endswith("/v1"):
        api = api + "/v1"
    return OrchestratorOptions(
        makers=makers,
        out_root=args.out_root,
        base_config=args.config,
        cwd=Path.cwd(),
        parallel_makers=max(1, args.parallel_makers),
        poll_seconds=args.poll_seconds,
        max_pages=args.max_pages,
        until_complete=not args.no_until_complete,
        local_ip=not args.no_local_ip,
        skip_flaresolverr_check=args.skip_flaresolverr_check,
        flaresolverr_health_url=args.flaresolverr_url,
        flaresolverr_api_url=api,
        download_diagrams=args.download_diagrams,
        upload_diagrams=args.upload_diagrams,
        import_dry_run=args.import_dry_run,
        live_import=args.live_import,
        transform_after=not args.no_transform_after,
        parse_watch=not args.no_parse_watch,
        workers=args.workers,
        max_concurrent=args.max_concurrent,
        verbose=args.verbose,
        dry_run=args.dry_run,
    )


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )

    available, meta = load_makers_file(args.makers_file)
    if args.discover_makers:
        try:
            health = args.flaresolverr_url.rstrip("/")
            api = health if health.endswith("/v1") else health + "/v1"
            discovered = discover_makers_via_flaresolverr(
                base_url=str(meta.get("base_url") or DEFAULT_BASE_URL),
                locate_path=str(meta.get("locate_path") or DEFAULT_LOCATE_PATH),
                flaresolverr_api=api,
            )
            logger.info("Discovered %s makers from PartSouq locate page", len(discovered))
            available = discovered
        except Exception as exc:  # noqa: BLE001
            logger.warning("Maker discovery failed (%s); using %s", exc, args.makers_file)

    makers = resolve_makers(args.makers, available)
    if args.list_makers:
        for name in makers:
            print(name)
        return 0

    opts = options_from_args(args, makers)
    logger.info(
        "Orchestrating %s maker(s); parallel_makers=%s; out_root=%s",
        len(makers),
        opts.parallel_makers,
        opts.out_root,
    )
    return run_orchestrator(opts)


if __name__ == "__main__":
    raise SystemExit(main())
