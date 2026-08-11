"""Tests for automatic megazip_* → external_* schema ensure."""

from __future__ import annotations

from data_pipeline.megazip.schema_ensure import rename_sql, resolve_database_url


def test_rename_sql_is_idempotent_and_targets_legacy_columns() -> None:
    sql = rename_sql()
    assert "megazip_data_id" in sql
    assert "external_data_id" in sql
    assert "megazip_item_id" in sql
    assert "external_item_id" in sql
    assert "RENAME COLUMN" in sql
    assert "information_schema.columns" in sql


def test_resolve_database_url_prefers_explicit() -> None:
    env = {
        "DATABASE_URL": "postgresql://u:p@db.example/postgres",
        "SUPABASE_URL": "https://abc.supabase.co",
        "SUPABASE_DB_PASSWORD": "secret",
    }
    assert resolve_database_url(env=env) == "postgresql://u:p@db.example/postgres"


def test_resolve_database_url_from_supabase_password() -> None:
    env = {
        "SUPABASE_URL": "https://abcdefgh.supabase.co",
        "SUPABASE_DB_PASSWORD": "s3cret",
    }
    url = resolve_database_url(env=env)
    assert url is not None
    assert "abcdefgh" in url
    assert "s3cret" in url
    assert url.startswith("postgresql://")


def test_resolve_database_url_local_cli() -> None:
    env = {
        "SUPABASE_URL": "http://127.0.0.1:54321",
        "SUPABASE_DB_PASSWORD": "postgres",
    }
    url = resolve_database_url(env=env)
    assert url == "postgresql://postgres:postgres@127.0.0.1:54322/postgres"


def test_resolve_database_url_missing() -> None:
    assert resolve_database_url(env={"SUPABASE_URL": "https://x.supabase.co"}) is None
