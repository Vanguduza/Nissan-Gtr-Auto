#!/usr/bin/env python3
"""Catalog APK FlareSolverr sidecar control agent.

Runs on the host next to Docker / native FlareSolverr. The Android app calls
POST /v1/ensure so the real Chromium solver can be started without embedding
browser automation inside the APK.

Endpoints (JSON):
  GET  /health
  GET  /v1/status
  POST /v1/ensure   — start compose (or verify already up), wait until healthy
  POST /v1/stop     — docker compose stop (optional)

Auth: header X-Sidecar-Token must match SIDECAR_TOKEN (default: catalog-apk-dev).
Bind: SIDECAR_BIND (default 127.0.0.1) : SIDECAR_PORT (default 8192).
"""

from __future__ import annotations

import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMPOSE_FILE = ROOT / "docker-compose.yml"
TOKEN = os.environ.get("SIDECAR_TOKEN", "catalog-apk-dev")
BIND = os.environ.get("SIDECAR_BIND", "127.0.0.1")
PORT = int(os.environ.get("SIDECAR_PORT", "8192"))
FS_URL = os.environ.get("FLARESOLVERR_URL", "http://127.0.0.1:8191")
ENSURE_TIMEOUT_SEC = int(os.environ.get("ENSURE_TIMEOUT_SEC", "90"))


def _json_bytes(payload: dict) -> bytes:
    return json.dumps(payload).encode("utf-8")


def flaresolverr_healthy(url: str = FS_URL, timeout: float = 3.0) -> bool:
    try:
        req = urllib.request.Request(url.rstrip("/") + "/", method="GET")
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return 200 <= int(resp.status) < 300
    except Exception:  # noqa: BLE001
        return False


def docker_available() -> bool:
    return shutil.which("docker") is not None


def compose_cmd(*args: str) -> list[str]:
    return ["docker", "compose", "-f", str(COMPOSE_FILE), *args]


def run_compose(*args: str) -> tuple[int, str]:
    if not COMPOSE_FILE.is_file():
        return 1, f"missing compose file: {COMPOSE_FILE}"
    try:
        proc = subprocess.run(
            compose_cmd(*args),
            cwd=str(ROOT),
            capture_output=True,
            text=True,
            timeout=120,
            check=False,
        )
        out = (proc.stdout or "") + (proc.stderr or "")
        return int(proc.returncode), out.strip()
    except Exception as exc:  # noqa: BLE001
        return 1, str(exc)


def ensure_flaresolverr() -> dict:
    if flaresolverr_healthy():
        return {
            "ok": True,
            "started": False,
            "flaresolverr_url": f"{FS_URL.rstrip('/')}/v1",
            "message": "FlareSolverr already healthy",
        }

    started_via = None
    detail = ""

    if docker_available():
        code, out = run_compose("up", "-d", "--remove-orphans")
        if code != 0:
            detail = f"compose up failed ({code}): {out[-500:]}"
        else:
            started_via = "docker"
            detail = out[-300:]
    else:
        # Native Windows binary via repo script (no Docker).
        repo_root = ROOT.parents[2] if len(ROOT.parents) >= 3 else ROOT
        native = repo_root / "data-pipeline" / "scripts" / "start-flaresolverr-native.ps1"
        if native.is_file() and shutil.which("powershell"):
            try:
                proc = subprocess.run(
                    [
                        "powershell",
                        "-NoProfile",
                        "-ExecutionPolicy",
                        "Bypass",
                        "-File",
                        str(native),
                    ],
                    capture_output=True,
                    text=True,
                    timeout=600,
                    check=False,
                )
                detail = ((proc.stdout or "") + (proc.stderr or "")).strip()[-500:]
                if proc.returncode == 0:
                    started_via = "native"
                else:
                    detail = f"native start failed ({proc.returncode}): {detail}"
            except Exception as exc:  # noqa: BLE001
                detail = f"native start error: {exc}"
        else:
            detail = (
                "docker not found and native starter missing; "
                f"expected {native}"
            )

    if started_via is None and not flaresolverr_healthy():
        return {
            "ok": False,
            "started": False,
            "flaresolverr_url": f"{FS_URL.rstrip('/')}/v1",
            "message": detail or "unable to start FlareSolverr",
        }

    deadline = time.time() + ENSURE_TIMEOUT_SEC
    while time.time() < deadline:
        if flaresolverr_healthy():
            return {
                "ok": True,
                "started": True,
                "flaresolverr_url": f"{FS_URL.rstrip('/')}/v1",
                "message": f"FlareSolverr started via {started_via} and healthy",
            }
        time.sleep(2.0)

    return {
        "ok": False,
        "started": True,
        "flaresolverr_url": f"{FS_URL.rstrip('/')}/v1",
        "message": (
            f"start attempted via {started_via} but not healthy within "
            f"{ENSURE_TIMEOUT_SEC}s. {detail}"
        ),
    }


