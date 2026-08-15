# Previews

Static artifacts for visual smoke (no secrets).

| File | What |
|------|------|
| [`hr-onboarding-id-card-driver.html`](./hr-onboarding-id-card-driver.html) | Simulated driver onboarding → CR80 ID card **front + back** (`@gtr/documents` `renderIdCardHtml`; logo + employee QR SVG) |
| [`hr-onboarding-id-card-driver.json`](./hr-onboarding-id-card-driver.json) | Payload meta for the same simulation (verify URL / QR payload) |


Regenerate:

```bash
pnpm preview:hr-id-card
```

Open the HTML in a browser (file URL). No ZIMRA / fiscal QR.
