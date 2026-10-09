#!/usr/bin/env node
console.warn("seed-hosted-dev-users.mjs is deprecated; forwarding to authoritative staff provisioner.");
await import("./provision-canonical-staff.mjs");
