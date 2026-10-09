"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import {
  VehicleCascadeFields,
  vehicleCascadeCanSubmit,
  type VehicleCascadeFormValue,
} from "@/components/vehicle-cascade-fields";
import {
  listVehicleMaster,
  VehicleCascade,
  type VehicleMasterRow,
} from "@/lib/vehicle-catalog";
import { saveCustomerVehicle } from "@/lib/customer-vehicle-session";
import { createWebClient } from "@/lib/supabase";
import styles from "./vehicle-selector.module.css";

type VehicleSelectorProps = {
  /** Hide the helper under VIN (homepage). Default: show. */
  showNote?: boolean;
};

type LoadState =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ready"; rows: VehicleMasterRow[] };

const emptyForm: VehicleCascadeFormValue = {
  maker: "",
  model: "",
  generation: "",
  engine: "",
  vin: "",
};

/**
 * Customer vehicle selector.
 *
 * It reads the master derived from the approved catalog_v2 release. Selecting a vehicle stores the
 * canonical vehicle-master id plus chassis/engine context for shop filtering and EPC-backed
 * customer fitment checks. It never exposes or navigates the customer into EPC browsing.
 */
export function VehicleSelector({ showNote = true }: VehicleSelectorProps) {
  const router = useRouter();
  const [load, setLoad] = useState<LoadState>({ kind: "loading" });
  const [form, setForm] = useState<VehicleCascadeFormValue>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadRows() {
      const client = createWebClient();
      if (!client) {
        if (!cancelled) {
          setLoad({
            kind: "error",
            message: "Vehicle catalog is not configured on this environment.",
          });
        }
        return;
      }

      const result = await listVehicleMaster(client);
      if (cancelled) return;
      if (!result.ok) {
        setLoad({ kind: "error", message: result.error });
        return;
      }
      setLoad({ kind: "ready", rows: result.data });
    }

    void loadRows();
    return () => {
      cancelled = true;
    };
  }, []);

  const rows = load.kind === "ready" ? load.rows : [];
  const controlsDisabled = load.kind !== "ready";

  function completeSelection(
    selected: NonNullable<ReturnType<typeof VehicleCascade.fromCascade>>,
  ) {
    saveCustomerVehicle(selected);
    // Shop consumes the saved canonical vehicle context and resolves saleable stock through the
    // EPC-derived fitment index. Do not route to model/PNC/OEM technical search.
    router.push("/shop");
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);

    const vinTrim = form.vin.trim();
    if (vinTrim.length >= 11) {
      const resolved = VehicleCascade.resolveVin(rows, vinTrim);
      if (!resolved) {
        setFormError(
          "VIN not found in the published vehicle master. Choose the vehicle manually instead.",
        );
        return;
      }
      completeSelection(resolved);
      return;
    }

    if (!form.maker || !form.model || !form.generation) return;
    const engines = VehicleCascade.engines(
      rows,
      form.maker,
      form.model,
      form.generation,
    );
    if (engines.length > 0 && !form.engine) return;

    const selected = VehicleCascade.fromCascade(
      form.maker,
      form.model,
      form.generation,
      form.engine || null,
      rows,
    );
    if (!selected) {
      setFormError("Selection not found in the published vehicle master.");
      return;
    }
    completeSelection(selected);
  }

  if (load.kind === "loading") {
    return (
      <div className={styles.form} aria-busy="true" aria-live="polite">
        <p className={styles.note}>Loading Nissan vehicle master…</p>
      </div>
    );
  }

  if (load.kind === "error") {
    return (
      <div className={styles.form} role="alert">
        <p className={styles.note}>Vehicle catalog unavailable: {load.message}</p>
      </div>
    );
  }

  return (
    <form
      className={styles.form}
      onSubmit={onSubmit}
      aria-label="Vehicle selector"
    >
      <VehicleCascadeFields
        rows={rows}
        value={form}
        onChange={(next) => {
          setForm(next);
          setFormError(null);
        }}
        disabled={controlsDisabled}
        showNote={showNote}
        error={formError}
      />
      <button
        type="submit"
        className={styles.submit}
        disabled={
          controlsDisabled ||
          rows.length === 0 ||
          !vehicleCascadeCanSubmit(form, rows)
        }
      >
        Use this vehicle
      </button>
      <p className={styles.note}>
        Your vehicle is used to verify fitment against the hosted Nissan catalog and filter live
        saleable stock. Technical EPC data stays internal.
      </p>
    </form>
  );
}
