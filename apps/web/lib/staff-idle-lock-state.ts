import { STAFF_IDLE_LOCK_MS } from "@/lib/staff-auth";

/** sessionStorage key — survives reload in the same tab; cleared on sign-out. */
export const STAFF_IDLE_LOCK_STORAGE_KEY = "gtr.staff.idleLock.v1";

export type StaffIdleLockPersisted = {
  lastActiveAt: number;
  locked: boolean;
};

/**
 * Decide idle-lock UI state from persisted timestamps.
 * Reload must not reset an idle / locked staff shell while GoTrue refresh still works.
 */
export function evaluateStaffIdleLock(
  persisted: StaffIdleLockPersisted | null,
  now: number,
  idleMs: number = STAFF_IDLE_LOCK_MS,
): StaffIdleLockPersisted {
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

export function parseStaffIdleLockPersisted(
  raw: string | null,
): StaffIdleLockPersisted | null {
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<StaffIdleLockPersisted>;
    if (
      typeof parsed.lastActiveAt !== "number" ||
      !Number.isFinite(parsed.lastActiveAt) ||
      typeof parsed.locked !== "boolean"
    ) {
      return null;
    }
    return { lastActiveAt: parsed.lastActiveAt, locked: parsed.locked };
  } catch {
    return null;
  }
}

export function readStaffIdleLockStorage(
  storage: Pick<Storage, "getItem"> | null | undefined = typeof sessionStorage !==
  "undefined"
    ? sessionStorage
    : null,
): StaffIdleLockPersisted | null {
  if (!storage) return null;
  try {
    return parseStaffIdleLockPersisted(
      storage.getItem(STAFF_IDLE_LOCK_STORAGE_KEY),
    );
  } catch {
    return null;
  }
}

export function writeStaffIdleLockStorage(
  state: StaffIdleLockPersisted,
  storage: Pick<Storage, "setItem"> | null | undefined = typeof sessionStorage !==
  "undefined"
    ? sessionStorage
    : null,
): void {
  if (!storage) return;
  try {
    storage.setItem(STAFF_IDLE_LOCK_STORAGE_KEY, JSON.stringify(state));
  } catch {
    // Quota / private mode — idle lock still works in-memory for this mount.
  }
}

export function clearStaffIdleLockStorage(
  storage: Pick<Storage, "removeItem"> | null | undefined = typeof sessionStorage !==
  "undefined"
    ? sessionStorage
    : null,
): void {
  if (!storage) return;
  try {
    storage.removeItem(STAFF_IDLE_LOCK_STORAGE_KEY);
  } catch {
    // ignore
  }
}
