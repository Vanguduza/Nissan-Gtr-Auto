"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import styles from "@/components/account.module.css";
import {
  createPettyCashExpenseRequest,
  submitFinanceRequisition,
  uploadPettyCashReceipt,
  zigExchangeRate,
  type CurrencyCode,
} from "@/lib/staff-finance";
import { createWebClient } from "@/lib/supabase";

function todayInput(): string {
  return new Date().toISOString().slice(0, 10);
}

export function StaffPettyCashExpenseForm() {
  const router = useRouter();
  const [entryDate, setEntryDate] = useState(todayInput);
  const [description, setDescription] = useState("");
  const [amount, setAmount] = useState("");
  const [currency, setCurrency] = useState<CurrencyCode>("USD");
  const [rate, setRate] = useState(String(zigExchangeRate()));
  const [receipt, setReceipt] = useState<File | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      return;
    }
    const n = Number(amount);
    if (!Number.isFinite(n) || n <= 0) {
      setMessage("Amount must be a positive number.");
      return;
    }
    if (!description.trim()) {
      setMessage("Description is required.");
      return;
    }
    setBusy(true);
    setMessage(null);
    try {
      const created = await createPettyCashExpenseRequest(client, {
        amount: n,
        currency,
        description: description.trim(),
        entryDate,
        exchangeRate: currency === "ZIG" ? Number(rate) : 1,
      });
      if (!created.ok) {
        setMessage(created.error);
        return;
      }
      if (receipt) {
        const up = await uploadPettyCashReceipt(client, {
          requisitionId: created.data,
          file: receipt,
        });
        if (!up.ok) {
          setMessage(
            `Expense draft saved, but receipt upload failed: ${up.error}`,
          );
          return;
        }
      }
      const submitted = await submitFinanceRequisition(client, created.data);
      if (!submitted.ok) {
        setMessage(submitted.error);
        return;
      }
      router.push("/staff/finance?tab=petty-cash");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className={styles.form} onSubmit={(e) => void onSubmit(e)}>
      <p className={styles.muted}>
        <Link
          href="/staff/finance?tab=petty-cash"
          className={styles.navLink}
          style={{ display: "inline", padding: 0, minHeight: 0 }}
        >
          ← Petty cash
        </Link>
      </p>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Add expense</legend>
        <p className={styles.muted}>
          Creates a petty cash requisition. On final approval the ledger posts
          automatically (expense debit / petty cash credit — reduces cash in
          hand).
        </p>
        {message ? (
          <p className={styles.formStatus} role="status">
            {message}
          </p>
        ) : null}
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Date
            <input
              type="date"
              value={entryDate}
              onChange={(e) => setEntryDate(e.target.value)}
              disabled={busy}
              required
            />
          </label>
          <label className={styles.field}>
            Amount
            <input
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              disabled={busy}
              inputMode="decimal"
              required
            />
          </label>
          <label className={styles.field}>
            Currency
            <select
              value={currency}
              onChange={(e) => {
                const c = e.target.value as CurrencyCode;
                setCurrency(c);
                if (c === "ZIG") setRate(String(zigExchangeRate()));
              }}
              disabled={busy}
            >
              <option value="USD">USD</option>
              <option value="ZIG">ZIG</option>
            </select>
          </label>
          {currency === "ZIG" ? (
            <label className={styles.field}>
              ZiG rate
              <input
                value={rate}
                onChange={(e) => setRate(e.target.value)}
                disabled={busy}
                inputMode="decimal"
              />
            </label>
          ) : null}
          <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
            Description
            <input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              disabled={busy}
              placeholder="e.g. Office supplies — stationery"
              required
            />
          </label>
          <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
            Receipt image (optional)
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp,application/pdf"
              disabled={busy}
              onChange={(e) => setReceipt(e.target.files?.[0] ?? null)}
            />
          </label>
        </div>
        <div className={styles.formActions}>
          <button type="submit" className={styles.btn} disabled={busy}>
            Submit for approval
          </button>
        </div>
      </fieldset>
    </form>
  );
}
