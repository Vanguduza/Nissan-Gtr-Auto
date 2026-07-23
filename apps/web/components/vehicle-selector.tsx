"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useMemo, useState } from "react";
import { VEHICLE_CATALOG } from "@/lib/vehicle-catalog";
import styles from "./vehicle-selector.module.css";

export function VehicleSelector() {
  const router = useRouter();
  const [makerId, setMakerId] = useState("");
  const [modelId, setModelId] = useState("");
  const [generationId, setGenerationId] = useState("");
  const [engineId, setEngineId] = useState("");
  const [vin, setVin] = useState("");

  const maker = useMemo(
    () => VEHICLE_CATALOG.find((m) => m.id === makerId),
    [makerId],
  );
  const model = useMemo(
    () => maker?.models.find((m) => m.id === modelId),
    [maker, modelId],
  );
  const generation = useMemo(
    () => model?.generations.find((g) => g.id === generationId),
    [model, generationId],
  );

  function pickMaker(id: string) {
    setMakerId(id);
    setModelId("");
    setGenerationId("");
    setEngineId("");
  }

  function pickModel(id: string) {
    setModelId(id);
    setGenerationId("");
    setEngineId("");
  }

  function pickGeneration(id: string) {
    setGenerationId(id);
    setEngineId("");
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (vin.trim().length >= 11) {
      router.push(`/search?mode=vin&q=${encodeURIComponent(vin.trim())}`);
      return;
    }
    if (!maker || !model || !generation || !engineId) return;
    const engine = generation.engines.find((en) => en.id === engineId);
    const q = [maker.label, model.label, generation.label, engine?.label]
      .filter(Boolean)
      .join(" · ");
    router.push(`/search?mode=model&q=${encodeURIComponent(q)}`);
  }

  const canSubmitCascade = Boolean(makerId && modelId && generationId && engineId);
  const canSubmitVin = vin.trim().length >= 11;

  return (
    <form className={styles.form} onSubmit={onSubmit} aria-label="Vehicle selector">
      <ol className={styles.steps} aria-label="Selection steps">
        <li className={makerId ? styles.stepDone : styles.stepActive}>1 · Maker</li>
        <li className={modelId ? styles.stepDone : makerId ? styles.stepActive : styles.stepIdle}>
          2 · Model
        </li>
        <li
          className={
            generationId
              ? styles.stepDone
              : modelId
                ? styles.stepActive
                : styles.stepIdle
          }
        >
          3 · Generation
        </li>
        <li
          className={
            engineId ? styles.stepDone : generationId ? styles.stepActive : styles.stepIdle
          }
        >
          4 · Engine
        </li>
      </ol>

      <div className={styles.grid}>
        <label className={styles.field}>
          Select maker
          <select
            value={makerId}
            onChange={(e) => pickMaker(e.target.value)}
            required={!canSubmitVin}
          >
            <option value="">Select maker</option>
            {VEHICLE_CATALOG.map((m) => (
              <option key={m.id} value={m.id}>
                {m.label}
              </option>
            ))}
          </select>
        </label>

        <label className={styles.field}>
          Select model
          <select
            value={modelId}
            onChange={(e) => pickModel(e.target.value)}
            disabled={!maker}
            required={!canSubmitVin}
          >
            <option value="">
              {maker ? "Select model" : "Choose maker first"}
            </option>
            {maker?.models.map((m) => (
              <option key={m.id} value={m.id}>
                {m.label}
              </option>
            ))}
          </select>
        </label>

        <label className={styles.field}>
          Select generation
          <select
            value={generationId}
            onChange={(e) => pickGeneration(e.target.value)}
            disabled={!model}
            required={!canSubmitVin}
          >
            <option value="">
              {model ? "Select generation" : "Choose model first"}
            </option>
            {model?.generations.map((g) => (
              <option key={g.id} value={g.id}>
                {g.label}
              </option>
            ))}
          </select>
        </label>

        <label className={styles.field}>
          Select engine
          <select
            value={engineId}
            onChange={(e) => setEngineId(e.target.value)}
            disabled={!generation}
            required={!canSubmitVin}
          >
            <option value="">
              {generation ? "Select engine" : "Choose generation first"}
            </option>
            {generation?.engines.map((en) => (
              <option key={en.id} value={en.id}>
                {en.label}
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
          onChange={(e) => setVin(e.target.value.toUpperCase())}
          placeholder="17 characters"
          maxLength={17}
          autoComplete="off"
          spellCheck={false}
        />
      </label>

      <p className={styles.note}>
        Options filter from the previous step. No UK registration-plate lookup —
        maker/model/generation/engine or VIN only.
      </p>
      <button
        type="submit"
        className={styles.submit}
        disabled={!canSubmitCascade && !canSubmitVin}
      >
        Find parts for this vehicle
      </button>
    </form>
  );
}
