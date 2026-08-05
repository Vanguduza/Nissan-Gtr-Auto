from __future__ import annotations

from fastapi import APIRouter

from app.api.v1 import ecocash_webhook, flow_endpoint, payment_webhook

api_router = APIRouter()
api_router.include_router(flow_endpoint.router)
api_router.include_router(payment_webhook.router)
api_router.include_router(ecocash_webhook.router)
