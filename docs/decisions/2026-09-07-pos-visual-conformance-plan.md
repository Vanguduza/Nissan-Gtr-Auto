# POS Visual Conformance & Production Polish Plan

**Status:** IMPLEMENTED IN SOFTWARE / x86 CI + target-device verification gates remain  
**Visual authority:** owner-approved 1536×1024 landscape POS benchmark, reattached 2026-09-08  
**Functional authority:** repository domain/RPC/offline/kiosk contracts plus the 2026-09-08 Customer Garage + Popular Items extension  
**Target:** Android landscape tablet POS

## Conformance result

The POS now follows the benchmark's three-zone composition: dark Nissan GTR Auto rail, bright discovery canvas and persistent Current Sale pane. Owner-approved functional corrections are retained even where the literal sample image differs: `Reports → EPC Browse`, the Nissan `Model → Generation → Engine` cascade, and `Popular Spares → Popular Items`.

No benchmark sample product, price or tax value is hard-coded merely to make a screenshot resemble the reference. Live/fake fixtures still pass through the same production bindings.

## Conformance matrix

| Area | Implemented state | Authority/result |
|---|---|---|
| Left rail | Dark branded rail, red selected state, benchmark navigation density, car/strapline treatment | Implemented; EPC Browse replaces Reports by owner decision |
| Header | Brand strapline, pill search/scan, operator initials/name, live date/time | Implemented |
| Vehicle cascade | `Model → Generation → Engine`, local EPC capable | Implemented owner override; persists active and multi-vehicle context |
| Hero | Committed GT-R rear artwork derived from the approved benchmark treatment | Implemented |
| Categories | Single horizontal seven-tile strip | Implemented |
| Popular Items | Bidirectional horizontal merchandising row: algorithmic spares + per-operator pinned part/model/category/subcategory cards | Implemented owner extension; swipe + explicit arrows |
| EPC pinning | Long press model/variant/category/part; Pin to Popular/Unpin; part may expose category/subcategory candidates | Implemented online/offline |
| Product imagery | POS popular-spare rows carry canonical `stock_item_images` primary image data | Implemented; no invented product thumbnails |
| Recent searches | Bounded device-local history with Clear All | Implemented |
| Customer | Dedicated individual/business search/create/edit + garage management | Implemented |
| Customer vehicle behavior | 0 manual / 1 auto / many chooser; switch mid-sale; Shop for another vehicle | Implemented |
| Current Sale | Retail card treatment, customer affordance, quantity controls, total emphasis | Implemented |
| Payment | `Proceed to Payment` opens payment surface rather than permanently exposing tender controls | Implemented |
| Receipts | Safe customer/business/contact snapshot + active/all shopped vehicle context; ESC/POS + A4/PDF paths | Implemented in software |
| Offline EPC | Complete encrypted hierarchy/diagram cache + indexed local fitment/text matching | Implemented; SQLCipher v7 |
| Staff portal | First Settings item; second credential verification and role/module recalculation | Implemented |
| A4 printing | Android Print Framework; vendor/Mopria PrintService compatibility model | Implemented; physical printer verification required |
| x86 Android build | Dedicated GitHub Actions job runs POS tests + `assembleTabletDebug` | Workflow implemented; must be green before release |
| Physical kiosk/peripherals | Device Owner, Lock Task, cold boot, camera, BT/TCP ESC/POS, Android PrintService | Target-device acceptance gate; cannot be simulated as a hardware pass |

## Visual benchmark rules

The 1536×1024 benchmark remains the layout/density reference. The following sample content is illustrative only and must not become business logic:

- benchmark currency/prices;
- VAT example;
- sample cart quantities;
- sample popular products;
- sample operator/date.

The implementation must preserve the visual relationships: narrow dark rail, wide hero, seven category tiles, horizontally carded Popular Items, compact recent-search chips, and a tall white Current Sale pane with the dominant checkout action.

## Offline/search hardening result

The local EPC cache does not rely on `%LIKE%` over every description row for the primary free-text path. SQLCipher schema v7 maintains an indexed deterministic `epc_part_terms` table plus OEM/PNC/fitment indexes. Complete EPC refresh remains transactionally separate from the pending-sale outbox. Popular Items pins use the same encrypted database with a small per-operator mutation journal for offline pin/unpin replay.

## Release gates

Software is acceptable for merge only when:

- `:feature:pos:testDebugUnitTest` is green;
- migrations include structural smoke coverage for customer/garage, multi-vehicle context, complete EPC diagrams and Popular Items pins;
- `git diff --check` is clean;
- x86 CI successfully builds `assembleTabletDebug`.

Deployment acceptance additionally requires one representative production tablet to verify Device Owner provisioning, boot/crash recovery, Lock Task reassertion, QR camera, Bluetooth/TCP ESC/POS and Android Print Framework/installed vendor PrintService. These hardware checks are deliberately not represented as automated passes.

## Non-negotiable rules

- No visual polish may introduce a second cart, stock, price, customer, payment or EPC authority.
- No decorative fake control is acceptable.
- Offline labels must state exactly what remains usable.
- Operator pins are shortcuts, not copied product/catalog truth.
- Printer claims remain protocol/PrintService qualified, never universal compatibility claims.
- Any future owner-approved benchmark correction is recorded in the design lock before implementation divergence becomes permanent.
