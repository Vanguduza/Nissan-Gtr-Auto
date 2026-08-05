"""Supabase client helpers (service role)."""

from __future__ import annotations

from functools import lru_cache

from supabase import Client, create_client

from app.core.config import get_settings


@lru_cache
def get_supabase() -> Client:
    s = get_settings()
    if not s.supabase_service_role_key:
        raise RuntimeError(
            "SUPABASE_SERVICE_ROLE_KEY is required for WhatsApp Flows (service role)",
        )
    return create_client(s.supabase_url, s.supabase_service_role_key)
