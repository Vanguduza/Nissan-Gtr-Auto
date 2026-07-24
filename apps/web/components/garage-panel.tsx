"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  deleteGarageVehicle,
  garageLabel,
  listGarageVehicles,
  requireSession,
  upsertGarageVehicle,
  type GarageVehicleRow,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "@/components/account.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; vehicles: GarageVehicleRow[] };

const emptyForm = {
  make: "Nissan",
  model: "",
  generation: "",
  engine: "",
  vin: "",
  isPrimary: true,
};

export function GaragePanel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [form, setForm] = useState(emptyForm);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setStatus({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }

    const session = await requireSession(client);
    if (!session.ok) {
      setStatus({ kind: "auth" });
      return;
    }

    const vehicles = await listGarageVehicles(client);
    if (!vehicles.ok) {
      setStatus({ kind: "error", message: vehicles.error });
      return;
    }
    setStatus({ kind: "ready", vehicles: vehicles.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }

    const result = await upsertGarageVehicle(client, {
      make: form.make.trim() || null,
      model: form.model.trim() || null,
      generation: form.generation.trim() || null,
      engine: form.engine.trim() || null,
      vin: form.vin.trim() || null,
      isPrimary: form.isPrimary,
    });
    setBusy(false);
    if (!result.ok) {
      setMessage(result.error);
      return;
    }
    setForm({ ...emptyForm, isPrimary: false });
    setMessage("Vehicle saved.");
    await refresh();
  }

  async function onDelete(id: string) {
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const result = await deleteGarageVehicle(client, id);
    setBusy(false);
    if (!result.ok) {
      setMessage(result.error);
      return;
    }
    await refresh();
  }

  async function onSetPrimary(v: GarageVehicleRow) {
    setBusy(true);
    const client = createWebClient();
    if (!client) {
      setBusy(false);
      return;
    }
    const result = await upsertGarageVehicle(client, {
      id: v.id,
      isPrimary: true,
    });
    setBusy(false);
    if (!result.ok) {
      setMessage(result.error);
      return;
    }
    await refresh();
  }

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading garage…</p>;
  }

  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> to manage My Garage vehicles.
      </p>
    );
  }

  if (status.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {status.message}
      </p>
    );
  }

  return (
    <>
      <ul className={styles.list}>
        {status.vehicles.length === 0 ? (
          <li className={styles.muted}>No vehicles saved yet.</li>
        ) : (
          status.vehicles.map((v) => (
            <li key={v.id}>
              <strong>{garageLabel(v)}</strong>
              {v.is_primary ? (
                <p className={styles.muted}>Active for sticky “Shopping for”</p>
              ) : null}
              {v.vin ? <p className={styles.muted}>VIN {v.vin}</p> : null}
              <div className={styles.addrActions}>
                {!v.is_primary ? (
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy}
                    onClick={() => void onSetPrimary(v)}
                  >
                    Set primary
                  </button>
                ) : null}
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void onDelete(v.id)}
                >
                  Remove
                </button>
                <Link href="/vehicle" className={styles.btnGhost}>
                  Browse by vehicle
                </Link>
              </div>
            </li>
          ))
        )}
      </ul>

      <form className={styles.form} onSubmit={onSubmit} style={{ marginTop: "1.25rem" }}>
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Add vehicle</legend>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Make
              <input
                value={form.make}
                onChange={(e) => setForm((f) => ({ ...f, make: e.target.value }))}
              />
            </label>
            <label className={styles.field}>
              Model
              <input
                value={form.model}
                onChange={(e) => setForm((f) => ({ ...f, model: e.target.value }))}
              />
            </label>
            <label className={styles.field}>
              Generation
              <input
                value={form.generation}
                onChange={(e) =>
                  setForm((f) => ({ ...f, generation: e.target.value }))
                }
              />
            </label>
            <label className={styles.field}>
              Engine
              <input
                value={form.engine}
                onChange={(e) => setForm((f) => ({ ...f, engine: e.target.value }))}
              />
            </label>
            <label className={styles.field}>
              VIN (optional)
              <input
                value={form.vin}
                onChange={(e) => setForm((f) => ({ ...f, vin: e.target.value }))}
                autoComplete="off"
              />
            </label>
            <label className={styles.checkField}>
              <input
                type="checkbox"
                checked={form.isPrimary}
                onChange={(e) =>
                  setForm((f) => ({ ...f, isPrimary: e.target.checked }))
                }
              />
              Set as primary
            </label>
          </div>
        </fieldset>
        <div className={styles.formActions}>
          <button type="submit" className={styles.btn} disabled={busy}>
            {busy ? "Saving…" : "Save vehicle"}
          </button>
          {message ? <p className={styles.formStatus}>{message}</p> : null}
        </div>
      </form>

      <p className={styles.muted} style={{ marginTop: "1rem" }}>
        Service reminders stay deferred until the marketing channel is live.
      </p>
    </>
  );
}