def stop_flaresolverr() -> dict:
    if not docker_available():
        return {"ok": False, "message": "docker not found"}
    code, out = run_compose("stop")
    return {
        "ok": code == 0,
        "message": out[-500:] if out else ("stopped" if code == 0 else f"stop failed ({code})"),
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "CatalogApkFlareSidecar/1.0"

    def log_message(self, fmt: str, *args) -> None:  # noqa: A003
        sys.stderr.write("%s - %s\n" % (self.address_string(), fmt % args))

    def _unauthorized(self) -> None:
        self.send_response(401)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(_json_bytes({"ok": False, "message": "unauthorized"}))

    def _check_token(self) -> bool:
        got = self.headers.get("X-Sidecar-Token", "")
        return got == TOKEN

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", "0") or "0")
        if length <= 0:
            return {}
        raw = self.rfile.read(length)
        try:
            return json.loads(raw.decode("utf-8"))
        except Exception:  # noqa: BLE001
            return {}

    def _respond(self, code: int, payload: dict) -> None:
        body = _json_bytes(payload)
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:  # noqa: N802
        path = self.path.split("?", 1)[0]
        if path in ("/health", "/"):
            healthy = flaresolverr_healthy()
            self._respond(
                200,
                {
                    "ok": True,
                    "agent": "catalog-apk-flaresolverr-sidecar",
                    "flaresolverr_healthy": healthy,
                    "flaresolverr_url": f"{FS_URL.rstrip('/')}/v1",
                },
            )
            return
        if path == "/v1/status":
            if not self._check_token():
                self._unauthorized()
                return
            healthy = flaresolverr_healthy()
            self._respond(
                200,
                {
                    "ok": healthy,
                    "flaresolverr_healthy": healthy,
                    "docker": docker_available(),
                    "compose_file": str(COMPOSE_FILE),
                    "flaresolverr_url": f"{FS_URL.rstrip('/')}/v1",
                },
            )
            return
        self._respond(404, {"ok": False, "message": "not found"})

    def do_POST(self) -> None:  # noqa: N802
        path = self.path.split("?", 1)[0]
        if not self._check_token():
            self._unauthorized()
            return
        _ = self._read_json()
        if path == "/v1/ensure":
            result = ensure_flaresolverr()
            self._respond(200 if result.get("ok") else 503, result)
            return
        if path == "/v1/stop":
            result = stop_flaresolverr()
            self._respond(200 if result.get("ok") else 500, result)
            return
        self._respond(404, {"ok": False, "message": "not found"})


def main() -> int:
    print(f"Sidecar agent on http://{BIND}:{PORT}/  (token required for /v1/*)")
    print(f"FlareSolverr expected at {FS_URL}")
    print(f"Compose file: {COMPOSE_FILE}")
    httpd = ThreadingHTTPServer((BIND, PORT), Handler)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nstopping")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
