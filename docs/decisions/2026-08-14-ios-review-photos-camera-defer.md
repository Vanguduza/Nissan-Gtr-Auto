# Decision: iOS PDP review photos — camera preference deferred

- **Date:** 2026-08-14
- **Status:** Accepted (defer)
- **Lane:** `@ios_agent` / `@hardware_mobile_agent` (Bridge-First)

## Context

Master-plan residual listed “iOS review photos: PhotosPicker today; camera bridge later.”

## Decision

**Defer** “prefer camera over Photos library” UX. Remove from Windows-actionable open list.

## Why

- `PdpReviewsScreen` already has **PhotosPicker** (library) and **Bridge-First** `UIKitReviewCameraBridge` / `ReviewCameraBridge.captureViaBridge()` — camera is not missing.
- Remaining work is thin UX preference (default CTA), not infrastructure.
- No HTML5 / WebView camera shortcuts.

## When to reopen

Explicit product ask to default the review photo CTA to camera (keep PhotosPicker secondary).
