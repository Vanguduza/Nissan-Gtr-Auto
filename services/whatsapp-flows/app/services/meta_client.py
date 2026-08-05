"""Meta WhatsApp Cloud API client — Flow triggers, payment CTA, receipt PDF."""

from __future__ import annotations

import logging
from typing import Any

import httpx

from app.core.config import Settings, get_settings

logger = logging.getLogger(__name__)


class MetaClientError(Exception):
    def __init__(self, message: str, status: int | None = None, body: str | None = None):
        super().__init__(message)
        self.status = status
        self.body = body


class MetaClient:
    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()

    @property
    def configured(self) -> bool:
        return bool(
            self.settings.whatsapp_access_token
            and self.settings.whatsapp_phone_number_id,
        )

    def _messages_url(self) -> str:
        v = self.settings.whatsapp_api_version
        pid = self.settings.whatsapp_phone_number_id
        return f"https://graph.facebook.com/{v}/{pid}/messages"

    def _headers(self) -> dict[str, str]:
        return {
            "Authorization": f"Bearer {self.settings.whatsapp_access_token}",
            "Content-Type": "application/json",
        }

    def _normalize_to(self, e164: str) -> str:
        return "".join(c for c in e164 if c.isdigit())

    async def _post_messages(self, payload: dict[str, Any]) -> dict[str, Any]:
        if not self.configured:
            if self.settings.whatsapp_allow_unverified_local:
                logger.warning("meta_client stub send: %s", payload.get("type"))
                return {"messages": [{"id": "stub"}], "stub": True}
            raise MetaClientError("WHATSAPP_ACCESS_TOKEN / PHONE_NUMBER_ID unset")

        body = {"messaging_product": "whatsapp", **payload}
        async with httpx.AsyncClient(timeout=30.0) as client:
            res = await client.post(
                self._messages_url(),
                headers=self._headers(),
                json=body,
            )
        text = res.text
        if res.status_code >= 400:
            raise MetaClientError(
                f"WhatsApp Cloud API {res.status_code}: {text[:400]}",
                status=res.status_code,
                body=text,
            )
        return res.json() if text else {}

    async def send_text(self, to_e164: str, body: str) -> dict[str, Any]:
        to = self._normalize_to(to_e164)
        return await self._post_messages(
            {
                "to": to,
                "type": "text",
                "text": {"preview_url": False, "body": body[:4096]},
            },
        )

    async def send_payment_cta(
        self,
        to_e164: str,
        *,
        payment_url: str,
        order_id: str,
        amount_label: str,
        body_text: str | None = None,
    ) -> dict[str, Any]:
        """Interactive CTA URL button — Paynow / EcoCash / OneMoney / InnBucks hosted page."""
        to = self._normalize_to(to_e164)
        text = body_text or (
            f"Order {order_id[:8]}… — {amount_label}\n"
            "Pay via Paynow (EcoCash, OneMoney, InnBucks, or card)."
        )
        return await self._post_messages(
            {
                "to": to,
                "type": "interactive",
                "interactive": {
                    "type": "cta_url",
                    "body": {"text": text[:1024]},
                    "action": {
                        "name": "cta_url",
                        "parameters": {
                            "display_text": "Pay now",
                            "url": payment_url,
                        },
                    },
                },
            },
        )

    async def send_flow_message(
        self,
        to_e164: str,
        *,
        flow_id: str,
        flow_cta: str = "Browse parts",
        body_text: str = "Select parts from the diagram and checkout.",
        screen: str | None = None,
        flow_token: str | None = None,
    ) -> dict[str, Any]:
        """Trigger a published WhatsApp Flow (optional entry point)."""
        to = self._normalize_to(to_e164)
        params: dict[str, Any] = {
            "flow_message_version": "3",
            "flow_id": flow_id,
            "flow_cta": flow_cta[:30],
            "flow_action": "navigate" if screen else "data_exchange",
        }
        if screen:
            params["flow_action_payload"] = {"screen": screen}
        if flow_token:
            params["flow_token"] = flow_token
        return await self._post_messages(
            {
                "to": to,
                "type": "interactive",
                "interactive": {
                    "type": "flow",
                    "body": {"text": body_text[:1024]},
                    "action": {"name": "flow", "parameters": params},
                },
            },
        )

    async def upload_pdf(self, pdf_bytes: bytes, filename: str = "receipt.pdf") -> str:
        if not self.configured:
            if self.settings.whatsapp_allow_unverified_local:
                return "stub-media-id"
            raise MetaClientError("WhatsApp not configured for media upload")
        v = self.settings.whatsapp_api_version
        pid = self.settings.whatsapp_phone_number_id
        url = f"https://graph.facebook.com/{v}/{pid}/media"
        headers = {"Authorization": f"Bearer {self.settings.whatsapp_access_token}"}
        files = {
            "file": (filename, pdf_bytes, "application/pdf"),
        }
        data = {"messaging_product": "whatsapp", "type": "application/pdf"}
        async with httpx.AsyncClient(timeout=60.0) as client:
            res = await client.post(url, headers=headers, data=data, files=files)
        if res.status_code >= 400:
            raise MetaClientError(
                f"media upload {res.status_code}: {res.text[:400]}",
                status=res.status_code,
                body=res.text,
            )
        mid = res.json().get("id")
        if not mid:
            raise MetaClientError("media upload missing id", body=res.text)
        return str(mid)

    async def send_document(
        self,
        to_e164: str,
        *,
        media_id: str | None = None,
        link: str | None = None,
        filename: str = "receipt.pdf",
        caption: str | None = None,
    ) -> dict[str, Any]:
        to = self._normalize_to(to_e164)
        document: dict[str, str] = {"filename": filename}
        if media_id:
            document["id"] = media_id
        elif link:
            document["link"] = link
        else:
            raise MetaClientError("document requires media_id or link")
        if caption:
            document["caption"] = caption[:1024]
        return await self._post_messages(
            {"to": to, "type": "document", "document": document},
        )

    async def send_receipt_message(
        self,
        to_e164: str,
        *,
        order_id: str,
        pdf_bytes: bytes | None = None,
        pdf_url: str | None = None,
        caption: str | None = None,
    ) -> dict[str, Any]:
        """Send receipt PDF over WhatsApp after PAID."""
        text = caption or f"Receipt for order {order_id}. Thank you for shopping Nissan GTR Auto."
        await self.send_text(to_e164, text)
        if pdf_bytes:
            mid = await self.upload_pdf(pdf_bytes, filename=f"receipt-{order_id[:8]}.pdf")
            return await self.send_document(
                to_e164,
                media_id=mid,
                filename=f"receipt-{order_id[:8]}.pdf",
                caption="Your receipt (PDF)",
            )
        if pdf_url:
            return await self.send_document(
                to_e164,
                link=pdf_url,
                filename=f"receipt-{order_id[:8]}.pdf",
                caption="Your receipt (PDF)",
            )
        return {"skipped_pdf": True}
