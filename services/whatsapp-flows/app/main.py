from __future__ import annotations

from fastapi import FastAPI

from app.api.v1 import api_router

app = FastAPI(
    title="GTR WhatsApp Flows",
    description=(
        "Meta WhatsApp Flow data exchange + Paynow-compatible payment callbacks "
        "for Nissan GTR Auto spare-parts cart/checkout (tax-agnostic receipts)."
    ),
    version="0.1.0",
)

app.include_router(api_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
