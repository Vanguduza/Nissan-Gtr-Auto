"use client";

import { Car, ChevronDown, Pin, ScanBarcode, Search } from "lucide-react";
import { useEffect, useState } from "react";
import type { PosStore } from "@/lib/pos/use-pos";
import type { WindowClass } from "@/lib/pos/window-class";
import { Modal } from "./PosDialogs";
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

/** Model → Generation → Engine. Make is never shown (owner decision D2). */
function CascadeFields({ pos }: { pos: PosStore }) {
  return (
    <>
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
    </>
  );
}

function PinVehicleButton({ pos }: { pos: PosStore }) {
  return (
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
  );
}

/** Medium and Compact: one field that opens the cascade as a focus dialog (blueprint §8.4). */
function VehicleButton({ pos, sheet }: { pos: PosStore; sheet: boolean }) {
  const [open, setOpen] = useState(false);
  const label = pos.vehicle ? `${pos.vehicle.modelName} ${pos.vehicle.chassisCode} ${pos.vehicle.engineCode}` : "Select vehicle";
  return (
    <>
      <button type="button" className={`${styles.vehicleButton} ${styles.focusable}`} onClick={() => setOpen(true)} aria-haspopup="dialog">
        <Car size={18} aria-hidden />
        <span className={styles.vehicleButtonLabel}>{label}</span>
        <ChevronDown size={16} aria-hidden />
      </button>
      {open ? (
        <Modal title="Choose vehicle" sheet={sheet} onClose={() => setOpen(false)}>
          <div className={styles.cascadeDialog}>
            <CascadeFields pos={pos} />
          </div>
          <div className={styles.rowEnd}>
            {pos.vehicle ? (
              <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => { pos.clearVehicle(); }}>
                Clear
              </button>
            ) : null}
            <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} disabled={!pos.vehicle} onClick={() => void pos.pinVehicle()}>
              <Pin size={14} aria-hidden /> Pin
            </button>
            <button type="button" className={styles.primaryButton} onClick={() => setOpen(false)}>
              Done
            </button>
          </div>
        </Modal>
      ) : null}
    </>
  );
}

/**
 * Header: vehicle cascade (zone 1, replaces the taxonomy line — delta D-002; Make never shown —
 * D2), search with scanner input (keyboard-wedge / companion only — web delta W-002), operator, clock.
 */
export function PosHeader({ pos, windowClass }: { pos: PosStore; windowClass: WindowClass }) {
  const now = useClock();
  return (
    <header className={styles.header}>
      {windowClass === "expanded" ? (
        <div className={styles.cascade} role="group" aria-label="Vehicle">
          <CascadeFields pos={pos} />
          <PinVehicleButton pos={pos} />
        </div>
      ) : (
        <VehicleButton pos={pos} sheet={windowClass === "compact"} />
      )}

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

      <div className={`${styles.operator} ${windowClass === "compact" ? styles.operatorCompact : ""}`}>
        <span className={styles.avatar} aria-hidden>
          {initials(pos.operator)}
        </span>
        <span>
          <div className={styles.operatorName}>{pos.operator}</div>
          <div className={styles.operatorSub}>POS</div>
        </span>
      </div>

      {windowClass === "compact" ? null : <div className={styles.clock} aria-live="off">
        <div className={styles.clockDate}>
          {now ? now.toLocaleDateString("en-ZW", { weekday: "short", day: "numeric", month: "short", year: "numeric" }) : ""}
        </div>
        <div className={styles.clockTime}>
          {now ? now.toLocaleTimeString("en-ZW", { hour: "2-digit", minute: "2-digit" }) : ""}
        </div>
      </div>}
    </header>
  );
}
