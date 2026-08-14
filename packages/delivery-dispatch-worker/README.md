# Delivery dispatch Temporal worker (§H H1)

Host process for **`DeliveryDispatchWorkflow`** / `DELIVERY_DISPATCH_WORKFLOW`.

**Status:** Infra ready — awaiting `TEMPORAL_ADDRESS` + `SUPABASE_SERVICE_ROLE_KEY` for live run (fail-closed without them).

- Package SM + SQL assign-bridge: `@gtr/delivery`
- Edge fire-and-assign bridge (unchanged): `supabase/functions/delivery-dispatch-cycle`
- **No Fleetbase.** AI never writes money / auto-POs.

## Run (local)

1. Temporal server (example): `temporal server start-dev`
2. Env (see `.env.example` — never commit values):

```bash
TEMPORAL_ADDRESS=localhost:7233   # required — no silent default
TEMPORAL_NAMESPACE=default
TEMPORAL_TASK_QUEUE=gtr-delivery-dispatch
SUPABASE_URL=…
SUPABASE_SERVICE_ROLE_KEY=…   # required; worker host only — never ship to clients
# Optional: DISPATCH_AUTO_ACCEPT_OFFERS=1  (parity with Edge cron auto-accept)
```

3. Start worker:

```bash
pnpm --filter @gtr/delivery-dispatch-worker start
```

## Start a workflow (client sketch)

Use Temporal UI / `tctl` / `@temporalio/client` with workflow type **`DeliveryDispatchWorkflow`**, task queue `gtr-delivery-dispatch`, args:

```json
{ "deliveryJobId": "<uuid>", "offerTimeoutSeconds": 30 }
```

## Tests

```bash
pnpm --filter @gtr/delivery-dispatch-worker test
```

Asserts workflow name parity with `@gtr/delivery` and SQL activity wiring.
