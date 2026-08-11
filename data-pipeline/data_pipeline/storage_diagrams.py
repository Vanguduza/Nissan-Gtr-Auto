"""Shared Supabase Storage helpers for catalog diagram assets.

REST uploads without ``cache-control`` default to ``Cache-Control: no-cache``.
All diagram upload entry points must use ``DIAGRAM_CACHE_CONTROL_SECONDS``.
"""

from __future__ import annotations

DIAGRAMS_BUCKET = "catalog-diagrams"
# Stored on the object and returned on public GET. Prefer explicit max-age= so
# browsers/CDNs cache; bare seconds can be emitted as ``public, 31536000`` (no TTL).
DIAGRAM_CACHE_CONTROL = "public, max-age=31536000, immutable"
# Back-compat alias used by older call sites / docs.
DIAGRAM_CACHE_CONTROL_SECONDS = "31536000"


def epc_storage_path(storage_path: str) -> str:
    """Map Megazip bundle paths (``megazip/…``) to public Storage paths (``epc/…``)."""
    if storage_path.lower().startswith("megazip/"):
        return "epc/" + storage_path[len("megazip/") :]
    return storage_path


def content_type_for_path(path: str) -> str:
    lower = path.lower()
    if lower.endswith(".gif"):
        return "image/gif"
    if lower.endswith(".webp"):
        return "image/webp"
    if lower.endswith((".jpg", ".jpeg")):
        return "image/jpeg"
    return "image/png"


def rest_upload_headers(*, api_key: str, content_type: str) -> dict[str, str]:
    """Headers for Storage REST POST/PUT upsert of a diagram object."""
    return {
        "apikey": api_key,
        "Authorization": f"Bearer {api_key}",
        "Content-Type": content_type,
        "x-upsert": "true",
        # Avoid Cache-Control: no-cache (REST default without this header).
        "cache-control": DIAGRAM_CACHE_CONTROL,
    }


def supabase_file_options(content_type: str) -> dict[str, str]:
    """``file_options`` for ``supabase-py`` ``storage.from_(…).upload``."""
    return {
        "content-type": content_type,
        "upsert": "true",
        "cache-control": DIAGRAM_CACHE_CONTROL,
    }
