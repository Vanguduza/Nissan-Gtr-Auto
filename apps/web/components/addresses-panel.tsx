"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  deleteOwnAddress,
  listOwnAddresses,
  requireSession,
  upsertOwnAddress,
  type CustomerAddressRow,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "@/components/account.module.css";

type Draft = {
  id: string | null;
  label: string;
  line1: string;
  line2: string;
  city: string;
  province: string;
  postal: string;
  country: string;
  isDefault: boolean;
};

function toDraft(row: CustomerAddressRow): Draft {
  return {
    id: row.id,
    label: row.label,
    line1: row.line1,
    line2: row.line2 ?? "",
    city: row.city ?? "",
    province: row.province ?? "",
    postal: row.postal_code ?? "",
    country: row.country || "Zimbabwe",
    isDefault: row.is_default,
  };
}

function emptyDraft(makeDefault: boolean): Draft {
  return {
    id: null,
    label: "",
    line1: "",
    line2: "",
    city: "",
    province: "",
    postal: "",
    country: "Zimbabwe",
    isDefault: makeDefault,
  };
}

type LoadState =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; addresses: CustomerAddressRow[] };

export function AddressesPanel() {
  const [load, setLoad] = useState<LoadState>({ kind: "loading" });
  const [editing, setEditing] = useState<Draft | null>(null);
  const [busy, setBusy] = useState(false);
  const [status, setStatus] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setLoad({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setLoad({ kind: "auth" });
      return;
    }
    const rows = await listOwnAddresses(client);
    if (!rows.ok) {
      setLoad({ kind: "error", message: rows.error });
      return;
    }
    setLoad({ kind: "ready", addresses: rows.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  function startNew() {
    const makeDefault =
      load.kind === "ready" ? load.addresses.length === 0 : true;
    setEditing(emptyDraft(makeDefault));
    setStatus(null);
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setBusy(true);
    setStatus(null);
    const client = createWebClient();
    if (!client) {
      setStatus("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const result = await upsertOwnAddress(client, {
      id: editing.id,
      label: editing.label.trim(),
      line1: editing.line1.trim(),
      line2: editing.line2.trim() || null,
      city: editing.city.trim() || null,
      province: editing.province.trim() || null,
      postal_code: editing.postal.trim() || null,
      country: editing.country.trim() || "Zimbabwe",
      is_default: editing.isDefault,
    });
    setBusy(false);
    if (!result.ok) {
      setStatus(result.error);
      return;
    }
    setEditing(null);
    setStatus("Address saved.");
    await refresh();
  }

  async function makeDefault(id: string) {
    if (load.kind !== "ready") return;
    const row = load.addresses.find((a) => a.id === id);
    if (!row) return;
    setBusy(true);
    setStatus(null);
    const client = createWebClient();
    if (!client) {
      setStatus("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const result = await upsertOwnAddress(client, {
      id: row.id,
      label: row.label,
      line1: row.line1,
      line2: row.line2,
      city: row.city,
      province: row.province,
      postal_code: row.postal_code,
      country: row.country,
      is_default: true,
    });
    setBusy(false);
    if (!result.ok) {
      setStatus(result.error);
      return;
    }
    setStatus("Default address updated.");
    await refresh();
  }

  async function remove(id: string) {
    setBusy(true);
    setStatus(null);
    const client = createWebClient();
    if (!client) {
      setStatus("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const result = await deleteOwnAddress(client, id);
    setBusy(false);
    if (!result.ok) {
      setStatus(result.error);
      return;
    }
    if (editing?.id === id) setEditing(null);
    setStatus("Address removed.");
    await refresh();
  }

  if (load.kind === "loading") {
    return <p className={styles.muted}>Loading addresses…</p>;
  }

  if (load.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> to manage delivery addresses.
      </p>
    );
  }

  if (load.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {load.message}{" "}
        <button
          type="button"
          className={styles.btnGhost}
          onClick={() => void refresh()}
        >
          Retry
        </button>
      </p>
    );
  }

  const addresses = load.addresses;

  return (
    <div className={styles.addrWrap}>
      <p className={styles.muted} style={{ marginBottom: "1rem" }}>
        Shipping addresses on <code>customer_addresses</code>. Contact phone for
        ContiPay lives on{" "}
        <Link href="/account/profile">profile</Link>.
      </p>

      {addresses.length === 0 && !editing ? (
        <p className={styles.muted}>No saved addresses yet.</p>
      ) : (
        <ul className={styles.list}>
          {addresses.map((a) => (
            <li key={a.id}>
              <strong>
                {a.label || "Address"}
                {a.is_default ? " · Default" : ""}
              </strong>
              <p className={styles.muted}>
                {[a.line1, a.line2, a.city, a.province, a.country]
                  .filter(Boolean)
                  .join(", ")}
              </p>
              <div className={styles.addrActions}>
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => {
                    setEditing(toDraft(a));
                    setStatus(null);
                  }}
                >
                  Edit
                </button>
                {!a.is_default ? (
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy}
                    onClick={() => void makeDefault(a.id)}
                  >
                    Set default
                  </button>
                ) : null}
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void remove(a.id)}
                >
                  Remove
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      {!editing ? (
        <button
          type="button"
          className={styles.btn}
          disabled={busy}
          onClick={startNew}
        >
          Add address
        </button>
      ) : (
        <form className={styles.form} onSubmit={(e) => void onSave(e)}>
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>
              {editing.id ? "Edit address" : "New address"}
            </legend>
            <div className={styles.formGrid}>
              <label className={styles.field}>
                Label
                <input
                  value={editing.label}
                  onChange={(e) =>
                    setEditing({ ...editing, label: e.target.value })
                  }
                  placeholder="Home, Workshop…"
                  required
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Street address
                <input
                  value={editing.line1}
                  onChange={(e) =>
                    setEditing({ ...editing, line1: e.target.value })
                  }
                  autoComplete="address-line1"
                  required
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Line 2
                <input
                  value={editing.line2}
                  onChange={(e) =>
                    setEditing({ ...editing, line2: e.target.value })
                  }
                  autoComplete="address-line2"
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                City
                <input
                  value={editing.city}
                  onChange={(e) =>
                    setEditing({ ...editing, city: e.target.value })
                  }
                  autoComplete="address-level2"
                  required
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Province
                <input
                  value={editing.province}
                  onChange={(e) =>
                    setEditing({ ...editing, province: e.target.value })
                  }
                  autoComplete="address-level1"
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Postal code
                <input
                  value={editing.postal}
                  onChange={(e) =>
                    setEditing({ ...editing, postal: e.target.value })
                  }
                  autoComplete="postal-code"
                  disabled={busy}
                />
              </label>
              <label className={styles.field}>
                Country
                <input
                  value={editing.country}
                  onChange={(e) =>
                    setEditing({ ...editing, country: e.target.value })
                  }
                  autoComplete="country-name"
                  required
                  disabled={busy}
                />
              </label>
              <label className={styles.checkField}>
                <input
                  type="checkbox"
                  checked={editing.isDefault}
                  disabled={busy}
                  onChange={(e) =>
                    setEditing({ ...editing, isDefault: e.target.checked })
                  }
                />
                Default delivery address
              </label>
            </div>
          </fieldset>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy}>
              {busy ? "Saving…" : "Save address"}
            </button>
            <button
              type="button"
              className={styles.btnGhost}
              disabled={busy}
              onClick={() => setEditing(null)}
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {status ? (
        <p className={styles.formStatus} role="status">
          {status}
        </p>
      ) : null}
    </div>
  );
}
