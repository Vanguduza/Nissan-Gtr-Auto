# EPC browse wireframes (Megazip-style)

Companion to `2026-08-07-megazip-epc-browse-integration-prompt.md` §9.

## Layout notes (web)

### Maker hub `/catalog`
- Page title: Parts catalog (EPC)
- Grid of maker tiles (name + model_count); primary CTA into models
- Secondary link: Shop stock → `/shop`

### Model grid `/catalog/[maker]`
- A–Z via `sort_key`; cards show display_name, body_type, year range
- Breadcrumb: Catalog › Maker

### Variant list `/catalog/[maker]/[model]`
- Dense rows: chassis_code prominent, then grade / region / year / engine
- Breadcrumb includes model

### Section grid `/catalog/…/[variant]`
- Thumbnail cards (lazy img + skeleton); name under tile
- Breadcrumb includes variant (chassis)

### Diagram `/catalog/…/[section]`
- Desktop: ~60% canvas / 40% parts table side-by-side
- Mobile (≤640px): canvas stacked above table
- Hotspot hover ↔ row highlight; click → `/parts/[oem]?from=epc`
- Stocked rows: StockBadge + PriceDual + AddToCart
- If diagram null but parts exist: banner + table only

## POS / mobile
- Same hierarchy depth; POS add-to-cart targets till cart, not storefront cart
- Mobile: native Box/ZStack hotspot overlays (not browser QR)

## Visual tokens
Reuse storefront tokens (`--gtr-red`, mono for OEM labels). Avoid new purple/cream AI-default themes.
