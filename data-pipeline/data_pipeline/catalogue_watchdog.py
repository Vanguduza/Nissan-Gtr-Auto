"""Watch PartSouq full-catalogue crawl; alert + safe restart on unexpected stop.

Surfaces failures for Cursor agents via:
  - ``out/catalogue_watchdog_alert.json``
  - stdout sentinel ``AGENT_LOOP_WAKE_catalogue_watchdog {...}`` (notify_on_output)

Usage::

  python -m data_pipeline.catalogue_watchdog --status
  python -m data_pipeline.catalogue_watchdog --attach-auto --restart-transient
  python -m data_pipeline.catalogue_watchdog --pid 25352 --restart-transient
"""

from __future__ import annotations

import argparse
import json
import logging
import os
import re
import sqlite3
import subprocess
import sys
import time
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

logger = logging.getLogger(__name__)

SENTINEL = "AGENT_LOOP_WAKE_catalogue_watchdog"
DEFAULT_ERR_LOG = Path("out/partsouq_full_catalogue.err.log")
DEFAULT_OUT_LOG = Path("out/partsouq_full_catalogue.log")
DEFAULT_ALERT = Path("out/catalogue_watchdog_alert.json")
DEFAULT_STATE_DB = Path("crawler_state.db")
DEFAULT_PID_FILE = Path("out/catalogue_crawl.pid")
DEFAULT_WATCHDOG_PID = Path("out/catalogue_watchdog.pid")
CRAWL_MODULE = "data_pipeline.amayama_catalog_auto"
FLARESOLVERR_URL = "http://127.0.0.1:8191/v1"
FLARESOLVERR_CONTAINER = "gtr-flaresolverr"
REPO_COMPOSE = Path("..") / "docker-compose.satellites.yml"

# Patterns that indicate the *run* is dying (not per-URL CF blocks).
FATAL_LOG_PATTERNS: list[tuple[str, re.Pattern[str]]] = [
    ("traceback", re.compile(r"^Traceback \(most recent call last\):", re.M)),
    ("flaresolverr_unreachable", re.compile(r"Connection refused|ConnectError|Failed to establish|All connection attempts failed", re.I)),
    ("flaresolverr_down", re.compile(r"FlareSolverr.*(unreachable|not running|failed|error|timeout)|Unable to connect.*8191", re.I)),
    ("session_lost", re.compile(r"Session (not found|does not exist|expired)|createSession failed|session.*invalid", re.I)),
    ("unhandled", re.compile(r"Unhandled exception|FATAL|SystemExit", re.I)),
]

TRANSIENT_KINDS = frozenset(
    {"flaresolverr_unreachable", "flaresolverr_down", "session_lost", "process_crash"}
)

# Crawl owns the queue/cache only; cache_parse_worker owns the bundle (avoids
# dual-writer checkpoint JSON races). Resume via crawler_state.db.
DEFAULT_RESTART_ARGV = [
    "-m",
    CRAWL_MODULE,
    "--local-ip",
    "--until-complete",
    "--crawl-only",
    "--out-dir",
    "out/partsouq_bundle",
    "-v",
]


@dataclass
class CrawlProcess:
    pid: int
    cmdline: str
    python_exe: str = field(default="")


@dataclass
class Alert:
    ts: str
    status: str
    reason: str
    kind: str
    crawl_pid: int | None
    exit_code: int | None
    queue: dict[str, int]
    last_log_lines: list[str]
    actions_taken: list[str]
    restart_attempted: bool
    restart_pid: int | None
    agent_prompt: str
    log_paths: dict[str, str]


def _utc_now() -> str:
    return datetime.now(timezone.utc).isoformat()


def _pipeline_root() -> Path:
    return Path(__file__).resolve().parents[1]


