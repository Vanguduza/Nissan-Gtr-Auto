"use client";

import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import styles from "./vehicle-selector.module.css";

export function VehicleSelector() {
  const router = useRouter();
  const [make, setMake] = useState("Nissan");
  const [model, setModel] = useState("");
  const [engine, setEngine] = useState("");
  const [vin, setVin] = useState("");

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (vin.trim().length >= 11) {
      router.push(`/search?mode=vin&q=${encodeURIComponent(vin.trim())}`);
      return;
    }
    const q = [make, model, engine].filter(Boolean).join(" ");
    router.push(`/search?mode=model&q=${encodeURIComponent(q)}`);
  }

  return (
    <form className={styles.form} onSubmit={onSubmit} aria-label="Vehicle selector">
      <div className={styles.grid}>
        <label className={styles.field}>
          Make
          <select value={make} onChange={(e) => setMake(e.target.value)}>
            <option>Nissan</option>
            <option>Infiniti</option>
          </select>
        </label>
        <label className={styles.field}>
          Model
          <input
            value={model}
            onChange={(e) => setModel(e.target.value)}
            placeholder="e.g. Navara D40"
            required={!vin}
          />
        </label>
        <label className={styles.field}>
          Engine
          <input
            value={engine}
            onChange={(e) => setEngine(e.target.value)}
            placeholder="e.g. YD25"
          />
        </label>
        <label className={styles.field}>
          Or VIN
          <input
            value={vin}
            onChange={(e) => setVin(e.target.value)}
            placeholder="17 characters"
            maxLength={17}
            autoComplete="off"
          />
        </label>
      </div>
      <p className={styles.note}>
        No UK registration-plate lookup — VIN or make/model/engine only.
      </p>
      <button type="submit" className={styles.submit}>
        Find parts for this vehicle
      </button>
    </form>
  );
}
