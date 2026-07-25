"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import styles from "@/components/account.module.css";
import {
  addPayrollDeduction,
  attendanceHoursInPeriod,
  clockAttendance,
  listEmployees,
  listOpenPayrollLines,
  requireSession,
  resolveSelfEmployeeId,
  type EmployeeOption,
  type PayrollLineOption,
} from "@/lib/staff-hr";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      selfId: string | null;
      employees: EmployeeOption[];
      payrollLines: PayrollLineOption[];
    };

function startOfLocalDayIso(d = new Date()): string {
  const x = new Date(d);
  x.setHours(0, 0, 0, 0);
  return x.toISOString();
}

function endOfLocalDayIso(d = new Date()): string {
  const x = new Date(d);
  x.setHours(23, 59, 59, 999);
  return x.toISOString();
}

function toDateInputValue(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return "";
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}

export function StaffHrPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [employeeId, setEmployeeId] = useState("");
  const [periodStart, setPeriodStart] = useState(() =>
    toDateInputValue(startOfLocalDayIso()),
  );
  const [periodEnd, setPeriodEnd] = useState(() =>
    toDateInputValue(endOfLocalDayIso()),
  );
  const [hours, setHours] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [payrollLineId, setPayrollLineId] = useState("");
  const [deductionLabel, setDeductionLabel] = useState("");
  const [deductionAmount, setDeductionAmount] = useState("");

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

    const self = await resolveSelfEmployeeId(client);
    if (!self.ok) {
      setBoot({ kind: "error", message: self.error });
      return;
    }

    const emps = await listEmployees(client);
    if (!emps.ok) {
      setBoot({ kind: "error", message: emps.error });
      return;
    }

    const lines = await listOpenPayrollLines(client);
    if (!lines.ok) {
      setBoot({ kind: "error", message: lines.error });
      return;
    }

    setBoot({
      kind: "ready",
      selfId: self.data,
      employees: emps.data,
      payrollLines: lines.data,
    });
    setEmployeeId((prev) => {
      if (prev) return prev;
      if (self.data) return self.data;
      return emps.data.find((e) => e.status === "active")?.id ?? emps.data[0]?.id ?? "";
    });
    setPayrollLineId((prev) => prev || lines.data[0]?.id || "");
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const selected = useMemo(() => {
    if (boot.kind !== "ready") return null;
    return boot.employees.find((e) => e.id === employeeId) ?? null;
  }, [boot, employeeId]);

  async function runClock(eventType: "clock_in" | "clock_out") {
    const client = createWebClient();
    if (!client || !employeeId) return;
    setBusy(true);
    setMessage(null);
    const res = await clockAttendance(client, { employeeId, eventType });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(
      `${eventType === "clock_in" ? "Clocked in" : "Clocked out"} · event ${res.data.slice(0, 8)}…`,
    );
  }

  async function onLookupHours(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !employeeId) return;
    if (!periodStart || !periodEnd) {
      setMessage("Choose a start and end date for the hours lookup.");
      return;
    }
    const start = new Date(`${periodStart}T00:00:00`);
    const end = new Date(`${periodEnd}T23:59:59.999`);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
      setMessage("Invalid date range.");
      return;
    }
    if (end < start) {
      setMessage("Period end must be on or after period start.");
      return;
    }

    setBusy(true);
    setMessage(null);
    setHours(null);
    const res = await attendanceHoursInPeriod(client, {
      employeeId,
      periodStart: start.toISOString(),
      periodEnd: end.toISOString(),
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setHours(res.data);
  }

  async function onAddDeduction(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !payrollLineId) return;
    const n = Number(deductionAmount);
    if (!deductionLabel.trim()) {
      setMessage("Deduction label required.");
      return;
    }
    if (!Number.isFinite(n) || n <= 0) {
      setMessage("Deduction amount must be positive.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await addPayrollDeduction(client, {
      payrollLineId,
      label: deductionLabel.trim(),
      amount: n,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Deduction added · ${res.data.slice(0, 8)}…`);
    setDeductionAmount("");
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading HR…</p>;
  }

  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with a staff account linked to an
        employee record (or HR/admin) to clock attendance and look up hours.
      </p>
    );
  }

  if (boot.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {boot.message}{" "}
        <button
          type="button"
          className={styles.btnGhost}
          onClick={() => void refresh()}
        >
          Retry
        </button>
      </p>
    );
  }

  if (boot.employees.length === 0 && !boot.selfId) {
    return (
      <p className={styles.muted}>
        No employee records visible. Link your user to an{" "}
        <code>employees</code> row, or sign in as HR/admin.
      </p>
    );
  }

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Employee</legend>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Who
            <select
              value={employeeId}
              onChange={(e) => {
                setEmployeeId(e.target.value);
                setHours(null);
                setMessage(null);
              }}
              disabled={busy || boot.employees.length === 0}
            >
              {boot.employees.length === 0 && boot.selfId ? (
                <option value={boot.selfId}>Self ({boot.selfId.slice(0, 8)}…)</option>
              ) : null}
              {boot.employees.map((emp) => (
                <option key={emp.id} value={emp.id}>
                  {emp.employee_code} — {emp.full_name}
                  {emp.status !== "active" ? ` (${emp.status})` : ""}
                  {boot.selfId === emp.id ? " · you" : ""}
                </option>
              ))}
            </select>
          </label>
        </div>
        {selected ? (
          <p className={styles.muted} style={{ marginTop: "0.65rem" }}>
            {selected.full_name} · {selected.employee_code}
          </p>
        ) : null}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Clock</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Manual clock in/out via <code>clock_attendance</code>. No tax or
          payroll computation here.
        </p>
        <div className={styles.formActions}>
          <button
            type="button"
            className={styles.btn}
            disabled={busy || !employeeId}
            onClick={() => void runClock("clock_in")}
          >
            Clock in
          </button>
          <button
            type="button"
            className={styles.btnGhost}
            disabled={busy || !employeeId}
            onClick={() => void runClock("clock_out")}
          >
            Clock out
          </button>
        </div>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Hours in period</legend>
        <form onSubmit={(e) => void onLookupHours(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              From
              <input
                type="date"
                value={periodStart}
                onChange={(e) => setPeriodStart(e.target.value)}
                disabled={busy}
                required
              />
            </label>
            <label className={styles.field}>
              To
              <input
                type="date"
                value={periodEnd}
                onChange={(e) => setPeriodEnd(e.target.value)}
                disabled={busy}
                required
              />
            </label>
          </div>
          <div className={styles.formActions} style={{ marginTop: "0.85rem" }}>
            <button
              type="submit"
              className={styles.btn}
              disabled={busy || !employeeId}
            >
              Look up hours
            </button>
            {hours != null ? (
              <p className={styles.formStatus}>
                {hours.toFixed(2)} hours in range
              </p>
            ) : null}
          </div>
        </form>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Manual deduction</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Custom line via <code>add_payroll_deduction</code> only — no PAYE,
          NSSA, or statutory remittance UI. Amount currency matches the payroll
          line (<code>USD</code> | <code>ZIG</code>).
        </p>
        <form onSubmit={(e) => void onAddDeduction(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Payroll line
              <select
                value={payrollLineId}
                onChange={(e) => setPayrollLineId(e.target.value)}
                disabled={busy || boot.payrollLines.length === 0}
              >
                {boot.payrollLines.map((l) => (
                  <option key={l.id} value={l.id}>
                    {l.id.slice(0, 8)}… · gross {l.gross_amount} {l.currency}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              Label
              <input
                value={deductionLabel}
                onChange={(e) => setDeductionLabel(e.target.value)}
                disabled={busy}
                placeholder="e.g. Staff advance"
              />
            </label>
            <label className={styles.field}>
              Amount
              <input
                value={deductionAmount}
                onChange={(e) => setDeductionAmount(e.target.value)}
                disabled={busy}
                inputMode="decimal"
              />
            </label>
          </div>
          <div className={styles.formActions} style={{ marginTop: "0.85rem" }}>
            <button
              type="submit"
              className={styles.btnGhost}
              disabled={busy || !payrollLineId}
            >
              Add deduction
            </button>
          </div>
        </form>
      </fieldset>

      {message ? (
        <p className={styles.lede} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
