# GTR Management — Android

**Product UX:** vendored CoolMallKotlin at [`vendor/coolmall-gtr/`](../../vendor/coolmall-gtr/)  
**Behavior SoT:** web staff modules (`apps/web/lib/staff-auth.ts` + staff/procurement desks)  
**Brand:** GTR colours only  
**Backend:** Supabase adapters (not CoolMall API; not legacy UI port)

Plan: [`docs/plans/2026-08-14-management-oss-shell-rebuild.md`](../../docs/plans/2026-08-14-management-oss-shell-rebuild.md)

## Transition note

This directory still holds the mistaken “inspired” scaffold + `:core:rpc` injection kit.
Do **not** grow it by copying `android-management-legacy` screens. Transplant RPC Fake/Live
into the CoolMall vendor; implement each web staff capability as CoolMall destinations.

## Constraints

- Supabase SoR · Bridge-First · no ZIMRA · no payroll tax · no `catalog-apk` edits
- Legacy = optional RPC discovery only
