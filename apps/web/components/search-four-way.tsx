"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import styles from "./search-four-way.module.css";

const modes = [
  { id: "part", label: "Part #" },
  { id: "vin", label: "VIN" },
  { id: "model", label: "Model" },
  { id: "pnc", label: "PNC" },
] as const;

type Mode = (typeof modes)[number]["id"];

export function SearchFourWay({ compact = false }: { compact?: boolean }) {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("part");
  const [q, setQ] = useState("");

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const query = q.trim();
    if (!query) return;
    // Phase 7 replaces stub with Meilisearch / API
    router.push(`/search?mode=${mode}&q=${encodeURIComponent(query)}`);
  }

  return (
    <form
      className={compact ? styles.compact : styles.panel}
      onSubmit={onSubmit}
      aria-label="Parts search"
    >
      <div className={styles.modes} role="tablist" aria-label="Search by">
        {modes.map((m) => (
          <button
            key={m.id}
            type="button"
            role="tab"
            aria-selected={mode === m.id}
            className={mode === m.id ? styles.modeActive : styles.mode}
            onClick={() => setMode(m.id)}
          >
            {m.label}
          </button>
        ))}
      </div>
      <div className={styles.row}>
        <label className={styles.srOnly} htmlFor="gtr-search-q">
          Search query
        </label>
        <input
          id="gtr-search-q"
          className={styles.input}
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder={
            mode === "vin"
              ? "17-character VIN"
              : mode === "model"
                ? "e.g. Navara D40"
                : mode === "pnc"
                  ? "PNC code"
                  : "OEM part number"
          }
          autoComplete="off"
        />
        <button type="submit" className={styles.submit}>
          Search
        </button>
      </div>
      <p className={styles.hint}>
        Index-backed results land in Phase 7 — UI path is live now.
      </p>
    </form>
  );
}
