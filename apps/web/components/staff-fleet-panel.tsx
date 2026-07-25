"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  FLEET_STATUSES,
  listFleetVehicles,
  requireSession,
  setFleetVehicleStatus,
  upsertFleetVehicle,
  type FleetVehicleRow,
  type FleetVehicleStatus,
} from "@/lib/staff-fleet";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; vehicles: FleetVehicleRow[] };

type FormState = {
  id: string | null;
  plate: string;
  label: string;
  status: FleetVehicleStatus;
  assignedDriverUserId: string;
  notes: string;
};

const emptyForm = (): FormState => ({
  id: null,
  plate: "",
  label: "",
  status: "active",
  assignedDriverUserId: "",
  notes: "",
});

function formFromRow(v: FleetVehicleRow): FormState {
  return {
    id: v.id,
    plate: v.plate,
    label: v.label ?? "",
    status: v.status,
    assignedDriverUserId: v.assigned_driver_user_id ?? "",
    notes: v.notes ?? "",
  };
}

/**
 * Company fleet CRUD (plate / label / status / optional driver assignee).
 * Metadata only — no browser geolocation (Bridge-First GPS stays in delivery app).
 */
export function StaffFleetPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [form, setForm] = useState<FormState>(emptyForm);
  const [filter, setFilter] = useState<FleetVehicleStatus | "">("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }
    const vehicles = await listFleetVehicles(
      client,
      filter === "" ? null : filter,
    );
    if (!vehicles.ok) {
      setBoot({ kind: "error", message: vehicles.error });
      return;
    }
    setBoot({ kind: "ready", vehicles: vehicles.data });
  }, [filter]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) return;
    if (!form.plate.trim()) {
      setMessage("Plate is required.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await upsertFleetVehicle(client, {
      id: form.id,
      plate: form.plate,
      label: form.label,
      status: form.status,
      assignedDriverUserId: form.assignedDriverUserId,
      notes: form.notes,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(
      form.id
        ? `Updated ${res.data.slice(0, 8)}…`
        : `Created ${res.data.slice(0, 8)}…`,
    );
    setForm(emptyForm());
    await refresh();
  }

  async function onRetire(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await setFleetVehicleStatus(client, id, "retired");
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Retired ${res.data.slice(0, 8)}…`);
    if (form.id === id) setForm(emptyForm());
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading company fleet…</p>;
  }

  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with admin / warehouse / dispatcher
        staff access.
      </p>
    );
  }

  if (boot.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {boot.message}
      </p>
    );
  }

  return (
    <>
      <p className={styles.muted}>
        Company delivery/ops vehicles — not B2B “Fleet” price list, not customer
        garage. No browser GPS on this page.
      </p>

      <div className={styles.formActions}>
        <label className={styles.field}>
          Status filter
          <select
            value={filter}
            onChange={(e) =>
              setFilter((e.target.value || "") as FleetVehicleStatus | "")
            }
            disabled={busy}
          >
            <option value="">All</option>
            {FLEET_STATUSES.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          className={styles.btnGhost}
          disabled={busy}
          onClick={() => void refresh()}
        >
          Refresh
        </button>
      </div>

      <ul className={styles.list}>
        {boot.vehicles.length === 0 ? (
          <li className={styles.muted}>No fleet vehicles yet.</li>
        ) : (
          boot.vehicles.map((v) => (
            <li key={v.id}>
              <strong>
                {v.plate}
                {v.label ? ` — ${v.label}` : ""}
              </strong>
              <p className={styles.muted}>
                {v.status}
                {v.assigned_driver_user_id
                  ? ` · driver ${v.assigned_driver_user_id.slice(0, 8)}…`
                  : " · unassigned"}
              </p>
              {v.notes ? <p className={styles.muted}>{v.notes}</p> : null}
              <div className={styles.addrActions}>
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => setForm(formFromRow(v))}
                >
                  Edit
                </button>
                {v.status !== "retired" ? (
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy}
                    onClick={() => void onRetire(v.id)}
                  >
                    Retire
                  </button>
                ) : null}
              </div>
            </li>
          ))
        )}
      </ul>

      <form className={styles.form} onSubmit={(e) => void onSubmit(e)}>
        <h2 className={styles.title} style={{ fontSize: "1.1rem" }}>
          {form.id ? "Edit vehicle" : "Add vehicle"}
        </h2>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Plate
            <input
              value={form.plate}
              onChange={(e) =>
                setForm((f) => ({ ...f, plate: e.target.value }))
              }
              required
              disabled={busy}
              placeholder="AB-1234"
              autoComplete="off"
            />
          </label>
          <label className={styles.field}>
            Label / nickname
            <input
              value={form.label}
              onChange={(e) =>
                setForm((f) => ({ ...f, label: e.target.value }))
              }
              disabled={busy}
              placeholder="Van Alpha"
            />
          </label>
          <label className={styles.field}>
            Status
            <select
              value={form.status}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  status: e.target.value as FleetVehicleStatus,
                }))
              }
              disabled={busy}
            >
              {FLEET_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            Assigned driver user id
            <input
              value={form.assignedDriverUserId}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  assignedDriverUserId: e.target.value,
                }))
              }
              disabled={busy}
              placeholder="UUID (driver role) or blank"
              autoComplete="off"
            />
          </label>
        </div>
        <label className={styles.field}>
          Notes
          <textarea
            value={form.notes}
            onChange={(e) => setForm((f) => ({ ...f, notes: e.target.value }))}
            disabled={busy}
            rows={2}
          />
        </label>
        <div className={styles.formActions}>
          <button type="submit" className={styles.btn} disabled={busy}>
            {form.id ? "Save changes" : "Create vehicle"}
          </button>
          {form.id ? (
            <button
              type="button"
              className={styles.btnGhost}
              disabled={busy}
              onClick={() => setForm(emptyForm())}
            >
              Cancel edit
            </button>
          ) : null}
        </div>
        {message ? (
          <p className={styles.formStatus} role="status">
            {message}
          </p>
        ) : null}
      </form>
    </>
  );
}
