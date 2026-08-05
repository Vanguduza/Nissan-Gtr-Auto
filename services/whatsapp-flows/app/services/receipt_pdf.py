"""Minimal tax-agnostic receipt PDF for WhatsApp delivery (no ZIMRA / fiscal QR)."""

from __future__ import annotations

from io import BytesIO
from typing import Any

from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas


def build_receipt_pdf(order: dict[str, Any]) -> bytes:
    buf = BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    width, height = A4
    y = height - 48
    c.setFont("Helvetica-Bold", 14)
    c.drawString(48, y, "Nissan GTR Auto — Receipt")
    y -= 24
    c.setFont("Helvetica", 10)
    c.drawString(48, y, f"Order: {order.get('id', '')}")
    y -= 16
    c.drawString(48, y, f"Status: {order.get('status', '')}")
    y -= 16
    c.drawString(48, y, f"Currency: {order.get('currency', 'USD')}")
    y -= 16
    c.drawString(48, y, f"Delivery: {order.get('delivery_method', '')}")
    y -= 28
    c.setFont("Helvetica-Bold", 10)
    c.drawString(48, y, "Items")
    y -= 16
    c.setFont("Helvetica", 10)
    for line in order.get("lines") or []:
        oem = line.get("oem", "")
        desc = line.get("description", oem)
        qty = line.get("qty", 1)
        price = float(line.get("unit_price", 0))
        c.drawString(48, y, f"{qty}x {desc} ({oem}) @ {price:.2f}")
        y -= 14
        if y < 72:
            c.showPage()
            y = height - 48
            c.setFont("Helvetica", 10)
    y -= 12
    c.drawString(48, y, f"Subtotal: {float(order.get('subtotal', 0)):.2f}")
    y -= 14
    c.drawString(48, y, f"Delivery fee: {float(order.get('delivery_fee', 0)):.2f}")
    y -= 14
    c.setFont("Helvetica-Bold", 11)
    c.drawString(48, y, f"Total: {float(order.get('total', 0)):.2f}")
    y -= 28
    c.setFont("Helvetica", 8)
    c.drawString(
        48,
        y,
        "Tax-agnostic commercial receipt — no fiscal device / ZIMRA payload.",
    )
    c.showPage()
    c.save()
    return buf.getvalue()
