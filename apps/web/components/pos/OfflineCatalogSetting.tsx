"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import type { OfflineCatalog } from "@/lib/pos/offline/catalog";
import { opfsSupported } from "@/lib/pos/offline/storage";
import styles from "./pos.module.css";

function gb(bytes: number): string {
  return bytes >= 1e9 ? `${(bytes / 1e9).toFixed(1)} GB` : `${Math.max(1, Math.round(bytes / 1e6))} MB`;
}

function when(iso: string | null): string {
  if (!iso) return "never";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

/**
 * Settings row for the downloadable full catalogue: download (≈5 GB, resumable), progress, pause,
 * refresh stock and prices, remove. With it, search, the vehicle cascade and EPC diagrams work with
 * no connection.
 */
export function OfflineCatalogSetting({ catalog }: { catalog: OfflineCatalog }) {
  const status = useSyncExternalStore(
    (cb) => catalog.subscribe(cb),
    () => JSON.stringify(catalog.status()),
    () => JSON.stringify({ state: "none" }),
  );
  const s = JSON.parse(status) as ReturnType<OfflineCatalog["status"]>;
  const [error, setError] = useState<string | null>(null);
  const [free, setFree] = useState<number | null>(null);
  const [supported, setSupported] = useState(true);

  useEffect(() => {
    setSupported(opfsSupported());
    void catalog.init();
    void navigator.storage?.estimate?.().then((e) => setFree(e.quota != null && e.usage != null ? e.quota - e.usage : null));
  }, [catalog, status]);

  const run = (action: () => Promise<void>) => {
    setError(null);
    action().catch((e: unknown) => setError(e instanceof Error ? e.message : "Something went wrong."));
  };

  let detail: string;
  switch (s.state) {
    case "none":
      detail = "Download the whole parts catalogue (about 5 GB, every vehicle, diagram and image) so search and EPC work with no connection.";
      break;
    case "downloading":
      detail = `Downloading ${s.release}: ${gb(s.doneBytes)} of ${gb(s.totalBytes)} (${Math.floor((s.doneBytes / Math.max(1, s.totalBytes)) * 100)}%). You can keep selling.`;
      break;
    case "paused":
      detail = `${s.message} ${gb(s.doneBytes)} of ${gb(s.totalBytes)} downloaded.`;
      break;
    case "ready":
      detail = `Ready: release ${s.release} (${gb(s.totalBytes)}), downloaded ${when(s.downloadedAt)}. Stock and prices for ${s.stockCount} parts, updated ${when(s.stockPulledAt)}.`;
      break;
  }

  return (
    <div className={styles.listRow}>
      <span>
        <div className={styles.listTitle}>Offline catalogue</div>
        <div className={styles.muted}>
          {supported ? detail : "This browser cannot store the offline catalogue. Use a current Chrome or Edge."}
          {supported && s.state !== "ready" && free != null ? ` Space this browser can use: ${gb(free)}.` : ""}
        </div>
        {s.state === "downloading" ? (
          <progress className={styles.offlineProgress} max={s.totalBytes} value={s.doneBytes} aria-label="Offline catalogue download" />
        ) : null}
        {error ? (
          <div className={`${styles.statusBanner} ${styles.statusError}`} role="alert">
            {error}
          </div>
        ) : null}
      </span>
      <div className={styles.segment} role="group" aria-label="Offline catalogue">
        {s.state === "downloading" ? (
          <button type="button" className={styles.segmentItem} onClick={() => catalog.pause()}>
            Pause
          </button>
        ) : (
          <button type="button" className={styles.segmentItem} disabled={!supported} onClick={() => run(() => catalog.download())}>
            {s.state === "none" ? "Download" : s.state === "paused" ? "Continue" : "Update"}
          </button>
        )}
        {s.state === "ready" ? (
          <button type="button" className={styles.segmentItem} onClick={() => run(() => catalog.refreshStock())}>
            Refresh prices
          </button>
        ) : null}
        {s.state !== "none" && s.state !== "downloading" ? (
          <button
            type="button"
            className={styles.segmentItem}
            onClick={() => {
              if (window.confirm("Remove the offline catalogue from this device?")) run(() => catalog.remove());
            }}
          >
            Remove
          </button>
        ) : null}
      </div>
    </div>
  );
}
