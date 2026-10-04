"use client";

import { ArrowLeft, CircleAlert, PackageCheck, RefreshCw, ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { formatMoney } from "@/lib/pos/money";
import type { ManagerProof, PaymentStatus, SplitSession } from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import styles from "./pos.module.css";
import { SplitRecoveryDetail, splitStatusLabel } from "./SplitPayment";
import { TerminalRecoveryList } from "./CardTerminalPanels";
import { PaymentLetters } from "./PaymentLetters";

const STATE_LABEL: Record<string, string> = {
  awaiting_payment: "Waiting for payment",
  payment_processing: "Payment in progress",
  payment_failed: "Payment failed",
  payment_expired: "Reservation expired",
  allocation_pending: "Paid, sale not finished",
  paid: "Paid",
  account_invoiced: "On account",
  cancelled: "Cancelled",
  delivered: "Collected",
};
const label = (state: string) => STATE_LABEL[state] ?? state.replace(/_/g, " ");

function when(iso: string | null): string {
  if (!iso) return "";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

/** Orders → Ready for pickup: paid or on-account counter orders the customer has not collected yet. */
export function PickupList({ pos }: { pos: PosStore }) {
  const [q, setQ] = useState("");
  const { refreshPickups } = pos;
  useEffect(() => {
    void refreshPickups(q);
  }, [refreshPickups, q]);
  const list = pos.pickups;
  return (
    <>
      <label className={styles.field}>
        <span className={styles.fieldLabel}>Find by invoice or customer</span>
        <input className={styles.input} value={q} onChange={(e) => setQ(e.target.value)} />
      </label>
      {list == null ? <div className={styles.emptyCard}>Loading…</div> : null}
      {list?.length === 0 ? <div className={styles.emptyCard}>Nothing waiting for collection.</div> : null}
      {list?.map((p) => (
        <div key={p.orderId} className={styles.listRow}>
          <span>
            <div className={styles.listTitle}>
              {p.documentNumber ?? "Order"} <span className={styles.badge}>{label(p.state)}</span>
            </div>
            <div className={styles.muted}>
              {p.customerName ?? "Walk-in"} · {formatMoney(p.total, p.currency)}
              {p.settledProvider ? ` · ${p.settledProvider.replace(/_/g, " ")}` : ""} · {when(p.updatedAt)}
            </div>
          </span>
          <button type="button" className={styles.primaryButton} disabled={pos.busy} onClick={() => void pos.collectOrder(p.orderId)}>
            <PackageCheck size={16} aria-hidden /> Handed over
          </button>
        </div>
      ))}
    </>
  );
}

/** Orders → Payments to resolve: checkouts whose money is not settled cleanly (`list_pos_payment_recovery`). */
export function ResolveList({ pos }: { pos: PosStore }) {
  const { refreshRecovery, refreshSplitRecovery } = pos;
  useEffect(() => {
    void refreshRecovery();
    void refreshSplitRecovery();
  }, [refreshRecovery, refreshSplitRecovery]);
  const list = pos.recoveryItems;
  const splits = pos.splitRecovery;
  // Part-paid sales appear once, with their parts and refunds (not again as a plain order).
  const splitOrders = new Set((splits ?? []).map((s) => s.orderId));
  return (
    <>
      {splits && splits.length > 0 ? <h3 className={styles.panelTitle} style={{ fontSize: 15 }}>Part payments</h3> : null}
      {splits?.map((s) => (
        <div key={s.sessionId} className={styles.listRow}>
          <span>
            <div className={styles.listTitle}>
              {s.documentNumber ?? "Sale"} · {formatMoney(s.total, s.currency)} <span className={styles.badge}>{splitStatusLabel(s.status)}</span>
            </div>
            <div className={styles.muted}>
              {s.customerName ?? "Walk-in"} · received {formatMoney(s.session.locked, s.currency)}
              {["open", "partially_captured", "leg_pending"].includes(s.status) ? ` · ${formatMoney(s.session.balanceDue, s.currency)} due` : ""}
              {s.session.refunds.some((r) => r.status !== "settled" && r.status !== "cancelled") ? " · refund open" : ""} · {when(s.updatedAt)}
            </div>
          </span>
          <button type="button" className={styles.primaryButton} onClick={() => pos.openRecovery(s.orderId)}>
            Open
          </button>
        </div>
      ))}
      {splits && splits.length > 0 && list && list.some((r) => !splitOrders.has(r.orderId)) ? (
        <h3 className={styles.panelTitle} style={{ fontSize: 15, marginTop: 12 }}>Single payments</h3>
      ) : null}
      {list == null ? <div className={styles.emptyCard}>Loading…</div> : null}
      <TerminalRecoveryList pos={pos} orderId={null} />
      {list?.length === 0 && !splits?.length && !pos.terminalRecovery?.length ? <div className={styles.emptyCard}>No payments to resolve.</div> : null}
      {list?.filter((r) => !splitOrders.has(r.orderId)).map((r) => (
        <div key={r.orderId} className={styles.listRow}>
          <span>
            <div className={styles.listTitle}>
              {formatMoney(r.total, r.currency)} <span className={styles.badge}>{label(r.state)}</span>
            </div>
            <div className={styles.muted}>
              {r.activeProvider ? `${r.activeProvider} · ` : ""}
              {r.openExceptions > 0 ? `${r.openExceptions} open issue${r.openExceptions === 1 ? "" : "s"} · ` : ""}
              {when(r.updatedAt)}
            </div>
          </span>
          <button type="button" className={styles.primaryButton} onClick={() => pos.openRecovery(r.orderId)}>
            Open
          </button>
        </div>
      ))}
    </>
  );
}

/**
 * Payment recovery (Blueprint §10.4, §10.7, §10.11): a dedicated screen for one order whose money
 * cannot be proven settled. It never offers to charge again; it reads the server, and resolves by
 * repair (money captured, sale unfinished) or release (no money in flight).
 */
export function RecoveryScreen({ pos }: { pos: PosStore }) {
  const orderId = pos.recoveryOrderId;
  const [status, setStatus] = useState<PaymentStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [repairing, setRepairing] = useState(false);
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [notes, setNotes] = useState("");
  const [badge, setBadge] = useState("");
  const [split, setSplit] = useState<SplitSession | null>(null);
  const { gateway } = pos;

  const load = async () => {
    if (!orderId) return;
    const [res, sp] = await Promise.all([gateway.paymentStatus(orderId), gateway.findSplit(orderId)]);
    setSplit(sp.ok ? sp.data : null);
    if (res.ok) {
      setStatus(res.data);
      setError(null);
    } else setError(res.error);
  };
  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId]);

  if (!orderId) {
    return (
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>Payments to resolve</h2>
        <div className={styles.list}>
          <ResolveList pos={pos} />
        </div>
      </section>
    );
  }

  const inFlight = status?.state === "payment_processing";
  const capturedUnfinished = status?.state === "allocation_pending" && !status.salesInvoiceId;
  const settled = Boolean(status?.salesInvoiceId);
  const splitHasMoney = Boolean(split && (split.locked > 0 || split.pending > 0 || split.refunds.length > 0));
  const releasable =
    status != null && !settled && !split && !inFlight && (status.state === "awaiting_payment" || status.state === "payment_failed");

  return (
    <section className={styles.panel}>
      <div className={styles.sectionHead}>
        <h2 className={styles.panelTitle}>Resolve payment</h2>
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => pos.openRecovery(null)}>
          <ArrowLeft size={16} aria-hidden /> All payments to resolve
        </button>
      </div>
      {error ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{error}</p> : null}
      {status ? (
        <>
          <div className={styles.dueRow}>
            <span>{label(status.state)}</span>
            <strong style={{ fontSize: 22 }}>{formatMoney(status.total, status.currency)}</strong>
          </div>
          <dl className={styles.facts}>
            <dt>Provider</dt>
            <dd>{status.activeProvider ?? status.settledProvider ?? "—"}</dd>
            <dt>Provider says</dt>
            <dd>{status.providerStatus ?? "—"}{status.providerFailure ? ` · ${status.providerFailure}` : ""}</dd>
            <dt>Reference</dt>
            <dd>{status.settledProviderRef ?? status.activeIntentId ?? "—"}</dd>
            <dt>Invoice</dt>
            <dd>{status.salesInvoiceId ? "Posted" : "Not posted"}</dd>
            <dt>Stock held until</dt>
            <dd>{when(status.reservationExpiresAt) || "—"}</dd>
          </dl>
          {split ? (
            <SplitRecoveryDetail
              pos={pos}
              session={split}
              onChanged={(next) => {
                if (next) setSplit(next);
                void load();
                void pos.refreshSplitRecovery();
              }}
            />
          ) : null}
          <TerminalRecoveryList pos={pos} orderId={status.orderId} />
          {status.activeIntentId && (status.activeProvider === "ecocash" || status.activeProvider === "paynow" || status.activeProvider === "contipay") ? (
            <PaymentLetters pos={pos} kind={status.activeProvider} sourceId={status.activeIntentId} />
          ) : null}
          {inFlight && !splitHasMoney ? (
            <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">
              <CircleAlert size={16} aria-hidden /> We cannot prove whether the customer has paid. Do not take this payment again. Check again,
              or ask the customer to show the provider&apos;s confirmation.
            </p>
          ) : null}
          {settled ? (
            <p className={`${styles.statusBanner} ${styles.statusNotice}`} role="status">
              The payment settled and the sale is posted. Nothing else to do; the order is in Ready for pickup.
            </p>
          ) : null}
          {status.exceptions.length > 0 ? (
            <div className={styles.list} style={{ marginTop: 12 }}>
              {status.exceptions.map((e) => (
                <div key={e.id} className={styles.listRow}>
                  <span>
                    <div className={styles.listTitle}>
                      {e.code.replace(/_/g, " ").toLowerCase()} {e.resolvedAt ? <span className={styles.badge}>Resolved</span> : null}
                    </div>
                    <div className={styles.muted}>
                      {e.detail ?? ""} {e.resolution ? `· ${e.resolution}` : ""} · {when(e.createdAt)}
                    </div>
                  </span>
                </div>
              ))}
            </div>
          ) : null}
          {capturedUnfinished ? (
            repairing ? (
              <form
                onSubmit={async (e) => {
                  e.preventDefault();
                  const proof: ManagerProof = pos.selfApprover
                    ? { kind: "self" }
                    : badge.trim()
                      ? { kind: "badge", payload: badge.trim() }
                      : { kind: "password", credentials: { identifier, password } };
                  const ok = await pos.repairPaidOrder(status.orderId, notes.trim() || null, proof);
                  setBadge("");
                  if (ok) {
                    setRepairing(false);
                    setPassword("");
                    void load();
                  }
                }}
              >
                <p className={styles.muted}>
                  <ShieldCheck size={14} aria-hidden /> The customer&apos;s money arrived but the sale did not finish. Repair posts the sale
                  against that money. {pos.selfApprover ? "You are signed in as a manager." : "Scan a manager's badge, or a manager or finance user signs in."}
                </p>
                {pos.selfApprover ? null : (
                <>
                <label className={styles.field}>
                  <span className={styles.fieldLabel}>Manager ID badge (scan)</span>
                  <input className={styles.input} type="password" value={badge} onChange={(e) => setBadge(e.target.value)} autoComplete="off" autoFocus />
                </label>
                <div className={styles.formGrid}>
                  <label className={styles.field}>
                    <span className={styles.fieldLabel}>Manager emp# / email / phone</span>
                    <input className={styles.input} value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoComplete="off" />
                  </label>
                  <label className={styles.field}>
                    <span className={styles.fieldLabel}>Manager password</span>
                    <input className={styles.input} type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="off" />
                  </label>
                </div>
                </>
                )}
                <label className={styles.field} style={{ marginTop: 12 }}>
                  <span className={styles.fieldLabel}>Notes (optional)</span>
                  <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} />
                </label>
                <div className={styles.rowEnd}>
                  <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setRepairing(false)}>
                    Cancel
                  </button>
                  <button type="submit" className={styles.primaryButton} disabled={pos.busy || (!pos.selfApprover && !badge.trim() && (!identifier.trim() || !password))}>
                    Repair paid order
                  </button>
                </div>
              </form>
            ) : null
          ) : null}
          <div className={styles.rowEnd}>
            <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => void load()}>
              <RefreshCw size={14} aria-hidden /> Check again
            </button>
            {capturedUnfinished && !repairing ? (
              <button type="button" className={styles.primaryButton} onClick={() => setRepairing(true)}>
                Repair paid order
              </button>
            ) : null}
            {releasable ? (
              <button
                type="button"
                className={styles.primaryButton}
                disabled={pos.busy}
                onClick={async () => {
                  if (await pos.releaseOrder(status.orderId, "Released from payment recovery")) void load();
                }}
              >
                Release stock and unlock the sale
              </button>
            ) : null}
          </div>
        </>
      ) : null}
    </section>
  );
}
