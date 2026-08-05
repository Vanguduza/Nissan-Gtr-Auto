"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import styles from "@/components/account.module.css";
import {
  fetchStoresInsights,
  listActiveWarehouses,
  type StoresInsightsResult,
} from "@/lib/staff-stores-insights";
import { requireSession } from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready" };

export function StaffStoresInsightsPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [warehouses, setWarehouses] = useState<
    { id: string; code: string; name: string }[]
  >([]);
  const [warehouseId, setWarehouseId] = useState("");
  const [includeDirectives, setIncludeDirectives] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [result, setResult] = useState<StoresInsightsResult | null>(null);

  const bootSession = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }
    const wh = await listActiveWarehouses(client);
    if (!wh.ok) {
      setBoot({ kind: "error", message: wh.error });
      return;
    }
    setWarehouses(wh.data);
    if (wh.data[0]) setWarehouseId(wh.data[0].id);
    setBoot({ kind: "ready" });
  }, []);

  useEffect(() => {
    void bootSession();
  }, [bootSession]);

  async function onLoad(e?: FormEvent) {
    e?.preventDefault();
    const client = createWebClient();
    if (!client || !warehouseId) return;

    setBusy(true);
    setMessage(null);
    const res = await fetchStoresInsights(client, {
      warehouseId,
      includeDirectives,
    });
    setBusy(false);

    if (!res.ok) {
      setResult(null);
      setMessage(res.error);
      return;
    }

    setResult(res.data);
    if (res.data.numericOnly && includeDirectives) {
      setMessage(
        res.data.error === "gemini_unavailable"
          ? "Directives unavailable (Gemini not configured). Showing ABC / forecast KPIs only."
          : res.data.error
            ? `Directives unavailable: ${res.data.error}`
            : "Numeric KPIs only — directives not returned.",
      );
    } else {
      setMessage(null);
    }
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading warehouse insights…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with warehouse, finance, or admin
        staff.
      </p>
    );
  }
  if (boot.kind === "error") {
    return <p className={styles.lede}>{boot.message}</p>;
  }

  const kpis = result?.kpis;
  const classCounts = Array.isArray(kpis?.abc_class_counts)
    ? (kpis?.abc_class_counts as { abc_class?: string; sku_count?: number }[])
    : [];
  const directives = result?.directives ?? [];

  return (
    <div className={styles.form}>
      <p className={styles.muted}>
        ABC + open forecast suggestions. Directives are advisory — never
        auto-create POs.
      </p>

      <form onSubmit={onLoad}>
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Warehouse · run</legend>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Warehouse
              <select
                value={warehouseId}
                onChange={(e) => setWarehouseId(e.target.value)}
                disabled={busy || warehouses.length === 0}
              >
                {warehouses.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.code} — {w.name}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.checkField}>
              <input
                type="checkbox"
                checked={includeDirectives}
                onChange={(e) => setIncludeDirectives(e.target.checked)}
                disabled={busy}
              />
              Include AI directives
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy}>
              {busy ? "Running…" : "Run insights"}
            </button>
            {message ? <p className={styles.formStatus}>{message}</p> : null}
          </div>
        </fieldset>
      </form>

      {result?.numericOnly && includeDirectives ? (
        <p className={styles.banner} role="status">
          Numeric only — AI directives unavailable
          {result.error ? ` (${result.error})` : ""}.
        </p>
      ) : null}

      {classCounts.length > 0 ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>ABC tiers</legend>
          <div className={styles.kpiGrid}>
            {classCounts.map((row) => (
              <div key={String(row.abc_class)} className={styles.kpiTile}>
                <span className={styles.kpiLabel}>
                  Tier {String(row.abc_class ?? "?")}
                </span>
                <span className={styles.kpiValue}>
                  {String(row.sku_count ?? 0)} SKUs
                </span>
              </div>
            ))}
          </div>
        </fieldset>
      ) : null}

      {result?.narrative ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>
            Narrative{result.gemini_used ? " · Gemini" : ""}
          </legend>
          <p className={styles.narrative}>{result.narrative}</p>
        </fieldset>
      ) : null}

      {directives.length > 0 ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Directives</legend>
          <ul className={styles.list}>
            {directives.map((d, i) => (
              <li key={`${d.action}-${d.oem ?? i}`}>
                <strong>{d.action}</strong>
                {d.oem ? ` · ${d.oem}` : ""}
                {d.suggested_qty != null ? ` · qty ${d.suggested_qty}` : ""}
                {" — "}
                {d.rationale}
              </li>
            ))}
          </ul>
        </fieldset>
      ) : null}
    </div>
  );
}
