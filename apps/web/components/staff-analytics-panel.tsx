"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  dateInputToPeriodBounds,
  fetchAnalyticsInsights,
  formatMoney,
  monthStartInput,
  requireSession,
  todayInput,
  type AnalyticsInsightsResult,
  type OpsSalesKpis,
} from "@/lib/staff-analytics";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready" };

function num(v: unknown): number | undefined {
  if (typeof v === "number" && Number.isFinite(v)) return v;
  if (typeof v === "string" && v.trim() !== "") {
    const n = Number(v);
    return Number.isFinite(n) ? n : undefined;
  }
  return undefined;
}

function KpiTile({ label, value }: { label: string; value: string }) {
  return (
    <div className={styles.kpiTile}>
      <span className={styles.kpiLabel}>{label}</span>
      <span className={styles.kpiValue}>{value}</span>
    </div>
  );
}

function SalesTiles({ kpis }: { kpis: OpsSalesKpis }) {
  const sales = kpis.sales;
  const byCur = sales?.by_currency ?? [];
  const aov = sales?.average_order_value ?? [];
  return (
    <>
      <KpiTile
        label="Orders"
        value={String(num(sales?.order_count) ?? 0)}
      />
      {byCur.map((row) => (
        <KpiTile
          key={`rev-${row.currency}`}
          label={`Revenue · ${row.currency}`}
          value={formatMoney(num(row.revenue), row.currency)}
        />
      ))}
      {aov.map((row) => (
        <KpiTile
          key={`aov-${row.currency}`}
          label={`AOV · ${row.currency}`}
          value={formatMoney(num(row.average_order_value), row.currency)}
        />
      ))}
      <KpiTile
        label="Credit notes"
        value={String(num(kpis.returns?.credit_note_count) ?? 0)}
      />
      <KpiTile
        label="On hand"
        value={String(num(kpis.inventory?.on_hand_qty) ?? 0)}
      />
      <KpiTile
        label="Low stock"
        value={String(num(kpis.inventory?.low_stock_count) ?? 0)}
      />
      <KpiTile
        label="Stockouts"
        value={String(num(kpis.inventory?.stockout_count) ?? 0)}
      />
      <KpiTile
        label="Quarantine qty"
        value={String(num(kpis.inventory?.quarantine_qty) ?? 0)}
      />
      <KpiTile
        label="AR customers"
        value={String(num(kpis.ar_aging?.customers_with_open_balance) ?? 0)}
      />
      <KpiTile
        label="Credit holds"
        value={String(num(kpis.credit_holds?.customers_on_credit_hold) ?? 0)}
      />
      <KpiTile
        label="Open DNs"
        value={String(num(kpis.open_deliveries?.open_delivery_notes_draft) ?? 0)}
      />
      <KpiTile
        label="Open jobs"
        value={String(num(kpis.open_deliveries?.open_delivery_jobs) ?? 0)}
      />
    </>
  );
}

export function StaffAnalyticsPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [from, setFrom] = useState(monthStartInput);
  const [to, setTo] = useState(todayInput);
  const [includeNarrative, setIncludeNarrative] = useState(true);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [result, setResult] = useState<AnalyticsInsightsResult | null>(null);

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
    setBoot({ kind: "ready" });
  }, []);

  useEffect(() => {
    void bootSession();
  }, [bootSession]);

  async function onLoad(e?: FormEvent) {
    e?.preventDefault();
    const client = createWebClient();
    if (!client) return;

    const bounds = dateInputToPeriodBounds(from, to);
    if (!bounds.ok) {
      setMessage(bounds.error);
      return;
    }

    setBusy(true);
    setMessage(null);
    const res = await fetchAnalyticsInsights(client, {
      from: bounds.data.from,
      to: bounds.data.to,
      includeNarrative,
    });
    setBusy(false);

    if (!res.ok) {
      setResult(null);
      setMessage(res.error);
      return;
    }

    setResult(res.data);
    if (res.data.numericOnly && includeNarrative) {
      setMessage(
        res.data.error === "gemini_unavailable"
          ? "Narrative unavailable (Gemini not configured). Showing numeric KPIs only."
          : res.data.error
            ? `Narrative unavailable: ${res.data.error}. Showing numeric KPIs only.`
            : "Numeric KPIs only — narrative not returned.",
      );
    } else {
      setMessage(null);
    }
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading analytics…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with admin, finance, or sales staff.
      </p>
    );
  }
  if (boot.kind === "error") {
    return <p className={styles.lede}>{boot.message}</p>;
  }

  const topSkus = result?.kpis.top_skus?.items ?? [];

  return (
    <div className={styles.form}>
      <p className={styles.muted}>
        Aggregates only (no customer PII). Scheduled deliveries:{" "}
        <Link href="/staff/analytics/subscriptions">report subscriptions</Link>.
      </p>

      <form onSubmit={onLoad}>
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Period · ops_sales_v1</legend>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              From
              <input
                type="date"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
                disabled={busy}
                required
              />
            </label>
            <label className={styles.field}>
              To
              <input
                type="date"
                value={to}
                onChange={(e) => setTo(e.target.value)}
                disabled={busy}
                required
              />
            </label>
            <label className={styles.checkField}>
              <input
                type="checkbox"
                checked={includeNarrative}
                onChange={(e) => setIncludeNarrative(e.target.checked)}
                disabled={busy}
              />
              Include AI narrative
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy}>
              {busy ? "Loading…" : "Load insights"}
            </button>
            {message ? <p className={styles.formStatus}>{message}</p> : null}
          </div>
        </fieldset>
      </form>

      {result?.numericOnly && includeNarrative ? (
        <p className={styles.banner} role="status">
          Numeric only — AI narrative unavailable
          {result.error ? ` (${result.error})` : ""}. KPI figures below are
          still valid.
        </p>
      ) : null}

      {result ? (
        <>
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>KPIs</legend>
            <div className={styles.kpiGrid}>
              <SalesTiles kpis={result.kpis} />
            </div>
          </fieldset>

          {result.narrative ? (
            <fieldset className={styles.fieldset}>
              <legend className={styles.legend}>
                Narrative{result.gemini_used ? " · Gemini" : ""}
              </legend>
              <p className={styles.narrative}>{result.narrative}</p>
            </fieldset>
          ) : null}

          {topSkus.length > 0 ? (
            <fieldset className={styles.fieldset}>
              <legend className={styles.legend}>Top SKUs</legend>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>OEM</th>
                    <th>Qty</th>
                    <th>Revenue</th>
                  </tr>
                </thead>
                <tbody>
                  {topSkus.map((row, i) => (
                    <tr key={`${row.oem ?? "oem"}-${row.currency}-${i}`}>
                      <td>{row.oem || "—"}</td>
                      <td>{num(row.qty) ?? 0}</td>
                      <td>
                        {formatMoney(num(row.revenue), row.currency || "USD")}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </fieldset>
          ) : null}
        </>
      ) : (
        <p className={styles.muted}>Choose a range and load insights.</p>
      )}
    </div>
  );
}