def process_alive(pid: int) -> bool:
    if pid <= 0:
        return False
    if sys.platform == "win32":
        import ctypes

        kernel32 = ctypes.windll.kernel32  # type: ignore[attr-defined]
        PROCESS_QUERY_LIMITED_INFORMATION = 0x1000
        handle = kernel32.OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, False, pid)
        if handle:
            kernel32.CloseHandle(handle)
            return True
        return False
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def find_crawl_processes() -> list[CrawlProcess]:
    """Locate running ``amayama_catalog_auto`` Python processes (Windows + Unix)."""
    found: list[CrawlProcess] = []
    if sys.platform == "win32":
        ps = (
            "Get-CimInstance Win32_Process -Filter \"Name='python.exe'\" | "
            "Where-Object { $_.CommandLine -match 'amayama_catalog_auto' } | "
            "ForEach-Object { \"$($_.ProcessId)|$($_.CommandLine)\" }"
        )
        try:
            proc = subprocess.run(
                ["powershell", "-NoProfile", "-Command", ps],
                capture_output=True,
                text=True,
                timeout=30,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            logger.warning("Process discovery failed: %s", exc)
            return found
        for line in (proc.stdout or "").splitlines():
            line = line.strip()
            if not line or "|" not in line:
                continue
            pid_s, cmdline = line.split("|", 1)
            try:
                pid = int(pid_s)
            except ValueError:
                continue
            exe = cmdline.split(" ", 1)[0].strip('"') if cmdline else ""
            found.append(CrawlProcess(pid=pid, cmdline=cmdline, python_exe=exe))
        return found

    try:
        proc = subprocess.run(
            ["ps", "ax", "-o", "pid=,command="],
            capture_output=True,
            text=True,
            timeout=15,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return found
    for line in (proc.stdout or "").splitlines():
        if CRAWL_MODULE not in line and "amayama_catalog_auto" not in line:
            continue
        parts = line.strip().split(None, 1)
        if len(parts) < 2:
            continue
        try:
            pid = int(parts[0])
        except ValueError:
            continue
        found.append(CrawlProcess(pid=pid, cmdline=parts[1], python_exe=parts[1].split()[0]))
    return found


def queue_status_counts(db_path: Path) -> dict[str, int]:
    if not db_path.is_file():
        return {}
    try:
        with sqlite3.connect(db_path) as conn:
            rows = conn.execute("SELECT status, COUNT(*) FROM queue GROUP BY status").fetchall()
        return {str(status): int(count) for status, count in rows}
    except sqlite3.Error as exc:
        logger.warning("queue status read failed: %s", exc)
        return {}


def flaresolverr_healthy(url: str = FLARESOLVERR_URL, timeout: float = 3.0) -> bool:
    try:
        import urllib.request

        req = urllib.request.Request(url, method="GET")
        with urllib.request.urlopen(req, timeout=timeout) as resp:  # noqa: S310
            # 405 Method Not Allowed still proves the service is up.
            return 200 <= getattr(resp, "status", 200) < 500
    except Exception:  # noqa: BLE001
        try:
            import urllib.error
            import urllib.request

            req = urllib.request.Request(
                url,
                data=b'{"cmd":"sessions.list"}',
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=timeout) as resp:  # noqa: S310
                return 200 <= getattr(resp, "status", 200) < 500
        except urllib.error.HTTPError as exc:
            return 400 <= exc.code < 500
        except Exception:  # noqa: BLE001
            return False


def ensure_flaresolverr(repo_root: Path) -> list[str]:
    """Start FlareSolverr container if down. Returns action log lines."""
    actions: list[str] = []
    if flaresolverr_healthy():
        actions.append("flaresolverr_already_up")
        return actions

    # Prefer docker start on known container name.
    start = subprocess.run(
        ["docker", "start", FLARESOLVERR_CONTAINER],
        capture_output=True,
        text=True,
        timeout=60,
        check=False,
    )
    if start.returncode == 0:
        actions.append(f"docker_start:{FLARESOLVERR_CONTAINER}")
    else:
        compose = repo_root / "docker-compose.satellites.yml"
        if compose.is_file():
            up = subprocess.run(
                [
                    "docker",
                    "compose",
                    "-f",
                    str(compose),
                    "--profile",
                    "scrape",
                    "up",
                    "-d",
                    FLARESOLVERR_CONTAINER,
                ],
                capture_output=True,
                text=True,
                timeout=120,
                check=False,
            )
            actions.append(
                f"compose_up:{FLARESOLVERR_CONTAINER}:rc={up.returncode}"
            )
        else:
            actions.append(f"docker_start_failed:{start.stderr.strip()[:200]}")

    for _ in range(20):
        if flaresolverr_healthy():
            actions.append("flaresolverr_healthy_after_start")
            return actions
        time.sleep(1.0)
    actions.append("flaresolverr_still_down")
    return actions


def tail_lines(path: Path, n: int = 40) -> list[str]:
    if not path.is_file():
        return []
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return []
    lines = text.splitlines()
    return lines[-n:]


def scan_fatal_patterns(text: str) -> list[str]:
    hits: list[str] = []
    for kind, pattern in FATAL_LOG_PATTERNS:
        if pattern.search(text):
            hits.append(kind)
    return hits


def classify_failure(log_tail: str, exit_code: int | None) -> tuple[str, str]:
    hits = scan_fatal_patterns(log_tail)
    if "session_lost" in hits:
        return "session_lost", "FlareSolverr session lost or invalid"
    if "flaresolverr_down" in hits or "flaresolverr_unreachable" in hits:
        return "flaresolverr_down", "FlareSolverr unreachable or errored"
    if "traceback" in hits or "unhandled" in hits:
        return "process_crash", "Unhandled exception / traceback in crawl logs"
    if exit_code is not None and exit_code != 0:
        return "process_crash", f"Crawl exited with code {exit_code}"
    if exit_code == 0:
        return "completed", "Crawl process exited successfully (exit 0)"
    return "unexpected_stop", "Crawl process stopped unexpectedly"


def agent_prompt_for(kind: str, reason: str, alert_path: Path) -> str:
    return (
        f"PartSouq catalogue crawl stopped ({kind}): {reason}. "
        f"Read {alert_path.as_posix()}, diagnose from "
        f"out/partsouq_full_catalogue.err.log and crawler_state.db, "
        "fix only if safe (FlareSolverr/docker/session), and restart "
        "`python -m data_pipeline.amayama_catalog_auto --local-ip --until-complete "
        "--out-dir out/partsouq_bundle --import-dry-run` if the failure looks transient. "
        "Do not kill a healthy crawl. No captcha SaaS."
    )


def write_alert(path: Path, alert: Alert) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(asdict(alert), indent=2) + "\n", encoding="utf-8")


def emit_sentinel(alert: Alert) -> None:
    payload = {
        "prompt": alert.agent_prompt,
        "status": alert.status,
        "kind": alert.kind,
        "reason": alert.reason,
        "crawl_pid": alert.crawl_pid,
        "restart_pid": alert.restart_pid,
    }
    # One line — Cursor notify_on_output / loop skill sentinel.
    print(f"{SENTINEL} {json.dumps(payload, separators=(',', ':'))}", flush=True)


def parse_restart_argv_from_cmdline(cmdline: str) -> list[str] | None:
    """Extract ``-m data_pipeline.amayama_catalog_auto ...`` args from a full command line."""
    marker = f"-m {CRAWL_MODULE}"
    alt = f'-m "{CRAWL_MODULE}"'
    idx = cmdline.find(marker)
    if idx < 0:
        idx = cmdline.find(alt)
        marker = alt
    if idx < 0:
        # Sometimes quoted module path variants
        m = re.search(r"-m\s+[\"']?data_pipeline\.amayama_catalog_auto[\"']?", cmdline)
        if not m:
            return None
        rest = cmdline[m.end() :].strip()
        return ["-m", CRAWL_MODULE, * _split_argv(rest)]
    rest = cmdline[idx + len(marker) :].strip()
    return ["-m", CRAWL_MODULE, *_split_argv(rest)]


def _split_argv(rest: str) -> list[str]:
    # Simple Windows-aware split for known flags (no nested quotes expected).
    try:
        import shlex

        if sys.platform == "win32":
            return shlex.split(rest, posix=False)
        return shlex.split(rest)
    except ValueError:
        return rest.split()


def start_crawl(
    python_exe: str,
    argv: list[str],
    cwd: Path,
    err_log: Path,
    out_log: Path,
) -> int:
    err_log.parent.mkdir(parents=True, exist_ok=True)
    # Append so prior context remains for diagnosis.
    err_f = open(err_log, "a", encoding="utf-8")  # noqa: SIM115
    out_f = open(out_log, "a", encoding="utf-8")  # noqa: SIM115
    creationflags = 0
    if sys.platform == "win32":
        creationflags = subprocess.CREATE_NEW_PROCESS_GROUP  # type: ignore[attr-defined]
    proc = subprocess.Popen(  # noqa: S603
        [python_exe, *argv],
        cwd=str(cwd),
        stdout=out_f,
        stderr=err_f,
        creationflags=creationflags,
    )
    err_f.close()
    out_f.close()
    return int(proc.pid)


def read_new_log_chunk(path: Path, offset: int) -> tuple[str, int]:
    if not path.is_file():
        return "", offset
    try:
        size = path.stat().st_size
        if offset > size:
            offset = 0
        with path.open("r", encoding="utf-8", errors="replace") as fh:
            fh.seek(offset)
            chunk = fh.read()
            return chunk, fh.tell()
    except OSError:
        return "", offset


def build_status(
    *,
    db_path: Path,
    err_log: Path,
    out_log: Path,
    alert_path: Path,
    pid: int | None,
) -> dict[str, Any]:
    procs = find_crawl_processes()
    alive = process_alive(pid) if pid else bool(procs)
    chosen = None
    if pid:
        for p in procs:
            if p.pid == pid:
                chosen = p
                break
    elif procs:
        chosen = procs[0]
    return {
        "ts": _utc_now(),
        "crawl_running": alive or bool(procs),
        "attached_pid": pid,
        "discovered": [asdict(p) for p in procs],
        "queue": queue_status_counts(db_path),
        "flaresolverr_healthy": flaresolverr_healthy(),
        "alert_present": alert_path.is_file(),
        "alert_path": str(alert_path),
        "last_err_lines": tail_lines(err_log, 8),
        "last_out_lines": tail_lines(out_log, 5),
        "chosen_cmdline": chosen.cmdline if chosen else None,
    }


def handle_failure(
    *,
    cwd: Path,
    repo_root: Path,
    crawl: CrawlProcess | None,
    exit_code: int | None,
    err_log: Path,
    out_log: Path,
    alert_path: Path,
    db_path: Path,
    restart_transient: bool,
    python_exe: str,
) -> Alert:
    log_tail_lines = tail_lines(err_log, 80)
    log_tail = "\n".join(log_tail_lines)
    kind, reason = classify_failure(log_tail, exit_code)
    actions: list[str] = []
    restart_pid: int | None = None
    restart_attempted = False

    if kind == "completed":
        alert = Alert(
            ts=_utc_now(),
            status="completed",
            reason=reason,
            kind=kind,
            crawl_pid=crawl.pid if crawl else None,
            exit_code=exit_code,
            queue=queue_status_counts(db_path),
            last_log_lines=log_tail_lines[-25:],
            actions_taken=["none_success"],
            restart_attempted=False,
            restart_pid=None,
            agent_prompt=(
                f"PartSouq catalogue crawl completed successfully. "
                f"Verify queue drain in {db_path.as_posix()} and import dry-run output."
            ),
            log_paths={"err": str(err_log), "out": str(out_log)},
        )
        write_alert(alert_path, alert)
        emit_sentinel(alert)
        logger.info("Crawl completed cleanly — alert written for agent notice")
        return alert

    if restart_transient and kind in TRANSIENT_KINDS:
        if kind.startswith("flaresolverr") or kind == "session_lost":
            actions.extend(ensure_flaresolverr(repo_root))
        if "flaresolverr_still_down" not in actions:
            argv = None
            if crawl and crawl.cmdline:
                argv = parse_restart_argv_from_cmdline(crawl.cmdline)
            if not argv:
                argv = list(DEFAULT_RESTART_ARGV)
            # Ensure until-complete is present for resume.
            if "--until-complete" not in argv:
                argv.append("--until-complete")
            py = python_exe or (crawl.python_exe if crawl else sys.executable)
            if crawl is not None and process_alive(crawl.pid):
                actions.append("skip_restart_still_alive")
            else:
                # Do not start a second crawl if another instance appeared.
                existing = find_crawl_processes()
                if existing:
                    actions.append(f"skip_restart_other_crawl_pid={existing[0].pid}")
                else:
                    restart_attempted = True
                    restart_pid = start_crawl(py, argv, cwd, err_log, out_log)
                    actions.append(f"restarted_pid={restart_pid}")
                    (cwd / DEFAULT_PID_FILE).write_text(str(restart_pid), encoding="utf-8")

    alert = Alert(
        ts=_utc_now(),
        status="alert",
        reason=reason,
        kind=kind,
        crawl_pid=crawl.pid if crawl else None,
        exit_code=exit_code,
        queue=queue_status_counts(db_path),
        last_log_lines=log_tail_lines[-25:],
        actions_taken=actions,
        restart_attempted=restart_attempted,
        restart_pid=restart_pid,
        agent_prompt=agent_prompt_for(kind, reason, alert_path),
        log_paths={"err": str(err_log), "out": str(out_log)},
    )
    write_alert(alert_path, alert)
    emit_sentinel(alert)
    logger.error(
        "Catalogue crawl alert kind=%s reason=%s restart=%s",
        kind,
        reason,
        restart_pid,
    )
    return alert


def watch_loop(
    *,
    cwd: Path,
    repo_root: Path,
    pid: int,
    cmdline: str,
    python_exe: str,
    err_log: Path,
    out_log: Path,
    alert_path: Path,
    db_path: Path,
    interval: float,
    restart_transient: bool,
    stall_seconds: float,
) -> int:
    crawl = CrawlProcess(pid=pid, cmdline=cmdline, python_exe=python_exe)
    offset = err_log.stat().st_size if err_log.is_file() else 0
    last_progress = time.monotonic()
    last_size = offset
    logger.info("Watching crawl pid=%s interval=%ss restart_transient=%s", pid, interval, restart_transient)

    while True:
        if not process_alive(pid):
            # Brief grace — process may be restarting itself.
            time.sleep(2.0)
            if not process_alive(pid):
                others = find_crawl_processes()
                if others:
                    logger.info(
                        "Original pid %s gone but crawl still running as %s — reattaching",
                        pid,
                        others[0].pid,
                    )
                    crawl = others[0]
                    pid = crawl.pid
                    last_progress = time.monotonic()
                    continue
                handle_failure(
                    cwd=cwd,
                    repo_root=repo_root,
                    crawl=crawl,
                    exit_code=None,
                    err_log=err_log,
                    out_log=out_log,
                    alert_path=alert_path,
                    db_path=db_path,
                    restart_transient=restart_transient,
                    python_exe=python_exe or crawl.python_exe or sys.executable,
                )
                # If we restarted, attach to new pid and keep watching.
                if restart_transient and alert_path.is_file():
                    try:
                        data = json.loads(alert_path.read_text(encoding="utf-8"))
                        new_pid = data.get("restart_pid")
                        if new_pid and process_alive(int(new_pid)):
                            pid = int(new_pid)
                            crawl = CrawlProcess(
                                pid=pid,
                                cmdline=" ".join(
                                    [python_exe or sys.executable, *DEFAULT_RESTART_ARGV]
                                ),
                                python_exe=python_exe or sys.executable,
                            )
                            last_progress = time.monotonic()
                            logger.info("Re-attached to restarted crawl pid=%s", pid)
                            continue
                    except (OSError, json.JSONDecodeError, ValueError, TypeError):
                        pass
                return 1 if (data_kind := _alert_kind(alert_path)) != "completed" else 0

        chunk, offset = read_new_log_chunk(err_log, offset)
        if chunk:
            if err_log.is_file() and err_log.stat().st_size > last_size:
                last_size = err_log.stat().st_size
                last_progress = time.monotonic()
            # Fatal patterns while still alive: only alert if traceback (likely dying).
            hits = scan_fatal_patterns(chunk)
            severe = [h for h in hits if h in {"traceback", "unhandled"}]
            if severe and not process_alive(pid):
                pass  # handled by next loop death path
            elif severe:
                logger.warning("Severe log patterns while process alive: %s", severe)

        if stall_seconds > 0 and (time.monotonic() - last_progress) > stall_seconds:
            logger.warning(
                "No err-log progress for %.0fs (pid=%s still alive=%s)",
                stall_seconds,
                pid,
                process_alive(pid),
            )
            # Soft alert only — do not kill or restart a possibly slow L5 crawl.
            soft = Alert(
                ts=_utc_now(),
                status="stall_warning",
                reason=f"No log progress for {int(stall_seconds)}s",
                kind="stall",
                crawl_pid=pid,
                exit_code=None,
                queue=queue_status_counts(db_path),
                last_log_lines=tail_lines(err_log, 15),
                actions_taken=["stall_warning_only"],
                restart_attempted=False,
                restart_pid=None,
                agent_prompt=(
                    f"PartSouq crawl pid {pid} has no log progress for {int(stall_seconds)}s. "
                    "Check whether it is healthy (FlareSolverr slow) or hung; do not kill if still fetching."
                ),
                log_paths={"err": str(err_log), "out": str(out_log)},
            )
            write_alert(alert_path, soft)
            emit_sentinel(soft)
            last_progress = time.monotonic()  # avoid alert spam

        time.sleep(interval)


def _alert_kind(alert_path: Path) -> str:
    try:
        return str(json.loads(alert_path.read_text(encoding="utf-8")).get("kind", ""))
    except (OSError, json.JSONDecodeError):
        return ""


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", type=int, default=None, help="Attach to existing crawl PID")
    parser.add_argument(
        "--attach-auto",
        action="store_true",
        help="Find running amayama_catalog_auto process",
    )
    parser.add_argument(
        "--status",
        action="store_true",
        help="Print one-shot health JSON and exit",
    )
    parser.add_argument(
        "--restart-transient",
        action="store_true",
        help="Auto-restart on FlareSolverr/session/crash if safe",
    )
    parser.add_argument("--interval", type=float, default=20.0, help="Poll interval seconds")
    parser.add_argument(
        "--stall-seconds",
        type=float,
        default=0.0,
        help="Soft-alert if err log unchanged this long (0=disabled)",
    )
    parser.add_argument("--err-log", type=Path, default=DEFAULT_ERR_LOG)
    parser.add_argument("--out-log", type=Path, default=DEFAULT_OUT_LOG)
    parser.add_argument("--alert", type=Path, default=DEFAULT_ALERT)
    parser.add_argument("--state-db", type=Path, default=DEFAULT_STATE_DB)
    parser.add_argument(
        "--python",
        default=sys.executable,
        help="Python executable used for restarts",
    )
    args = parser.parse_args(argv)

    cwd = _pipeline_root()
    os.chdir(cwd)
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    err_log = (cwd / args.err_log).resolve() if not args.err_log.is_absolute() else args.err_log
    out_log = (cwd / args.out_log).resolve() if not args.out_log.is_absolute() else args.out_log
    alert_path = (cwd / args.alert).resolve() if not args.alert.is_absolute() else args.alert
    db_path = (cwd / args.state_db).resolve() if not args.state_db.is_absolute() else args.state_db
    repo_root = cwd.parent

    if args.status:
        status = build_status(
            db_path=db_path,
            err_log=err_log,
            out_log=out_log,
            alert_path=alert_path,
            pid=args.pid,
        )
        print(json.dumps(status, indent=2))
        return 0 if status.get("crawl_running") else 2

    pid = args.pid
    cmdline = ""
    python_exe = args.python
    if args.attach_auto or pid is None:
        procs = find_crawl_processes()
        if not procs and pid is None:
            print(
                "ERROR: no amayama_catalog_auto process found. "
                "Start the crawl first, or pass --pid.",
                file=sys.stderr,
            )
            # Still emit alert so agent can respond.
            alert = Alert(
                ts=_utc_now(),
                status="alert",
                reason="No crawl process running",
                kind="not_running",
                crawl_pid=None,
                exit_code=None,
                queue=queue_status_counts(db_path),
                last_log_lines=tail_lines(err_log, 25),
                actions_taken=[],
                restart_attempted=False,
                restart_pid=None,
                agent_prompt=agent_prompt_for(
                    "not_running", "No crawl process running", alert_path
                ),
                log_paths={"err": str(err_log), "out": str(out_log)},
            )
            if args.restart_transient:
                actions = ensure_flaresolverr(repo_root)
                alert.actions_taken.extend(actions)
                if "flaresolverr_still_down" not in actions:
                    existing = find_crawl_processes()
                    if not existing:
                        alert.restart_attempted = True
                        alert.restart_pid = start_crawl(
                            python_exe,
                            list(DEFAULT_RESTART_ARGV),
                            cwd,
                            err_log,
                            out_log,
                        )
                        alert.actions_taken.append(f"restarted_pid={alert.restart_pid}")
                        pid = alert.restart_pid
                        cmdline = " ".join([python_exe, *DEFAULT_RESTART_ARGV])
            write_alert(alert_path, alert)
            emit_sentinel(alert)
            if pid and process_alive(pid):
                logger.info("Started crawl pid=%s; entering watch loop", pid)
            else:
                return 1
        elif procs:
            if pid is not None:
                match = next((p for p in procs if p.pid == pid), None)
                if not match:
                    print(f"ERROR: pid {pid} is not an amayama_catalog_auto process", file=sys.stderr)
                    return 1
                crawl = match
            else:
                crawl = procs[0]
            pid = crawl.pid
            cmdline = crawl.cmdline
            if crawl.python_exe:
                python_exe = crawl.python_exe

    assert pid is not None
    if not cmdline:
        procs = find_crawl_processes()
        match = next((p for p in procs if p.pid == pid), None)
        if match:
            cmdline = match.cmdline
            python_exe = match.python_exe or python_exe

    if not process_alive(pid):
        handle_failure(
            cwd=cwd,
            repo_root=repo_root,
            crawl=CrawlProcess(pid=pid, cmdline=cmdline, python_exe=python_exe),
            exit_code=None,
            err_log=err_log,
            out_log=out_log,
            alert_path=alert_path,
            db_path=db_path,
            restart_transient=args.restart_transient,
            python_exe=python_exe,
        )
        return 1

    (cwd / DEFAULT_PID_FILE).write_text(str(pid), encoding="utf-8")
    (cwd / DEFAULT_WATCHDOG_PID).write_text(str(os.getpid()), encoding="utf-8")
    logger.info("Watchdog pid=%s attached to crawl pid=%s", os.getpid(), pid)
    return watch_loop(
        cwd=cwd,
        repo_root=repo_root,
        pid=pid,
        cmdline=cmdline,
        python_exe=python_exe,
        err_log=err_log,
        out_log=out_log,
        alert_path=alert_path,
        db_path=db_path,
        interval=args.interval,
        restart_transient=args.restart_transient,
        stall_seconds=args.stall_seconds,
    )


if __name__ == "__main__":
    raise SystemExit(main())
