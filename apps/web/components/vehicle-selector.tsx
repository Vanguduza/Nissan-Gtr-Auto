"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { FormEvent, useEffect, useState } from "react";
import {
  VehicleCascadeFields,
  vehicleCascadeCanSubmit,
  type VehicleCascadeFormValue,
} from "@/components/vehicle-cascade-fields";
import {
  listVehicleMaster,
  searchQueryForVehicle,
  VehicleCascade,
  type VehicleMasterRow,
} from "@/lib/vehicle-catalog";
import {
  catalogPath,
  lookupVariantByChassis,
  saveEpcContext,
  type CatalogBrowseContext,
} from "@/lib/catalog-hierarchy";
import { createWebClient } from "@/lib/supabase";
import styles from "./vehicle-selector.module.css";

type VehicleSelectorProps = {
  /** Hide the UK-plate / cascade helper under VIN (homepage). Default: show. */
  showNote?: boolean;
};

type LoadState =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; rows: VehicleMasterRow[] };

const emptyForm: VehicleCascadeFormValue = {
  maker: "",
  model: "",
  generation: "",
  engine: "",
  vin: "",
};

export function VehicleSelector({ showNote = true }: VehicleSelectorProps) {
  const router = useRouter();
  const pathname = usePathname();
  const [load, setLoad] = useState<LoadState>({ kind: "loading" });
  const [form, setForm] = useState<VehicleCascadeFormValue>(emptyForm);
  const [formError, setFormError] = useState<string | null>(null);
  const [epcCtx, setEpcCtx] = useState<CatalogBrowseContext | null>(null);
  const [epcChecked, setEpcChecked] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadRows() {
      const client = createWebClient();
      if (!client) {
        if (!cancelled) {
          setLoad({
            kind: "error",
            message: "Supabase is not configured on this environment.",
          });
        }
        return;
      }

      const { data: sessionData } = await client.auth.getSession();
      if (!sessionData.session) {
        if (!cancelled) setLoad({ kind: "auth" });
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

  async function resolveEpcLink(chassis: string | null | undefined) {
    const code = chassis?.trim();
    if (!code) {
      setEpcCtx(null);
      return;
    }
    const client = createWebClient();
    if (!client) return;
    const ctx = await lookupVariantByChassis(client, code);
    if (ctx) {
      saveEpcContext(ctx);
      setEpcCtx(ctx);
    } else {
      setEpcCtx(null);
    }
  }

  function navigateForVehicle(
    selected: NonNullable<ReturnType<typeof VehicleCascade.fromCascade>>,
  ) {
    void resolveEpcLink(selected.generation);
    if (selected.vin && selected.vin.length >= 11) {
      router.push(
        `/search?mode=vin&q=${encodeURIComponent(selected.vin)}`,
      );
      return;
    }
    const prefix = selected.vinPrefix?.trim();
    if (prefix) {
      router.push(
        `/search?mode=vin&q=${encodeURIComponent(prefix)}`,
      );
      return;
    }
    const q = searchQueryForVehicle(selected);
    if (!q) return;
    router.push(`/search?mode=model&q=${encodeURIComponent(q)}`);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);

    const vinTrim = form.vin.trim();
    if (vinTrim.length >= 11) {
      const resolved = VehicleCascade.resolveVin(rows, vinTrim);
      if (!resolved) {
        setFormError(
          "VIN not found in live catalog. Only prefixes present in vehicle_master are identifiable.",
        );
        return;
      }
      navigateForVehicle(resolved);
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
      setFormError("Selection not found in live catalog.");
      return;
    }
    navigateForVehicle(selected);
  }

  if (load.kind === "loading") {
    return (
      <div className={styles.form} aria-busy="true" aria-live="polite">
        <p className={styles.note}>Loading vehicles from catalog…</p>
      </div>
    );
  }

  if (load.kind === "auth") {
    const next = pathname || "/vehicle";
    return (
      <div className={styles.form}>
        <p className={styles.note}>
          Live vehicle selection requires a signed-in account.{" "}
          <Link href={`/login?next=${encodeURIComponent(next)}`}>Sign in</Link>{" "}
          to load maker / model / generation / engine from the catalog.
        </p>
      </div>
    );
  }

  if (load.kind === "error") {
    return (
      <div className={styles.form} role="alert">
        <p className={styles.note}>Catalog unavailable: {load.message}</p>
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
        Find parts for this vehicle
      </button>
      {epcCtx ? (
        <p className={styles.note}>
          <Link href={catalogPath(epcCtx)}>Browse EPC diagrams</Link> for
          chassis {epcCtx.variant ?? form.generation}
        </p>
      ) : form.generation ? (
        <p className={styles.note}>
          <button
            type="button"
            className={styles.submit}
            style={{ marginTop: "0.5rem" }}
            onClick={() => void resolveEpcLink(form.generation)}
          >
            Check EPC diagrams
          </button>
          {epcChecked === form.generation ? (
            <span>
              {" "}
              No EPC hierarchy for chassis {form.generation} (and no published
              alias). Use search by model, or Browse EPC from the maker hub.
            </span>
          ) : null}
        </p>
      ) : null}
    </form>
  );
}
