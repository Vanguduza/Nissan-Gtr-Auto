# POS Customer Garage + Popular Items — Canonical Extension

**Status:** LOCKED / additive to the 2026-09-07 operator-screen design lock  
**Date:** 2026-09-08  
**Applies to:** Android tablet POS, POS RPCs, encrypted offline cache, receipt snapshots

## Owner-approved changes

The reattached owner benchmark remains the visual authority for the landscape tablet composition. Two functional extensions intentionally supersede the literal sample labels/content in that image:

1. **Popular Spares becomes Popular Items.** The row keeps algorithmic best-selling spare cards and also contains operator-owned pinned EPC shortcuts.
2. **Customer becomes a real customer selection/management workspace.** It is not a text field or decorative cart action.

Neither change may weaken the benchmark's three-zone composition, persistent Current Sale pane, horizontal merchandising treatment, spacing hierarchy or retail visual language.

## Popular Items contract

The Home carousel merges two sources without creating another catalog authority:

- **Algorithmic items:** server-ranked posted-sales spares from `list_pos_popular_spares`.
- **Operator pins:** explicit per-staff shortcuts stored in `pos_operator_popular_pins` and synced into the encrypted tablet database.

Pinned item kinds are `part`, `model`, `category` and `subcategory`. Pins are displayed before algorithmic items. If an OEM part is explicitly pinned, the same OEM is suppressed from the algorithmic portion so one card is not duplicated.

In POS EPC Browse, a long press on a model, model variant, section/category or diagram part opens the Popular Items action dialog. A diagram part can contribute the part itself, its category and/or its subcategory when those catalog fields exist. The action is **Pin to Popular** when absent and **Unpin** when already pinned. Pinned Home cards also expose Unpin.

The carousel supports normal left/right touch swiping plus explicit accessible left/right controls. A part pin adds that OEM through the canonical POS cart path. Model/category/subcategory pins open a catalog search scoped from the stored EPC metadata; they never carry their own stock, price or fitment truth.

### Offline pin semantics

Pins are per authenticated operator. `gtr_pos_offline.db` SQLCipher schema v7 caches pins and a small dirty-action journal. Pin/unpin performed offline is immediately reflected locally and is replayed when connectivity returns. The server is still the cross-device source of truth after synchronization. A pin limit of 24 prevents an unbounded Home carousel.

## Customer workspace contract

The Customer navigation item opens a customer-management page that can:

- search and select existing customers;
- create or edit **individual** and **business** accounts;
- manage non-sensitive commercial/contact fields used by POS and receipts;
- add/edit Nissan vehicles in the customer's garage using canonical EPC model/chassis/engine identifiers.

Sensitive identity, credit and authentication data are not exposed merely because the operator can manage customer sale context.

### Customer → vehicle selection

- No saved vehicles: keep manual `Model → Generation → Engine` selection available.
- Exactly one saved vehicle: select it automatically as the fitment filter.
- More than one saved vehicle: open the garage vehicle chooser.
- **Shop for another vehicle:** clear only the active fitment filter and return to the manual cascade.
- Switching vehicles mid-sale is supported. Every distinct vehicle used during the sale is retained in `vehicle_contexts`; the active vehicle columns remain the fast current-filter projection.

The customer or active vehicle can change without recreating or discarding sale lines. Quotations, invoices and offline replay preserve the accumulated vehicle-context history.

## Receipt privacy contract

Receipts may show only relevant non-sensitive customer information: display/business name, contact name when different, configured email/phone, and vehicle context. Customer snapshot fields are written to the invoice so later account edits do not rewrite historical receipt identity. No sensitive identity/auth/credit field is added to the receipt surface by this feature.

## Offline EPC/search contract

The tablet retains the complete Nissan EPC hierarchy, diagrams, fitment rows and optionally diagram image bytes in encrypted local storage. Local fitment filtering works without network access. Free-text EPC matching uses the indexed deterministic `epc_part_terms` table rather than repeated `%LIKE%` scans; OEM/PNC/fitment indexes remain authoritative for exact structural matching.

Catalog refreshes remain transactionally isolated from pending offline sale records. The outbox must never be dropped as a side effect of refreshing EPC data.

## Printer contract

Receipt printers continue to use direct ESC/POS Bluetooth SPP or Wi-Fi/LAN TCP where the printer supports those protocols. A4/desktop output uses Android Print Framework and therefore requires a compatible built-in, Mopria or vendor PrintService. Vendor driver/settings installation remains admin-only under Kiosk & device.

## Primary implementation

- `PosCustomerWorkspace.kt`
- `PosPopularItems.kt`
- `PosOperatorWorkspace.kt`
- `PosEpcBrowseScreen.kt`
- `PosViewModel.kt`
- `offline/SqlCipherOfflinePosStore.kt`
- `offline/OfflinePosSyncEngine.kt`
- `supabase/migrations/20260907110000_pos_customer_garage_management.sql`
- `supabase/migrations/20260907130000_pos_multi_vehicle_context.sql`
- `supabase/migrations/20260907140000_pos_operator_popular_pins.sql`

## Verification gates

- POS Kotlin compilation and unit tests must be green.
- Supabase structural smoke tests must prove the new RPC/table/column contracts exist after migrations.
- The x86 Android CI must build the tablet APK because the current ARM64 development host cannot execute Google's x86 AAPT2 binary.
- Physical Device Owner/Lock Task, camera, Bluetooth/TCP printer and Android PrintService behavior remain target-device acceptance tests; software must not claim those transports worked on hardware until tested there.
