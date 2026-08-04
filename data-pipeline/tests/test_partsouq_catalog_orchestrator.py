"""Unit tests for PartSouq multi-make orchestrator (no live network)."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from data_pipeline.partsouq_catalog_orchestrator import (
    DEFAULT_MAKERS_FILE,
    OrchestratorOptions,
    build_crawl_argv,
    build_maker_paths,
    build_parse_watch_argv,
    build_parser,
    build_transform_argv,
    extract_makers_from_locate_html,
    load_makers_file,
    maker_slug,
    options_from_args,
    prepare_maker,
    resolve_makers,
    run_orchestrator,
    write_manifest,
)


AVAILABLE = ["Nissan", "Toyota", "Honda", "Mercedes-Benz"]


def test_maker_slug() -> None:
    assert maker_slug("Toyota") == "toyota"
    assert maker_slug("Mercedes-Benz") == "mercedes-benz"
    assert maker_slug("Land Rover") == "land-rover"


def test_resolve_makers_all() -> None:
    assert resolve_makers("all", AVAILABLE) == AVAILABLE


def test_resolve_makers_single_case_insensitive() -> None:
    assert resolve_makers("toyota", AVAILABLE) == ["Toyota"]


def test_resolve_makers_group() -> None:
    assert resolve_makers("Toyota,Honda,Nissan", AVAILABLE) == ["Toyota", "Honda", "Nissan"]


def test_resolve_makers_dedupe() -> None:
    assert resolve_makers("Toyota,toyota,TOYOTA", AVAILABLE) == ["Toyota"]


def test_resolve_makers_empty_raises() -> None:
    with pytest.raises(ValueError):
        resolve_makers("", AVAILABLE)
    with pytest.raises(ValueError):
        resolve_makers(" , ", AVAILABLE)


def test_resolve_makers_adhoc_unknown_allowed() -> None:
    got = resolve_makers("CustomBrand", AVAILABLE)
    assert got == ["CustomBrand"]


def test_maker_paths_layout() -> None:
    paths = build_maker_paths("Toyota", Path("out/makers"))
    assert paths.slug == "toyota"
    assert paths.root == Path("out/makers/toyota")
    assert paths.state_db == Path("out/makers/toyota/crawler_state.db")
    assert paths.cache_dir == Path("out/makers/toyota/cache")
    assert paths.out_dir == Path("out/makers/toyota/bundle")
    assert paths.parse_db == Path("out/makers/toyota/cache_parse_state.db")
    assert paths.diagram_storage_prefix == "partsouq/toyota"
    assert "c=Toyota" in paths.start_url
    entry = paths.as_manifest_entry()
    assert entry["allowed_brand"] == "toyota"
    assert entry["maker"] == "Toyota"


def test_load_default_makers_file() -> None:
    makers, meta = load_makers_file(DEFAULT_MAKERS_FILE)
    assert "Nissan" in makers
    assert "Toyota" in makers
    assert meta.get("locate_path")


def test_build_parser_requires_makers() -> None:
    with pytest.raises(SystemExit):
        build_parser().parse_args([])


def test_arg_parsing_and_options(tmp_path: Path) -> None:
    args = build_parser().parse_args(
        [
            "--makers",
            "Toyota,Honda",
            "--out-root",
            str(tmp_path / "makers"),
            "--parallel-makers",
            "2",
            "--max-pages",
            "10",
            "--import-dry-run",
            "--dry-run",
            "--skip-flaresolverr-check",
        ]
    )
    makers = resolve_makers(args.makers, AVAILABLE)
    opts = options_from_args(args, makers)
    assert opts.makers == ["Toyota", "Honda"]
    assert opts.parallel_makers == 2
    assert opts.max_pages == 10
    assert opts.import_dry_run is True
    assert opts.dry_run is True
    assert opts.skip_flaresolverr_check is True


def test_crawl_and_parse_argv_isolation() -> None:
    paths = build_maker_paths("Nissan", Path("out/makers"))
    opts = OrchestratorOptions(makers=["Nissan"], max_pages=5, until_complete=True)
    crawl = build_crawl_argv(paths, opts)
    assert "data_pipeline.amayama_catalog_auto" in crawl
    assert "--crawl-only" in crawl
    assert "--max-pages" in crawl
    assert "5" in crawl
    assert str(paths.state_db) in crawl
    assert str(paths.cache_dir) in crawl
    assert str(paths.out_dir) in crawl
    assert "--until-complete" not in crawl  # max-pages disables until-complete

    parse = build_parse_watch_argv(paths, opts)
    assert "data_pipeline.cache_parse_worker" in parse
    assert "--watch" in parse
    assert str(paths.parse_db) in parse
    # Identity/backfill live in the watcher — no separate mapping module argv.
    assert not any("vin_decode" in a or "mapping" in a.lower() for a in parse)


def test_transform_argv_flags() -> None:
    paths = build_maker_paths("Honda", Path("out/makers"))
    opts = OrchestratorOptions(
        makers=["Honda"],
        download_diagrams=True,
        import_dry_run=True,
        live_import=False,
    )
    argv = build_transform_argv(paths, opts)
    assert "--transform-only" in argv
    assert "--download-diagrams" in argv
    assert "--import-dry-run" in argv
    assert "--live-import" not in argv


def test_extract_makers_from_locate_html() -> None:
    html = """
    <a href="/en/catalog/genuine/locate?c=Toyota">Toyota</a>
    <a href="/en/catalog/genuine/unit?c=Nissan&vid=1">x</a>
    <a href="/en/catalog/genuine/locate?c=NISSAN201809">skip code</a>
    <a href="/en/catalog/genuine/locate?c=Honda">Honda</a>
    """
    found = extract_makers_from_locate_html(html)
    assert "Toyota" in found
    assert "Nissan" in found
    assert "Honda" in found
    assert not any("201809" in m for m in found)


def test_dry_run_prepare_and_manifest(tmp_path: Path) -> None:
    out_root = tmp_path / "makers"
    base = tmp_path / "scrape.json"
    base.write_text(
        json.dumps(
            {
                "allowed_brand": "nissan",
                "start_url": "https://partsouq.com/en/catalog/genuine/locate?c=Nissan",
                "diagram_storage_prefix": "partsouq/nissan",
            }
        ),
        encoding="utf-8",
    )
    opts = OrchestratorOptions(
        makers=["Toyota", "Honda"],
        out_root=out_root,
        base_config=base,
        dry_run=True,
        skip_flaresolverr_check=True,
        cwd=tmp_path,
    )
    code = run_orchestrator(opts)
    assert code == 0

    manifest = json.loads((out_root / "manifest.json").read_text(encoding="utf-8"))
    assert len(manifest["makers"]) == 2
    slugs = {m["slug"] for m in manifest["makers"]}
    assert slugs == {"toyota", "honda"}

    toyota = out_root / "toyota"
    assert (toyota / "scrape.json").exists()
    scrape = json.loads((toyota / "scrape.json").read_text(encoding="utf-8"))
    assert scrape["allowed_brand"] == "toyota"
    assert scrape["diagram_storage_prefix"] == "partsouq/toyota"
    assert "c=Toyota" in scrape["start_url"]
    assert (toyota / "cache").is_dir()
    assert (toyota / "bundle").is_dir()
    assert (toyota / "meta.json").exists()


def test_write_manifest_paths(tmp_path: Path) -> None:
    paths = build_maker_paths("Nissan", tmp_path / "makers")
    prepare_maker(
        paths,
        OrchestratorOptions(makers=["Nissan"], out_root=tmp_path / "makers", base_config=tmp_path / "missing.json"),
    )
    out = write_manifest(tmp_path / "makers", [paths.as_manifest_entry()])
    data = json.loads(out.read_text(encoding="utf-8"))
    assert data["makers"][0]["slug"] == "nissan"
