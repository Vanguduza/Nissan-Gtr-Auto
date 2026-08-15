"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import styles from "@/components/account.module.css";
import {
  downloadBrandedPayslipPdf,
  fundPayrollLines,
  fundPayrollRun,
  listEmployees,
  listHrPayslipSchedules,
  listOpenPayrollLines,
  listPayrollDeductions,
  listSubmittedPayrollRuns,
  requireSession,
  upsertHrPayslipSchedule,
  type EmployeeOption,
  type HrPayslipScheduleRow,
  type PayrollLineOption,
} from "@/lib/staff-hr";
import { createWebClient } from "@/lib/supabase";
import type { Database } from "@gtr/supabase-client";

type PayFrequency = Database["public"]["Enums"]["hr_pay_frequency"];
type Currency = Database["public"]["Enums"]["currency_code"];

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      employees: EmployeeOption[];
      lines: PayrollLineOption[];
      runs: Array<{
        id: string;
        document_number: string | null;
        period_start: string;
        period_end: string;
        currency: Currency;
        total_net: number;
        status: string;
      }>;
      schedules: HrPayslipScheduleRow[];
    };

function toDateInputValue(iso: string | null | undefined): string {
  if (!iso) return "";
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso.slice(0, 10);
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function StaffHrPayrollPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [selectedLineIds, setSelectedLineIds] = useState<Set<string>>(
    () => new Set(),
  );
  const [runId, setRunId] = useState("");
  const [cashAccount, setCashAccount] = useState("1100");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [freq, setFreq] = useState<PayFrequency>("monthly");
  const [cronExpr, setCronExpr] = useState("0 6 25 * *");
  const [nextRun, setNextRun] = useState("");
  const [schedActive, setSchedActive] = useState(true);
  const [schedCash, setSchedCash] = useState("1100");
  const [schedCurrency, setSchedCurrency] = useState<Currency>("USD");
  const [schedRate, setSchedRate] = useState("1");
  const [autoFund, setAutoFund] = useState(true);

  const empById = useMemo(() => {
    const m = new Map<string, EmployeeOption>();
    if (boot.kind === "ready") {
      for (const e of boot.employees) m.set(e.id, e);
    }
    return m;
  }, [boot]);

  const refresh = useCallback(async () => {
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

    const [emps, lines, runs, schedules] = await Promise.all([
      listEmployees(client),
      listOpenPayrollLines(client),
      listSubmittedPayrollRuns(client),
      listHrPayslipSchedules(client),
    ]);
    if (!emps.ok) {
      setBoot({ kind: "error", message: emps.error });
      return;
    }
    if (!lines.ok) {
      setBoot({ kind: "error", message: lines.error });
      return;
    }
    if (!runs.ok) {
      setBoot({ kind: "error", message: runs.error });
      return;
    }
    if (!schedules.ok) {
      setBoot({ kind: "error", message: schedules.error });
      return;
    }

    setBoot({
      kind: "ready",
      employees: emps.data,
      lines: lines.data,
      runs: runs.data,
      schedules: schedules.data,
    });
    if (!runId && runs.data[0]) setRunId(runs.data[0].id);
    const monthly = schedules.data.find((s) => s.pay_frequency === "monthly");
    if (monthly) {
      setFreq(monthly.pay_frequency);
      setCronExpr(monthly.cron_expr);
      setNextRun(toDateInputValue(monthly.next_run_at));
      setSchedActive(monthly.is_active);
      setSchedCash(monthly.cash_account_code || "1100");
      setSchedCurrency(monthly.currency);
      setSchedRate(String(monthly.default_exchange_rate ?? 1));
      setAutoFund(monthly.auto_fund);
    }
  }, [runId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  function toggleLine(id: string) {
    setSelectedLineIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  async function downloadPdfsForLines(lineIds: string[]) {
    const client = createWebClient();
    if (!client || boot.kind !== "ready") return;
    const session = await client.auth.getSession();
    const token = session.data.session?.access_token;
    if (!token) {
      setMessage("Sign in required for payslip PDFs.");
      return;
    }
    let okCount = 0;
    for (const lineId of lineIds) {
      const line = boot.lines.find((l) => l.id === lineId);
      const emp = line ? empById.get(line.employee_id) : null;
      if (!line || !emp) continue;
      const deductions = await listPayrollDeductions(client, lineId);
      if (!deductions.ok) continue;
      const run = boot.runs.find((r) => r.id === line.payroll_run_id);
      const pdf = await downloadBrandedPayslipPdf(token, {
        storeName: "Nissan GTR Auto",
        employeeName: emp.full_name,
        employeeCode: emp.employee_code,
        periodStart: run?.period_start ?? "",
        periodEnd: run?.period_end ?? "",
        currency: line.currency,
        grossPay: Number(line.gross_amount),
        manualDeductions: deductions.data.map((d) => ({
          label: d.label,
          amount: Number(d.amount),
        })),
        netPay: Number(line.net_amount),
      });
      if (pdf.ok) okCount += 1;
    }
    setMessage(
      `${okCount} branded PDF(s) downloaded (gross − manual only; no fiscal QR)`,
    );
  }

  async function onFundSelected() {
    const client = createWebClient();
    if (!client) return;
    const ids = [...selectedLineIds];
    if (ids.length === 0) {
      setMessage("Select one or more payroll lines.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await fundPayrollLines(client, {
      payrollLineIds: ids,
      cashAccountCode: cashAccount || "1100",
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(
      `Funded ${res.data.funded_count} · skipped ${res.data.skipped_count} · net ${res.data.total_net} ${res.data.currency} from ${res.data.cash_account_code} · Dr 5200/Cr 2150 then Dr 2150/Cr cash`,
    );
    await refresh();
    await downloadPdfsForLines(ids);
  }

  async function onFundRun() {
    const client = createWebClient();
    if (!client || !runId) return;
    const lineIds =
      boot.kind === "ready"
        ? boot.lines.filter((l) => l.payroll_run_id === runId).map((l) => l.id)
        : [];
    setBusy(true);
    setMessage(null);
    const res = await fundPayrollRun(client, {
      payrollRunId: runId,
      cashAccountCode: cashAccount || "1100",
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(
      `Run funded · ${res.data.funded_count} line(s) · net ${res.data.total_net} ${res.data.currency} · payment JE ${res.data.payment_journal_id?.slice(0, 8) ?? "—"}…`,
    );
    await refresh();
    if (lineIds.length) await downloadPdfsForLines(lineIds);
  }

  async function onSaveSchedule(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const rate = Number(schedRate);
    const res = await upsertHrPayslipSchedule(client, {
      payFrequency: freq,
      cronExpr,
      nextRunAt: nextRun
        ? new Date(`${nextRun}T06:00:00.000Z`).toISOString()
        : null,
      isActive: schedActive,
      cashAccountCode: schedCash || "1100",
      currency: schedCurrency,
      defaultExchangeRate: Number.isFinite(rate) && rate > 0 ? rate : 1,
      autoFund,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Schedule saved · ${freq} · id ${res.data.slice(0, 8)}…`);
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading payroll…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> as HR/admin to fund payslips.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {boot.message}{" "}
        <button type="button" className={styles.btnGhost} onClick={() => void refresh()}>
          Retry
        </button>
      </p>
    );
  }

  const unfunded = boot.lines.filter((l) => !l.payment_journal_id);

  return (
    <div className={styles.form}>
      <p className={styles.muted}>
        Gross payroll only (no PAYE/NSSA). Funding posts append-only journals:
        Dr <code>5200</code> / Cr <code>2150</code>, then Dr <code>2150</code> / Cr
        cash (<code>1100</code> default). Not a ContiPay bank push.
      </p>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>On-demand fund + PDF</legend>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Cash / bank CoA
            <input
              value={cashAccount}
              onChange={(e) => setCashAccount(e.target.value)}
              disabled={busy}
              placeholder="1100"
            />
          </label>
          <label className={styles.field}>
            Submitted run (whole run)
            <select
              value={runId}
              onChange={(e) => setRunId(e.target.value)}
              disabled={busy || boot.runs.length === 0}
            >
              {boot.runs.length === 0 ? (
                <option value="">No submitted runs</option>
              ) : null}
              {boot.runs.map((r) => (
                <option key={r.id} value={r.id}>
                  {r.document_number || r.id.slice(0, 8)} · {r.period_start}→
                  {r.period_end} · net {r.total_net} {r.currency}
                </option>
              ))}
            </select>
          </label>
        </div>
        <div className={styles.formActions} style={{ marginTop: "0.85rem" }}>
          <button
            type="button"
            className={styles.btn}
            disabled={busy || !runId}
            onClick={() => void onFundRun()}
          >
            Fund entire run
          </button>
          <button
            type="button"
            className={styles.btnGhost}
            disabled={busy || selectedLineIds.size === 0}
            onClick={() => void onFundSelected()}
          >
            Fund selected ({selectedLineIds.size})
          </button>
        </div>

        <div style={{ marginTop: "1rem", overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead>
              <tr>
                <th style={{ textAlign: "left", padding: "0.35rem" }} />
                <th style={{ textAlign: "left", padding: "0.35rem" }}>Employee</th>
                <th style={{ textAlign: "right", padding: "0.35rem" }}>Gross</th>
                <th style={{ textAlign: "right", padding: "0.35rem" }}>Net</th>
                <th style={{ textAlign: "left", padding: "0.35rem" }}>Status</th>
              </tr>
            </thead>
            <tbody>
              {unfunded.length === 0 ? (
                <tr>
                  <td colSpan={5} className={styles.muted} style={{ padding: "0.5rem" }}>
                    No unfunded payroll lines visible.
                  </td>
                </tr>
              ) : (
                unfunded.map((l) => {
                  const emp = empById.get(l.employee_id);
                  return (
                    <tr key={l.id}>
                      <td style={{ padding: "0.35rem" }}>
                        <input
                          type="checkbox"
                          checked={selectedLineIds.has(l.id)}
                          onChange={() => toggleLine(l.id)}
                          disabled={busy}
                          aria-label={`Select ${emp?.employee_code ?? l.id}`}
                        />
                      </td>
                      <td style={{ padding: "0.35rem" }}>
                        {emp
                          ? `${emp.employee_code} · ${emp.full_name}`
                          : l.employee_id.slice(0, 8)}
                      </td>
                      <td style={{ padding: "0.35rem", textAlign: "right" }}>
                        {Number(l.gross_amount).toFixed(2)} {l.currency}
                      </td>
                      <td style={{ padding: "0.35rem", textAlign: "right" }}>
                        {Number(l.net_amount).toFixed(2)} {l.currency}
                      </td>
                      <td style={{ padding: "0.35rem" }}>Unfunded</td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Payslip schedule</legend>
        <p className={styles.muted}>
          Ops cron calls Edge <code>process-payroll-schedules</code> (worker
          secret). Cadence registry: <code>ai_worker_schedules.process_payroll_schedules</code>.
        </p>
        <form onSubmit={(e) => void onSaveSchedule(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Frequency
              <select
                value={freq}
                onChange={(e) => {
                  const f = e.target.value as PayFrequency;
                  setFreq(f);
                  const existing = boot.schedules.find((s) => s.pay_frequency === f);
                  if (existing) {
                    setCronExpr(existing.cron_expr);
                    setNextRun(toDateInputValue(existing.next_run_at));
                    setSchedActive(existing.is_active);
                    setSchedCash(existing.cash_account_code || "1100");
                    setSchedCurrency(existing.currency);
                    setSchedRate(String(existing.default_exchange_rate ?? 1));
                    setAutoFund(existing.auto_fund);
                  }
                }}
                disabled={busy}
              >
                <option value="weekly">Weekly</option>
                <option value="fortnightly">Fortnightly</option>
                <option value="monthly">Monthly</option>
              </select>
            </label>
            <label className={styles.field}>
              Cron expr
              <input
                value={cronExpr}
                onChange={(e) => setCronExpr(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              Next run date
              <input
                type="date"
                value={nextRun}
                onChange={(e) => setNextRun(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              Cash CoA
              <input
                value={schedCash}
                onChange={(e) => setSchedCash(e.target.value)}
                disabled={busy}
              />
            </label>
            <label className={styles.field}>
              Currency
              <select
                value={schedCurrency}
                onChange={(e) => setSchedCurrency(e.target.value as Currency)}
                disabled={busy}
              >
                <option value="USD">USD</option>
                <option value="ZIG">ZIG</option>
              </select>
            </label>
            <label className={styles.field}>
              Exchange rate
              <input
                value={schedRate}
                onChange={(e) => setSchedRate(e.target.value)}
                disabled={busy}
                inputMode="decimal"
              />
            </label>
            <label className={styles.field}>
              <span style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
                <input
                  type="checkbox"
                  checked={schedActive}
                  onChange={(e) => setSchedActive(e.target.checked)}
                  disabled={busy}
                />
                Active
              </span>
            </label>
            <label className={styles.field}>
              <span style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
                <input
                  type="checkbox"
                  checked={autoFund}
                  onChange={(e) => setAutoFund(e.target.checked)}
                  disabled={busy}
                />
                Auto-fund from cash on schedule
              </span>
            </label>
          </div>
          <div className={styles.formActions} style={{ marginTop: "0.85rem" }}>
            <button type="submit" className={styles.btn} disabled={busy}>
              Save schedule
            </button>
          </div>
        </form>
        {boot.schedules.length > 0 ? (
          <ul className={styles.muted} style={{ marginTop: "0.75rem" }}>
            {boot.schedules.map((s) => (
              <li key={s.id}>
                {s.pay_frequency}: next {s.next_run_at?.slice(0, 10) ?? "—"} · cash{" "}
                {s.cash_account_code || "1100"} ·{" "}
                {s.is_active ? "active" : "paused"}
                {s.auto_fund ? " · auto-fund" : ""}
              </li>
            ))}
          </ul>
        ) : null}
      </fieldset>

      {message ? (
        <p className={styles.lede} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
