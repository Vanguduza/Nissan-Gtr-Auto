/**
 * Assert staff idle-lock persistence rules (no test runner in apps/web yet).
 * Run: node apps/web/scripts/assert-staff-idle-lock.mjs
 */

const IDLE_MS = 3 * 60_000;

function evaluateStaffIdleLock(persisted, now, idleMs = IDLE_MS) {
  if (!persisted || !Number.isFinite(persisted.lastActiveAt)) {
    return { lastActiveAt: now, locked: false };
  }
  if (persisted.locked) {
    return { lastActiveAt: persisted.lastActiveAt, locked: true };
  }
  if (now - persisted.lastActiveAt >= idleMs) {
    return { lastActiveAt: persisted.lastActiveAt, locked: true };
  }
  return { lastActiveAt: persisted.lastActiveAt, locked: false };
}

function assert(cond, msg) {
  if (!cond) throw new Error(msg);
}

const t0 = 1_000_000;

// Fresh session
{
  const r = evaluateStaffIdleLock(null, t0);
  assert(r.locked === false && r.lastActiveAt === t0, "fresh unlock");
}

// Active within window — reload must stay unlocked
{
  const r = evaluateStaffIdleLock(
    { lastActiveAt: t0, locked: false },
    t0 + IDLE_MS - 1,
  );
  assert(r.locked === false, "within idle window");
}

// Idle exceeded — reload must lock (the reported bug)
{
  const r = evaluateStaffIdleLock(
    { lastActiveAt: t0, locked: false },
    t0 + IDLE_MS,
  );
  assert(r.locked === true, "idle exceeded locks on reload");
}

// Explicit locked survives
{
  const r = evaluateStaffIdleLock(
    { lastActiveAt: t0, locked: true },
    t0 + 1,
  );
  assert(r.locked === true && r.lastActiveAt === t0, "explicit lock persists");
}

console.log("assert-staff-idle-lock: OK");
