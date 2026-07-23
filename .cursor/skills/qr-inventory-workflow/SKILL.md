---
name: qr-inventory-workflow
description: QR payload generation, Bridge-First scanning/printing, receiving and returns lifecycle for inventory. Use when the task touches QR codes, ESC/POS printing, inventory receiving, or returns via QR.
---

# QR Inventory Workflow

> **Trigger:** Load only when the task touches QR code generation, scanning, printing, inventory receiving, or returns via QR.

## Payload Specification

```
gtr://part/{OEM_PART_NUMBER}?batch={BATCH_ID}&valuation={FIFO|AVG}
```

Example: `gtr://part/21410-JF00A?batch=RCV-2024-001&valuation=FIFO`

## Lifecycle

### 1. Generation (Stock Receipt)
- Triggered by Procurement/Stores workflow on stock receipt.
- Backend auto-generates QR payload with OEM part number, receiving batch, valuation bracket.
- Stored in `inventory_qr_codes` table linked to `stock_receipt_items`.

### 2. Printing (ESC/POS Bridge)
- Management app sends print job to Bluetooth thermal printer via native bridge.
- Label format: QR code + OEM part number + batch ID + date.
- Bridge: `bridges/android/escpos-printer/` or `bridges/ios/escpos-printer/`.

### 3. Scanning (Bridge-First)
- **iOS:** `bridges/ios/QRScanner/` — AVFoundation, zero-latency.
- **Android:** `bridges/android/qr-scanner/` — CameraX.
- Decoded OEM part number passed to shared UI layer.
- **Never** use browser/HTML5 QR libraries.

### 4. Sales/Counter Flow
1. Attendant scans sticker
2. Native bridge decodes → queries Supabase
3. System resolves: part details, linked vehicle diagram, multi-currency price
4. Adds to cart (with core charge split if applicable)

### 5. Returns Flow
1. Scan returned item's QR
2. System resolves exact originating invoice and batch
3. Issues precise credit note (Quarantine Returns Protocol)
4. Routes item to Quarantine warehouse automatically

## Database Tables

```sql
inventory_qr_codes (
  id UUID PRIMARY KEY,
  oem_part_number VARCHAR(20) NOT NULL,
  batch_id VARCHAR(50) NOT NULL,
  valuation_method VARCHAR(10),  -- 'FIFO' | 'AVG'
  stock_receipt_item_id UUID,
  generated_at TIMESTAMPTZ DEFAULT now(),
  printed_at TIMESTAMPTZ,
  UNIQUE(oem_part_number, batch_id)
)
```

## Route to @hardware_mobile_agent

Initial implementation of scanner/printer bridges must be routed to `@hardware_mobile_agent`.
