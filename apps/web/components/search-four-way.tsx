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

export function SearchFourWay({
  variant = "panel",
}: {
  /** panel = page body; header = dense AutoDoc-style chrome search */
  variant?: "panel" | "header" | "compact";
}) {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("part");
  const [q, setQ] = useState("");
  const isHeader = variant === "header";
  const idPrefix = isHeader ? "gtr-hdr" : "gtr-search";

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const query = q.trim();
    if (!query) return;
    // Phase 7 replaces stub with Meilisearch / API
    router.push(`/search?mode=${mode}&q=${encodeURIComponent(query)}`);
  }

  return (
    <form
      className={
        isHeader
          ? styles.header
          : variant === "compact"
            ? styles.compact
            : styles.panel
      }
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
        <label className={styles.srOnly} htmlFor={`${idPrefix}-q`}>
          Search query
        </label>
        <input
          id={`${idPrefix}-q`}
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
                  : "OEM part number or keyword"
          }
          autoComplete="off"
        />
        <button type="submit" className={styles.submit}>
          Search
        </button>
      </div>
      {!isHeader ? (
        <p className={styles.hint}>
          Index-backed results land in Phase 7 — UI path is live now.
        </p>
      ) : null}
    </form>
  );
}
