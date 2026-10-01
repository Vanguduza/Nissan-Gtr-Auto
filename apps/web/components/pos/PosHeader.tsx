"use client";

import { Pin, ScanBarcode, Search } from "lucide-react";
import { useEffect, useState } from "react";
import type { PosStore } from "@/lib/pos/use-pos";
import styles from "./pos.module.css";

function initials(name: string): string {
  const parts = name.replace(/@.*/, "").split(/[\s._-]+/).filter(Boolean);
  return (parts[0]?.[0] ?? "O").toUpperCase() + (parts[1]?.[0] ?? parts[0]?.[1] ?? "").toUpperCase();
}

function useClock() {
  const [now, setNow] = useState<Date | null>(null);
  useEffect(() => {
    setNow(new Date());
    const t = window.setInterval(() => setNow(new Date()), 15_000);
    return () => window.clearInterval(t);
  }, []);
  return now;
}

/**
 * Header: vehicle cascade (zone 1, replaces the taxonomy line — delta D-002; Make never shown —
 * D2), search with scanner input (keyboard-wedge / companion only — web delta W-002), operator, clock.
 */
export function PosHeader({ pos }: { pos: PosStore }) {
  const now = useClock();
  return (
    <header className={styles.header}>
      <div className={styles.cascade} role="group" aria-label="Vehicle">
        <label className={styles.cascadeField}>
          <span className={styles.cascadeLabel}>Model</span>
          <select
            className={`${styles.cascadeSelect} ${styles.focusable}`}
            value={pos.modelSlug}
            onChange={(e) => void pos.selectModel(e.target.value)}
          >
            <option value="">Select model</option>
            {pos.models.map((m) => (
              <option key={m.slug} value={m.slug}>
                {m.name}
              </option>
            ))}
          </select>
        </label>
        <label className={styles.cascadeField}>
          <span className={styles.cascadeLabel}>Generation</span>
          <select
            className={`${styles.cascadeSelect} ${styles.focusable}`}
            value={pos.chassisCode}
            disabled={!pos.modelSlug}
            onChange={(e) => pos.selectGeneration(e.target.value)}
          >
            <option value="">Generation</option>
            {pos.generations.map((g) => (
              <option key={g.chassisCode} value={g.chassisCode}>
                {g.label}
              </option>
            ))}
          </select>
        </label>
        <label className={styles.cascadeField}>
          <span className={styles.cascadeLabel}>Engine</span>
          <select
            className={`${styles.cascadeSelect} ${styles.focusable}`}
            value={pos.engineCode}
            disabled={!pos.chassisCode}
            onChange={(e) => pos.selectEngine(e.target.value)}
          >
            <option value="">Engine</option>
            {pos.engines.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          className={styles.iconButton}
          style={{ alignSelf: "flex-end" }}
          disabled={!pos.vehicle}
          aria-label="Pin this vehicle to Popular Items"
          title="Pin this vehicle to Popular Items"
          onClick={() => void pos.pinVehicle()}
        >
          <Pin size={16} aria-hidden />
        </button>
      </div>

      <form
        className={styles.search}
        role="search"
        onSubmit={(e) => {
          e.preventDefault();
          void pos.runSearch(pos.query);
        }}
      >
        <Search size={20} strokeWidth={1.75} aria-hidden />
        <input
          className={styles.searchInput}
          type="search"
          value={pos.query}
          onChange={(e) => pos.setQuery(e.target.value)}
          placeholder={
            pos.vehicle
              ? `Search parts for ${pos.vehicle.modelName} ${pos.vehicle.chassisCode}…`
              : "Search by part name, part number, or vehicle model…"
          }
          aria-label="Search spares"
          autoComplete="off"
        />
        <button
          type="submit"
          className={styles.iconButton}
          aria-label="Search (scanner input is typed here)"
          title="Scan with a USB/Bluetooth scanner into this field, or pair the companion phone in Settings"
        >
          <ScanBarcode size={22} strokeWidth={1.75} aria-hidden />
        </button>
      </form>

      <div className={styles.operator}>
        <span className={styles.avatar} aria-hidden>
          {initials(pos.operator)}
        </span>
        <span>
          <div className={styles.operatorName}>{pos.operator}</div>
          <div className={styles.operatorSub}>POS</div>
        </span>
      </div>

      <div className={styles.clock} aria-live="off">
        <div className={styles.clockDate}>
          {now ? now.toLocaleDateString("en-ZW", { weekday: "short", day: "numeric", month: "short", year: "numeric" }) : ""}
        </div>
        <div className={styles.clockTime}>
          {now ? now.toLocaleTimeString("en-ZW", { hour: "2-digit", minute: "2-digit" }) : ""}
        </div>
      </div>
    </header>
  );
}
