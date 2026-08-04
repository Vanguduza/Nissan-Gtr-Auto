"""Unit tests for catalogue crawl watchdog helpers."""

from __future__ import annotations

from data_pipeline.catalogue_watchdog import (
    classify_failure,
    parse_restart_argv_from_cmdline,
    scan_fatal_patterns,
)


def test_scan_fatal_traceback() -> None:
    text = "INFO ok\nTraceback (most recent call last):\n  File \"x.py\""
    assert "traceback" in scan_fatal_patterns(text)


def test_scan_session_lost() -> None:
    text = "ERROR Session not found for id abc"
    assert "session_lost" in scan_fatal_patterns(text)


def test_classify_completed() -> None:
    kind, _ = classify_failure("all good", 0)
    assert kind == "completed"


def test_classify_flaresolverr() -> None:
    kind, _ = classify_failure("ConnectError: Connection refused to 127.0.0.1:8191", 1)
    assert kind == "flaresolverr_down"


def test_parse_restart_argv() -> None:
    cmdline = (
        r'"C:\Python\python.exe" -m data_pipeline.amayama_catalog_auto '
        r"--local-ip --until-complete --out-dir out/partsouq_bundle --import-dry-run -v"
    )
    argv = parse_restart_argv_from_cmdline(cmdline)
    assert argv is not None
    assert argv[0:2] == ["-m", "data_pipeline.amayama_catalog_auto"]
    assert "--until-complete" in argv
    assert "--local-ip" in argv


def test_per_url_cf_not_fatal_alone() -> None:
    text = (
        "ERROR data_pipeline.amayama_catalog_auto: Cloudflare challenge still present: "
        "https://partsouq.com/en/catalog/genuine/unit?c=Nissan"
    )
    # CF-still-present alone is not in FATAL_LOG_PATTERNS
    assert scan_fatal_patterns(text) == []
