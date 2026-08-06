"use client";

import { useMemo, type ReactNode } from "react";
import {
  VehicleCascade,
  type VehicleMasterRow,
} from "@/lib/vehicle-catalog";
import styles from "./vehicle-selector.module.css";

export type VehicleCascadeFormValue = {
  maker: string;
  model: string;
  generation: string;
  engine: string;
  vin: string;
};

type VehicleCascadeFieldsProps = {
  rows: VehicleMasterRow[];
  value: VehicleCascadeFormValue;
  onChange: (next: VehicleCascadeFormValue) => void;
  disabled?: boolean;
  /** Show UK-plate / cascade helper under VIN. Default: true. */
  showNote?: boolean;
  /** Inline validation message (VIN miss, etc.). */
  error?: string | null;
  /** Extra content after VIN (e.g. garage primary checkbox). */
  children?: ReactNode;
};

/**
 * Shared Maker|Model → Generation|Engine cascade + VIN path.
 * Options come only from live `vehicle_master` rows via {@link VehicleCascade}.
 */
export function VehicleCascadeFields({
  rows,
  value,
  onChange,
  disabled = false,
  showNote = true,
  error = null,
  children,
}: VehicleCascadeFieldsProps) {
  const { maker, model, generation, engine, vin } = value;

  const makers = useMemo(() => VehicleCascade.makers(rows), [rows]);
  const models = useMemo(
    () => (maker ? VehicleCascade.models(rows, maker) : []),
    [rows, maker],
  );
  const generations = useMemo(
    () =>
      maker && model
        ? VehicleCascade.generations(rows, maker, model)
        : [],
    [rows, maker, model],
  );
  const engines = useMemo(
    () =>
      maker && model && generation
        ? VehicleCascade.engines(rows, maker, model, generation)
        : [],
    [rows, maker, model, generation],
  );

  const canSubmitVin = vin.trim().length >= 11;

  function patch(partial: Partial<VehicleCascadeFormValue>) {
    onChange({ ...value, ...partial });
  }

  function pickMaker(id: string) {
    patch({ maker: id, model: "", generation: "", engine: "" });
  }

  function pickModel(id: string) {
    patch({ model: id, generation: "", engine: "" });
  }

  function pickGeneration(id: string) {
    patch({ generation: id, engine: "" });
  }

  return (
    <>
      <ol className={styles.steps} aria-label="Selection steps">
        <li className={maker ? styles.stepDone : styles.stepActive}>
          1 · Maker
        </li>
        <li
          className={
            model
              ? styles.stepDone
              : maker
                ? styles.stepActive
                : styles.stepIdle
          }
        >
          2 · Model
        </li>
        <li
          className={
            generation
              ? styles.stepDone
              : model
                ? styles.stepActive
                : styles.stepIdle
          }
        >
          3 · Generation
        </li>
        <li
          className={
            engine || (generation && engines.length === 0)
              ? styles.stepDone
              : generation
                ? styles.stepActive
                : styles.stepIdle
          }
        >
          4 · Engine
        </li>
      </ol>

      {rows.length === 0 ? (
        <p className={styles.note}>No vehicles loaded from catalog yet.</p>
      ) : makers.length === 0 ? (
        <p className={styles.note} role="status">
          Catalog rows loaded, but no identifiable makers (check VIN prefixes /
          model brands in vehicle_master).
        </p>
      ) : null}

      <div className={styles.grid}>
        <label className={styles.field}>
          Select maker
          <select
            value={maker}
            onChange={(e) => pickMaker(e.target.value)}
            required={!canSubmitVin}
            disabled={disabled || makers.length === 0}
          >
            <option value="">Select maker</option>
            {makers.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </label>

        <label className={styles.field}>
          Select model
          <select
            value={model}
            onChange={(e) => pickModel(e.target.value)}
            disabled={disabled || !maker}
            required={!canSubmitVin}
          >
            <option value="">
              {maker ? "Select model" : "Choose maker first"}
            </option>
            {models.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </label>

        <label className={styles.field}>
          Select generation
          <select
            value={generation}
            onChange={(e) => pickGeneration(e.target.value)}
            disabled={disabled || !model}
            required={!canSubmitVin}
          >
            <option value="">
              {model ? "Select generation" : "Choose model first"}
            </option>
            {generations.map((g) => (
              <option key={g} value={g}>
                {g}
              </option>
            ))}
          </select>
        </label>

        <label className={styles.field}>
          Select engine
          <select
            value={engine}
            onChange={(e) => patch({ engine: e.target.value })}
            disabled={disabled || !generation || engines.length === 0}
            required={!canSubmitVin && engines.length > 0}
          >
            <option value="">
              {!generation
                ? "Choose generation first"
                : engines.length === 0
                  ? "No engine codes in catalog"
                  : "Select engine"}
            </option>
            {engines.map((en) => (
              <option key={en} value={en}>
                {en}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className={styles.divider} role="separator">
        or identify by VIN
      </div>

      <label className={styles.field}>
        VIN
        <input
          value={vin}
          onChange={(e) => patch({ vin: e.target.value.toUpperCase() })}
          placeholder="17 characters"
          maxLength={17}
          autoComplete="off"
          spellCheck={false}
          disabled={disabled}
        />
      </label>

      {error ? (
        <p className={styles.error} role="alert">
          {error}
        </p>
      ) : null}

      {showNote ? (
        <p className={styles.note}>
          Options filter from the previous step using live catalog rows only. No
          UK registration-plate lookup — maker/model/generation/engine or VIN.
        </p>
      ) : null}

      {children}
    </>
  );
}

/** Whether cascade or VIN path is complete enough to submit. */
export function vehicleCascadeCanSubmit(
  value: VehicleCascadeFormValue,
  rows: VehicleMasterRow[],
): boolean {
  const vinTrim = value.vin.trim();
  if (vinTrim.length >= 11) return true;
  if (!value.maker || !value.model || !value.generation) return false;
  const engines = VehicleCascade.engines(
    rows,
    value.maker,
    value.model,
    value.generation,
  );
  return engines.length === 0 || Boolean(value.engine);
}
