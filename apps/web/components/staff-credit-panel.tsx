"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  loadCustomerCredit,
  searchCustomers,
  setCustomerCredit,
  type CustomerCreditRow,
  type CustomerCreditSnapshot,
  type CustomerOption,
} from "@/lib/staff-credit";
import { setCustomerMarketingOptIn } from "@/lib/staff-hr";
import { requireSession } from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready" };

export function StaffCreditPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [query, setQuery] = useState("");
  const [hits, setHits] = useState<CustomerOption[]>([]);
  const [selected, setSelected] = useState<CustomerCreditRow | null>(null);
  const [snapshot, setSnapshot] = useState<CustomerCreditSnapshot | null>(null);
  const [limitInput, setLimitInput] = useState("");
  const [hold, setHold] = useState(false);
  const [marketingOptIn, setMarketingOptIn] = useState(false);

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
    setBoot({ kind: "ready" });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (boot.kind !== "ready") return;
    const q = query.trim();
    if (q.length < 2) {
      setHits([]);
      return;
    }
    const t = window.setTimeout(() => {
      void (async () => {
        const client = createWebClient();
        if (!client) return;
        const res = await searchCustomers(client, q);
        if (!res.ok) {
          setMessage(res.error);
          setHits([]);
          return;
        }
        setHits(res.data);
      })();
    }, 250);
    return () => window.clearTimeout(t);
  }, [boot.kind, query]);

  async function selectCustomer(hit: CustomerOption) {
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setBusy(false);
      return;
    }
    const loaded = await loadCustomerCredit(client, hit.id);
    setBusy(false);
    if (!loaded.ok) {
      setMessage(loaded.error);
      return;
    }
    if (!loaded.data) {
      setMessage("Customer not found.");
      return;
    }
    setSelected(loaded.data);
    setSnapshot(null);
    setLimitInput(String(loaded.data.credit_limit));
    setHold(loaded.data.credit_hold);
    setMarketingOptIn(loaded.data.marketing_opt_in);
    setQuery(hit.display_name);
    setHits([]);
  }

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (!selected) return;
    const client = createWebClient();
    if (!client) return;
    const limit = Number(limitInput);
    if (!Number.isFinite(limit) || limit < 0) {
      setMessage("Credit limit must be a number â‰¥ 0.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await setCustomerCredit(client, {
      customerId: selected.id,
      creditLimit: limit,
      creditHold: hold,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setSnapshot(res.data);
    setSelected({
      ...selected,
      id: res.data.customer_id,
      credit_limit: Number(res.data.credit_limit),
      credit_hold: res.data.credit_hold,
      open_balance: Number(res.data.open_balance),
      currency: res.data.currency,
    });
    setMessage(
      `Saved Â· limit ${Number(res.data.credit_limit).toFixed(2)} ${res.data.currency} Â· hold ${res.data.credit_hold ? "ON" : "off"} Â· open ${Number(res.data.open_balance).toFixed(2)} ${res.data.currency}`,
    );
  }

  async function onClearHold() {
    if (!selected) return;
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await setCustomerCredit(client, {
      customerId: selected.id,
      creditHold: false,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setHold(false);
    setSnapshot(res.data);
    setMessage(`Credit hold cleared Â· open ${Number(res.data.open_balance).toFixed(2)} ${res.data.currency}`);
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading credit deskâ€¦</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login?next=/staff/crm/credit">Sign in</Link> with
        sales/finance/admin to set B2B credit.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {boot.message}
      </p>
    );
  }

  const display = snapshot
    ? {
        limit: Number(snapshot.credit_limit),
        hold: snapshot.credit_hold,
        open: Number(snapshot.open_balance),
        currency: snapshot.currency,
      }
    : selected
      ? {
          limit: selected.credit_limit,
          hold: selected.credit_hold,
          open: selected.open_balance,
          currency: selected.currency ?? "USD",
        }
      : null;

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Find customer</legend>
        <label className={styles.field}>
          Name or customer id
          <input
            value={query}
            onChange={(e) => {
              setQuery(e.target.value);
              setSelected(null);
              setSnapshot(null);
            }}
            disabled={busy}
            placeholder="Search display nameâ€¦"
            autoComplete="off"
          />
        </label>
        {hits.length > 0 && !selected ? (
          <ul className={styles.list}>
            {hits.map((h) => (
              <li key={h.id}>
                <button
                  type="button"
                  className={styles.btnGhost}
                  onClick={() => void selectCustomer(h)}
                >
                  {h.display_name}
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </fieldset>

      {selected && display ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>
            Credit Â· {selected.display_name}
          </legend>
          <p className={styles.muted}>
            Open balance{" "}
            <strong>
              {display.open.toFixed(2)} {display.currency}
            </strong>
            {" Â· "}
            hold {display.hold ? "ON" : "off"}
            {" Â· "}
            current limit {display.limit.toFixed(2)} {display.currency}
          </p>
          <form onSubmit={(e) => void onSave(e)}>
            <div className={styles.formGrid}>
              <label className={styles.field}>
                Credit limit ({display.currency})
                <input
                  value={limitInput}
                  onChange={(e) => setLimitInput(e.target.value)}
                  disabled={busy}
                  inputMode="decimal"
                  required
                />
              </label>
              <label className={styles.field}>
                Credit hold
                <select
                  value={hold ? "yes" : "no"}
                  onChange={(e) => setHold(e.target.value === "yes")}
                  disabled={busy}
                >
                  <option value="no">Clear / off</option>
                  <option value="yes">On hold</option>
                </select>
              </label>
              <label className={styles.field}>
                Marketing opt-in
                <select
                  value={marketingOptIn ? "yes" : "no"}
                  onChange={(e) => setMarketingOptIn(e.target.value === "yes")}
                  disabled={busy}
                >
                  <option value="no">Opted out</option>
                  <option value="yes">Opted in</option>
                </select>
              </label>
            </div>
            <p className={styles.muted}>
              Promo cooldown stamp is worker-only
              {selected.last_promotional_message_at
                ? ` Â· last promo ${new Date(selected.last_promotional_message_at).toLocaleString()}`
                : ""}
              .
            </p>
            <div className={styles.formActions}>
              <button type="submit" className={styles.btn} disabled={busy}>
                Save via set_customer_credit
              </button>
              <button
                type="button"
                className={styles.btnGhost}
                disabled={busy}
                onClick={() => {
                  void (async () => {
                    if (!selected) return;
                    const client = createWebClient();
                    if (!client) return;
                    setBusy(true);
                    const res = await setCustomerMarketingOptIn(client, {
                      customerId: selected.id,
                      optIn: marketingOptIn,
                    });
                    setBusy(false);
                    if (!res.ok) {
                      setMessage(res.error);
                      return;
                    }
                    setSelected({
                      ...selected,
                      marketing_opt_in: marketingOptIn,
                    });
                    setMessage(
                      marketingOptIn
                        ? "Marketing opt-in enabled (cooldown still applies)."
                        : "Marketing opt-in cleared.",
                    );
                  })();
                }}
              >
                Save marketing opt-in
              </button>
              {hold ? (
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void onClearHold()}
                >
                  Clear hold only
                </button>
              ) : null}
            </div>
          </form>
        </fieldset>
      ) : (
        <p className={styles.muted}>
          Select a customer to view open balance (explicit currency) and set
          limit / hold. Storefront cannot self-set credit.
        </p>
      )}

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
