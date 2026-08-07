"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  VehicleCascadeFields,
  vehicleCascadeCanSubmit,
  type VehicleCascadeFormValue,
} from "@/components/vehicle-cascade-fields";
import {
  deleteGarageVehicle,
  garageLabel,
  listGarageVehicles,
  requireSession,
  upsertGarageVehicle,
  type GarageVehicleRow,
} from "@/lib/customer-storefront";
import {
  listVehicleMaster,
  VehicleCascade,
  type VehicleMasterRow,
} from "@/lib/vehicle-catalog";
import {
  catalogPath,
  lookupVariantByChassis,
  saveEpcContext,
} from "@/lib/catalog-hierarchy";
import { createWebClient } from "@/lib/supabase";
import accountStyles from "@/components/account.module.css";
import cascadeStyles from "@/components/vehicle-selector.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; vehicles: GarageVehicleRow[] };

const emptyForm: VehicleCascadeFormValue = {
  maker: "",
  model: "",
  generation: "",
  engine: "",
  vin: "",
};

export function GaragePanel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [catalog, setCatalog] = useState<VehicleMasterRow[] | null>(null);
  const [catalogError, setCatalogError] = useState<string | null>(null);
  const [form, setForm] = useState<VehicleCascadeFormValue>(emptyForm);
  const [isPrimary, setIsPrimary] = useState(true);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

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

    const [vehicles, master] = await Promise.all([
      listGarageVehicles(client),
      listVehicleMaster(client),
    ]);

    if (!vehicles.ok) {
      setStatus({ kind: "error", message: vehicles.error });
      return;
    }

    if (!master.ok) {
      setCatalog([]);
      setCatalogError(master.error);
    } else {
      setCatalog(master.data);
      setCatalogError(null);
    }

    setStatus({ kind: "ready", vehicles: vehicles.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const rows = catalog ?? [];
  const catalogReady = catalog !== null && !catalogError;
  const controlsDisabled = !catalogReady || busy || rows.length === 0;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setFormError(null);
    setStatusMessage(null);
    const client = createWebClient();
    if (!client) {
      setFormError("Supabase is not configured.");
      setBusy(false);
      return;
    }

    const vinTrim = form.vin.trim().toUpperCase();
    let make: string | null = null;
    let model: string | null = null;
    let generation: string | null = null;
    let engine: string | null = null;
    let vin: string | null = null;

    if (vinTrim.length >= 11) {
      const resolved = VehicleCascade.resolveVin(rows, vinTrim);
      if (!resolved) {
        setFormError(
          "VIN not found in live catalog. Only prefixes present in vehicle_master are identifiable.",
        );
        setBusy(false);
        return;
      }
      make = resolved.make;
      model = resolved.model;
      generation = resolved.generation;
      engine = resolved.engine;
      vin = vinTrim;
    } else {
      if (!form.maker || !form.model || !form.generation) {
        setFormError("Select maker, model, and generation from the catalog.");
        setBusy(false);
        return;
      }
      const engines = VehicleCascade.engines(
        rows,
        form.maker,
        form.model,
        form.generation,
      );
      if (engines.length > 0 && !form.engine) {
        setFormError("Select an engine from the catalog.");
        setBusy(false);
        return;
      }
      const selected = VehicleCascade.fromCascade(
        form.maker,
        form.model,
        form.generation,
        form.engine || null,
        rows,
      );
      if (!selected) {
        setFormError("Selection not found in live catalog.");
        setBusy(false);
        return;
      }
      make = selected.make;
      model = selected.model;
      generation = selected.generation;
      engine = selected.engine;
      vin = vinTrim || null;
    }

    const result = await upsertGarageVehicle(client, {
      make,
      model,
      generation,
      engine,
      vin,
      isPrimary,
    });
    setBusy(false);
    if (!result.ok) {
      setFormError(result.error);
      return;
    }
    setForm(emptyForm);
    setIsPrimary(false);
    setStatusMessage("Vehicle saved.");
    await refresh();
  }

  async function onDelete(id: string) {
    setBusy(true);
    setFormError(null);
    setStatusMessage(null);
    const client = createWebClient();
    if (!client) {
      setFormError("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const result = await deleteGarageVehicle(client, id);
    setBusy(false);
    if (!result.ok) {
      setFormError(result.error);
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
      setFormError(result.error);
      return;
    }
    await refresh();
  }

  if (status.kind === "loading") {
    return <p className={accountStyles.muted}>Loading garage…</p>;
  }

  if (status.kind === "auth") {
    return (
      <p className={accountStyles.lede}>
        <Link href="/login">Sign in</Link> to manage My Garage vehicles.
      </p>
    );
  }

  if (status.kind === "error") {
    return (
      <p className={accountStyles.lede} role="alert">
        {status.message}
      </p>
    );
  }

  return (
    <>
      <ul className={accountStyles.list}>
        {status.vehicles.length === 0 ? (
          <li className={accountStyles.muted}>No vehicles saved yet.</li>
        ) : (
          status.vehicles.map((v) => (
            <li key={v.id}>
              <strong>{garageLabel(v)}</strong>
              {v.is_primary ? (
                <p className={accountStyles.muted}>
                  Active for sticky “Shopping for”
                </p>
              ) : null}
              {v.vin ? (
                <p className={accountStyles.muted}>VIN {v.vin}</p>
              ) : null}
              <div className={accountStyles.addrActions}>
                {!v.is_primary ? (
                  <button
                    type="button"
                    className={accountStyles.btnGhost}
                    disabled={busy}
                    onClick={() => void onSetPrimary(v)}
                  >
                    Set primary
                  </button>
                ) : null}
                <button
                  type="button"
                  className={accountStyles.btnGhost}
                  disabled={busy}
                  onClick={() => void onDelete(v.id)}
                >
                  Remove
                </button>
                <Link href="/vehicle" className={accountStyles.btnGhost}>
                  Browse by vehicle
                </Link>
                {v.generation ? (
                  <GarageEpcLink chassis={v.generation} />
                ) : null}
              </div>
            </li>
          ))
        )}
      </ul>

      <form
        className={cascadeStyles.form}
        onSubmit={onSubmit}
        style={{ marginTop: "1.25rem" }}
        aria-label="Add garage vehicle"
      >
        <p
          className={cascadeStyles.note}
          style={{
            margin: 0,
            fontFamily: "var(--font-display)",
            fontWeight: 700,
            letterSpacing: "0.06em",
            textTransform: "uppercase",
            color: "var(--gtr-steel)",
          }}
        >
          Add vehicle
        </p>
        {catalogError ? (
          <p className={cascadeStyles.error} role="alert">
            Catalog unavailable: {catalogError}
          </p>
        ) : null}
        <VehicleCascadeFields
          rows={rows}
          value={form}
          onChange={(next) => {
            setForm(next);
            setFormError(null);
            setStatusMessage(null);
          }}
          disabled={controlsDisabled}
          showNote
          error={formError}
        >
          <label className={accountStyles.checkField}>
            <input
              type="checkbox"
              checked={isPrimary}
              onChange={(e) => setIsPrimary(e.target.checked)}
              disabled={busy}
            />
            Set as primary
          </label>
        </VehicleCascadeFields>
        <button
          type="submit"
          className={cascadeStyles.submit}
          disabled={
            busy ||
            !catalogReady ||
            rows.length === 0 ||
            !vehicleCascadeCanSubmit(form, rows)
          }
        >
          {busy ? "Saving…" : "Save vehicle"}
        </button>
        {statusMessage ? (
          <p className={accountStyles.formStatus}>{statusMessage}</p>
        ) : null}
      </form>

      <p className={accountStyles.muted} style={{ marginTop: "1rem" }}>
        Service reminders stay deferred until the marketing channel is live.
      </p>
    </>
  );
}

function GarageEpcLink({ chassis }: { chassis: string }) {
  const [href, setHref] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function run() {
      const client = createWebClient();
      if (!client) return;
      const ctx = await lookupVariantByChassis(client, chassis);
      if (cancelled || !ctx) return;
      saveEpcContext(ctx);
      setHref(catalogPath(ctx));
    }
    void run();
    return () => {
      cancelled = true;
    };
  }, [chassis]);

  if (!href) return null;
  return (
    <Link href={href} className={accountStyles.btnGhost}>
      Browse EPC diagrams
    </Link>
  );
}
