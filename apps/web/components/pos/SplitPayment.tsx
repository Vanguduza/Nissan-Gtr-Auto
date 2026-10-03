"use client";

import { Banknote, CircleAlert, Layers, MinusCircle, RefreshCw, ShieldCheck } from "lucide-react";
import { useEffect, useState } from "react";
import { formatMoney, roundMoney } from "@/lib/pos/money";
import type { ManagerProof, RefundFeePolicy, SplitLeg, SplitRefund, SplitSession, SplitTender } from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import styles from "./pos.module.css";

const TENDER_LABEL: Record<string, string> = {
  cash: "Cash",
  bank: "Card / bank",
  store_credit: "Store credit",
  ecocash: "EcoCash",
  paynow: "Paynow",
  contipay: "ContiPay",
  card_terminal: "Card machine",
};
export const tenderLabel = (t: string) => TENDER_LABEL[t] ?? t.replace(/_/g, " ");

const LEG_LABEL: Record<SplitLeg["status"], string> = {
  planned: "Not started",
  held: "Held from store credit",
  pending: "Waiting for the provider",
  captured: "Received",
  failed: "Failed",
  unknown: "Not confirmed",
  allocated: "Applied to the invoice",
  refund_review: "Refund to approve",
  refund_pending: "Refund approved",
  refunded: "Refunded",
  cancelled: "Cancelled",
};

const SESSION_LABEL: Record<SplitSession["status"], string> = {
  open: "No part paid yet",
  partially_captured: "Part paid",
  leg_pending: "A part is in progress",
  fully_committed: "Paid in full, posting",
  finalizing: "Posting the sale",
  settled: "Paid and posted",
  finalization_failed: "Paid in full, sale not posted",
  refund_review: "Refund to approve",
  refund_pending: "Refund approved, not yet paid",
  refunded: "Refunded",
  cancelled: "Cancelled",
};
export const splitStatusLabel = (s: SplitSession["status"]) => SESSION_LABEL[s] ?? s.replace(/_/g, " ");

const REFUND_LABEL: Record<string, string> = {
  review: "To approve",
  pending: "Approved, pay the customer",
  settled: "Paid to the customer",
  failed: "Failed",
  cancelled: "Cancelled",
};

const FEE_POLICIES: Array<{ id: RefundFeePolicy; label: string }> = [
  { id: "manual_review", label: "Decide later (manager review)" },
  { id: "business_absorbs", label: "The business pays any fees" },
  { id: "customer_bears", label: "Fees come off the refund" },
];

/** Total, received and still due — every figure from the server's split session. */
export function SplitSummary({ session }: { session: SplitSession }) {
  return (
    <dl className={styles.facts}>
      <dt>Sale total</dt>
      <dd>{formatMoney(session.total, session.currency)}</dd>
      <dt>Received</dt>
      <dd>{formatMoney(session.locked, session.currency)}</dd>
      {session.pending > 0 ? (
        <>
          <dt>In progress</dt>
          <dd>{formatMoney(session.pending, session.currency)}</dd>
        </>
      ) : null}
      {["open", "partially_captured", "leg_pending"].includes(session.status) ? (
        <>
          <dt>Still due</dt>
          <dd>
            <strong>{formatMoney(session.balanceDue, session.currency)}</strong>
          </dd>
        </>
      ) : null}
    </dl>
  );
}

export function SplitLegs({ session }: { session: SplitSession }) {
  if (session.legs.length === 0) return <div className={styles.emptyCard}>No part taken yet.</div>;
  return (
    <div className={styles.list}>
      {session.legs.map((l) => (
        <div key={l.id} className={styles.listRow}>
          <span>
            <div className={styles.listTitle}>
              {l.sequenceNo}. {tenderLabel(l.tender)} <span className={styles.badge}>{LEG_LABEL[l.status] ?? l.status}</span>
            </div>
            <div className={styles.muted}>
              {[l.reference ?? l.providerRef, l.statusDetail, l.refundRequired && l.refundRequired > 0 ? `${formatMoney(l.refundRequired, session.currency)} to refund` : null]
                .filter(Boolean)
                .join(" · ") || " "}
            </div>
          </span>
          <strong>{formatMoney(l.amount, session.currency)}</strong>
        </div>
      ))}
    </div>
  );
}

/**
 * Part payments inside Payment (Blueprint §10.5, §9.4): one part per step, the header is the
 * server's balance. Cash and card/bank are received at once; store credit is held until the sale
 * posts. The sale posts on its own when the parts cover it. If the customer cannot pay the rest,
 * the governed reduced basket (§10.8) keeps only what is paid for.
 */
/** [onCancelled] closes Payment once the part-paid sale is cancelled; a finished sale shows its receipt here. */
export function SplitPanel({ pos, onCancelled }: { pos: PosStore; onCancelled: () => void }) {
  const session = pos.split!;
  const currency = session.currency;
  const [tender, setTender] = useState<SplitTender>("cash");
  const [amount, setAmount] = useState<string>(String(session.availableToAllocate));
  const [reference, setReference] = useState("");
  const [cashGiven, setCashGiven] = useState("");
  const [reducing, setReducing] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  // Each step starts at the server's remaining balance (Blueprint §9.4: the balance is the step header).
  useEffect(() => {
    setAmount(String(session.availableToAllocate));
  }, [session.availableToAllocate]);
  const value = Number(amount);
  const registered = Boolean(pos.cart?.customerId) && pos.cart?.customerName !== "POS Walk-in";
  const open = !["settled", "finalizing", "finalization_failed", "refund_review", "refund_pending", "refunded", "cancelled"].includes(session.status);
  const valid = Number.isFinite(value) && value > 0 && value <= session.availableToAllocate + 0.001 && (tender !== "bank" || reference.trim().length > 0);
  const change = tender === "cash" && Number(cashGiven) > 0 && Number.isFinite(value) ? roundMoney(Number(cashGiven) - value) : null;
  const reasons: Record<SplitTender, string | null> = {
    cash: null,
    bank: null,
    store_credit: registered ? null : "Choose the customer first: store credit is theirs.",
  };

  return (
    <div style={{ marginTop: 12 }}>
      <div className={styles.dueRow}>
        <span>
          <Layers size={14} aria-hidden /> Part payments · {splitStatusLabel(session.status)}
        </span>
        <strong style={{ fontSize: 22 }}>{formatMoney(session.balanceDue, currency)} due</strong>
      </div>
      <SplitSummary session={session} />
      <SplitLegs session={session} />

      {session.status === "finalization_failed" ? (
        <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">
          <CircleAlert size={16} aria-hidden /> Paid in full, but the sale did not post{session.finalizationError ? `: ${session.finalizationError}` : ""}. Do not take more money.
        </p>
      ) : null}

      {open && session.availableToAllocate > 0 && !reducing && !cancelling ? (
        <>
          <h3 className={styles.panelTitle} style={{ fontSize: 15, marginTop: 14 }}>
            Take the next part
          </h3>
          <div className={styles.tenderGrid} role="radiogroup" aria-label="Tender for this part">
            {(Object.keys(reasons) as SplitTender[]).map((t) => (
              <button
                key={t}
                type="button"
                role="radio"
                aria-checked={tender === t}
                className={`${styles.tenderCard} ${tender === t ? styles.tenderCardActive : ""}`}
                disabled={Boolean(reasons[t])}
                onClick={() => setTender(t)}
              >
                <span className={styles.listTitle}>{tenderLabel(t)}</span>
                {reasons[t] ? <span className={styles.muted}>{reasons[t]}</span> : null}
              </button>
            ))}
            {(["ecocash", "paynow", "contipay"] as const).map((p) => (
              <button key={p} type="button" role="radio" aria-checked={false} className={styles.tenderCard} disabled>
                <span className={styles.listTitle}>{tenderLabel(p)}</span>
                <span className={styles.muted}>{pos.providers?.[p] ?? "Not available for part payments yet: take it as the whole payment."}</span>
              </button>
            ))}
            <button type="button" role="radio" aria-checked={false} className={styles.tenderCard} disabled>
              <span className={styles.listTitle}>Card machine</span>
              <span className={styles.muted}>Use the counter tablet paired with the card machine.</span>
            </button>
          </div>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Amount for this part (up to {formatMoney(session.availableToAllocate, currency)})</span>
              <input className={styles.input} type="number" min={0} step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} />
            </label>
            {tender === "bank" ? (
              <label className={styles.field}>
                <span className={styles.fieldLabel}>Card slip or transfer reference (required)</span>
                <input className={styles.input} value={reference} onChange={(e) => setReference(e.target.value)} autoComplete="off" />
              </label>
            ) : null}
            {tender === "cash" ? (
              <label className={styles.field}>
                <span className={styles.fieldLabel}>
                  <Banknote size={12} aria-hidden /> Cash handed over {change != null ? `· change ${formatMoney(Math.max(0, change), currency)}` : ""}
                </span>
                <input className={styles.input} type="number" min={0} step="0.01" value={cashGiven} onChange={(e) => setCashGiven(e.target.value)} />
              </label>
            ) : null}
          </div>
          <div className={styles.rowEnd}>
            {session.locked > 0 ? (
              <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setReducing(true)}>
                <MinusCircle size={16} aria-hidden /> Customer can&apos;t pay the rest
              </button>
            ) : null}
            <button
              type="button"
              className={styles.primaryButton}
              disabled={pos.busy || !valid || (change != null && change < 0)}
              onClick={async () => {
                // A completed sale shows its receipt in place; otherwise clear this part's details.
                if (!(await pos.addSplitPart(tender, value, tender === "bank" ? reference : null))) {
                  setReference("");
                  setCashGiven("");
                }
              }}
            >
              Take {Number.isFinite(value) && value > 0 ? formatMoney(value, currency) : "part"}
            </button>
          </div>
        </>
      ) : null}

      {reducing ? <ReduceBasket pos={pos} session={session} onCancel={() => setReducing(false)} /> : null}

      {cancelling ? (
        <CancelSplitForm pos={pos} session={session} onCancel={() => setCancelling(false)} onDone={onCancelled} />
      ) : open && !reducing ? (
        <p className={styles.muted} style={{ marginTop: 10 }}>
          Stopping this sale?{" "}
          <button type="button" className={styles.linkButton} onClick={() => setCancelling(true)}>
            Cancel part payments
          </button>
          {session.locked > 0 ? " — money already received becomes a refund for a manager to approve." : ""}
        </p>
      ) : null}
    </div>
  );
}

/**
 * Reduced basket (Blueprint §10.8): an explicit action from the part-paid state, never a cart edit.
 * The operator chooses what the customer keeps; the server works out the legal total, which cannot
 * exceed what has been received, re-reserves the stock and posts the sale. Any surplus becomes a refund.
 */
function ReduceBasket({ pos, session, onCancel }: { pos: PosStore; session: SplitSession; onCancel: () => void }) {
  const lines = pos.cart?.lines ?? [];
  const [keep, setKeep] = useState<Record<string, number>>(() => Object.fromEntries(lines.map((l) => [l.id, l.qty])));
  const [confirmed, setConfirmed] = useState(false);
  const [notes, setNotes] = useState("");
  const items = lines.filter((l) => (keep[l.id] ?? 0) > 0).map((l) => ({ cartLineId: l.id, qty: keep[l.id]! }));
  return (
    <div className={styles.panelInset} style={{ marginTop: 14 }}>
      <h3 className={styles.panelTitle} style={{ fontSize: 15 }}>
        Keep only what is paid for
      </h3>
      <p className={styles.muted}>
        {formatMoney(session.locked, session.currency)} has been received. Choose what the customer takes; the new total is worked out by the
        server and cannot be more than that. Anything received over the new total is refunded.
      </p>
      <div className={styles.list}>
        {lines.map((l) => (
          <div key={l.id} className={styles.listRow}>
            <span>
              <div className={styles.listTitle}>{l.name}</div>
              <div className={styles.muted}>
                {l.oemPartNumber} · {formatMoney(l.unitPrice, session.currency)} each · {l.qty} on the sale
              </div>
            </span>
            <label className={styles.field} style={{ width: 110 }}>
              <span className={styles.fieldLabel}>Keep</span>
              <input
                className={styles.input}
                type="number"
                min={0}
                max={l.qty}
                step={1}
                value={keep[l.id] ?? 0}
                onChange={(e) => setKeep((k) => ({ ...k, [l.id]: Math.max(0, Math.min(l.qty, Number(e.target.value) || 0)) }))}
              />
            </label>
          </div>
        ))}
      </div>
      <label className={styles.field} style={{ marginTop: 10 }}>
        <span className={styles.fieldLabel}>Notes (optional)</span>
        <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} />
      </label>
      <label className={styles.checkRow}>
        <input type="checkbox" checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} /> The customer agreed to take only these items.
      </label>
      <div className={styles.rowEnd}>
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onCancel}>
          Back
        </button>
        <button
          type="button"
          className={styles.primaryButton}
          disabled={pos.busy || !confirmed || items.length === 0}
          onClick={async () => {
            await pos.reduceBasket(items, notes.trim() || null);
          }}
        >
          Finish with these items
        </button>
      </div>
    </div>
  );
}

function CancelSplitForm({ pos, session, onCancel, onDone }: { pos: PosStore; session: SplitSession; onCancel: () => void; onDone: () => void }) {
  const [reason, setReason] = useState("");
  const [policy, setPolicy] = useState<RefundFeePolicy>("manual_review");
  return (
    <div className={styles.panelInset} style={{ marginTop: 14 }}>
      <h3 className={styles.panelTitle} style={{ fontSize: 15 }}>
        Cancel part payments
      </h3>
      <p className={styles.muted}>
        The stock goes back on sale.{" "}
        {session.locked > 0 ? `${formatMoney(session.locked, session.currency)} already received is refunded only after a manager approves it.` : "No money has been taken."}
      </p>
      <label className={styles.field}>
        <span className={styles.fieldLabel}>Reason (required)</span>
        <input className={styles.input} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      {session.locked > 0 ? (
        <label className={styles.field}>
          <span className={styles.fieldLabel}>Refund fees</span>
          <select className={styles.input} value={policy} onChange={(e) => setPolicy(e.target.value as RefundFeePolicy)}>
            {FEE_POLICIES.map((p) => (
              <option key={p.id} value={p.id}>
                {p.label}
              </option>
            ))}
          </select>
        </label>
      ) : null}
      <div className={styles.rowEnd}>
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onCancel}>
          Keep the sale
        </button>
        <button
          type="button"
          className={styles.dangerButton}
          disabled={pos.busy || !reason.trim()}
          onClick={async () => {
            if (await pos.cancelSplit(reason.trim(), policy)) onDone();
          }}
        >
          Cancel part payments
        </button>
      </div>
    </div>
  );
}

/** How an approver authorises one step: signed in already, a scanned ID badge, or a password. */
export function ApproverProofFields({
  pos,
  onProof,
}: {
  pos: PosStore;
  onProof: (proof: ManagerProof | null) => void;
}) {
  const [badge, setBadge] = useState("");
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const emit = (b: string, i: string, p: string) =>
    onProof(b.trim() ? { kind: "badge", payload: b.trim() } : i.trim() && p ? { kind: "password", credentials: { identifier: i.trim(), password: p } } : null);
  if (pos.selfApprover) {
    return (
      <p className={styles.muted}>
        <ShieldCheck size={14} aria-hidden /> You are signed in as an approver.
      </p>
    );
  }
  return (
    <>
      <label className={styles.field}>
        <span className={styles.fieldLabel}>Approver ID badge (scan)</span>
        <input
          className={styles.input}
          type="password"
          autoComplete="off"
          value={badge}
          onChange={(e) => {
            setBadge(e.target.value);
            emit(e.target.value, identifier, password);
          }}
        />
      </label>
      <div className={styles.formGrid}>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>Or manager / finance emp# / email</span>
          <input
            className={styles.input}
            autoComplete="off"
            value={identifier}
            onChange={(e) => {
              setIdentifier(e.target.value);
              emit(badge, e.target.value, password);
            }}
          />
        </label>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>Password</span>
          <input
            className={styles.input}
            type="password"
            autoComplete="off"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              emit(badge, identifier, e.target.value);
            }}
          />
        </label>
      </div>
    </>
  );
}

/** One refund of a captured part: approve (fees), record it paid (reference), or mark it failed. */
function RefundRow({ pos, session, refund, onChanged }: { pos: PosStore; session: SplitSession; refund: SplitRefund; onChanged: () => void }) {
  const [step, setStep] = useState<"approve" | "complete" | "fail" | null>(null);
  const [policy, setPolicy] = useState<RefundFeePolicy>(refund.feePolicy === "manual_review" ? "business_absorbs" : (refund.feePolicy as RefundFeePolicy));
  const [fee, setFee] = useState("0");
  const [ref, setRef] = useState("");
  const [text, setText] = useState("");
  const [proof, setProof] = useState<ManagerProof | null>(pos.selfApprover ? { kind: "self" } : null);
  const leg = session.legs.find((l) => l.id === refund.legId);
  const done = refund.status === "settled" || refund.status === "cancelled";
  const submit = async () => {
    const p = pos.selfApprover ? ({ kind: "self" } as const) : proof;
    if (!p || !step) return;
    const ok = await pos.splitRefundStep(
      refund.id,
      step === "approve"
        ? { kind: "approve", feePolicy: policy, customerFee: policy === "customer_bears" ? Number(fee) || 0 : 0, notes: text.trim() || null }
        : step === "complete"
          ? { kind: "complete", providerRef: ref.trim(), notes: text.trim() || null }
          : { kind: "fail", reason: text.trim() },
      p,
    );
    if (ok) {
      setStep(null);
      onChanged();
    }
  };
  return (
    <div className={styles.listRow} style={{ flexDirection: "column", alignItems: "stretch" }}>
      <div style={{ display: "flex", justifyContent: "space-between", gap: 12 }}>
        <span>
          <div className={styles.listTitle}>
            Refund {leg ? `of ${tenderLabel(leg.tender)} part ${leg.sequenceNo}` : ""} <span className={styles.badge}>{REFUND_LABEL[refund.status] ?? refund.status}</span>
          </div>
          <div className={styles.muted}>
            {[refund.notes, refund.failureReason, refund.providerRef, refund.netCustomerRefund != null ? `customer gets ${formatMoney(refund.netCustomerRefund, session.currency)}` : null]
              .filter(Boolean)
              .join(" · ") || " "}
          </div>
        </span>
        <strong>{formatMoney(refund.grossAmount, session.currency)}</strong>
      </div>
      {!done && !step ? (
        <div className={styles.rowEnd}>
          {refund.status === "review" || refund.status === "failed" ? (
            <button type="button" className={styles.primaryButton} onClick={() => setStep("approve")}>
              Approve refund
            </button>
          ) : null}
          {refund.status === "pending" ? (
            <>
              <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setStep("fail")}>
                Refund failed
              </button>
              <button type="button" className={styles.primaryButton} onClick={() => setStep("complete")}>
                Refund paid to customer
              </button>
            </>
          ) : null}
        </div>
      ) : null}
      {step ? (
        <div className={styles.panelInset}>
          {step === "approve" ? (
            <div className={styles.formGrid}>
              <label className={styles.field}>
                <span className={styles.fieldLabel}>Fees</span>
                <select className={styles.input} value={policy} onChange={(e) => setPolicy(e.target.value as RefundFeePolicy)}>
                  {FEE_POLICIES.filter((p) => p.id !== "manual_review").map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.label}
                    </option>
                  ))}
                </select>
              </label>
              {policy === "customer_bears" ? (
                <label className={styles.field}>
                  <span className={styles.fieldLabel}>Fee kept from the refund</span>
                  <input className={styles.input} type="number" min={0} step="0.01" value={fee} onChange={(e) => setFee(e.target.value)} />
                </label>
              ) : null}
            </div>
          ) : null}
          {step === "complete" ? (
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Refund reference (cash slip, transfer or reversal number)</span>
              <input className={styles.input} value={ref} onChange={(e) => setRef(e.target.value)} />
            </label>
          ) : null}
          <label className={styles.field}>
            <span className={styles.fieldLabel}>{step === "fail" ? "What went wrong (required)" : "Notes (optional)"}</span>
            <input className={styles.input} value={text} onChange={(e) => setText(e.target.value)} />
          </label>
          <ApproverProofFields pos={pos} onProof={setProof} />
          <div className={styles.rowEnd}>
            <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setStep(null)}>
              Cancel
            </button>
            <button
              type="button"
              className={styles.primaryButton}
              disabled={pos.busy || (!pos.selfApprover && !proof) || (step === "complete" && !ref.trim()) || (step === "fail" && !text.trim())}
              onClick={() => void submit()}
            >
              {step === "approve" ? "Approve refund" : step === "complete" ? "Record refund paid" : "Mark refund failed"}
            </button>
          </div>
        </div>
      ) : null}
    </div>
  );
}

/** Recovery for a part-paid sale: its parts, its refunds, and the only safe next steps. */
export function SplitRecoveryDetail({ pos, session, onChanged }: { pos: PosStore; session: SplitSession; onChanged: (s: SplitSession | null) => void }) {
  const [cancelling, setCancelling] = useState(false);
  return (
    <div style={{ marginTop: 14 }}>
      <div className={styles.dueRow}>
        <span>
          <Layers size={14} aria-hidden /> Part payments · {splitStatusLabel(session.status)}
        </span>
        <strong>
          {["open", "partially_captured", "leg_pending"].includes(session.status)
            ? `${formatMoney(session.balanceDue, session.currency)} due`
            : `${formatMoney(session.locked, session.currency)} received`}
        </strong>
      </div>
      <SplitSummary session={session} />
      <SplitLegs session={session} />
      {session.finalizationError ? (
        <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">
          <CircleAlert size={16} aria-hidden /> The sale did not post: {session.finalizationError}
        </p>
      ) : null}
      {session.refunds.length > 0 ? (
        <>
          <h3 className={styles.panelTitle} style={{ fontSize: 15, marginTop: 14 }}>
            Refunds
          </h3>
          <div className={styles.list}>
            {session.refunds.map((r) => (
              <RefundRow key={r.id} pos={pos} session={session} refund={r} onChanged={() => onChanged(null)} />
            ))}
          </div>
        </>
      ) : null}
      {cancelling ? (
        <CancelSplitRecovery pos={pos} session={session} onCancel={() => setCancelling(false)} onDone={(s) => onChanged(s)} />
      ) : null}
      <div className={styles.rowEnd}>
        {session.status === "finalization_failed" || session.status === "fully_committed" ? (
          <button
            type="button"
            className={styles.primaryButton}
            disabled={pos.busy}
            onClick={async () => onChanged(await pos.retrySplitFinalization(session.sessionId))}
          >
            <RefreshCw size={14} aria-hidden /> Post the sale again
          </button>
        ) : null}
        {!cancelling && ["open", "partially_captured", "finalization_failed"].includes(session.status) ? (
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setCancelling(true)}>
            Cancel part payments
          </button>
        ) : null}
      </div>
    </div>
  );
}

function CancelSplitRecovery({ pos, session, onCancel, onDone }: { pos: PosStore; session: SplitSession; onCancel: () => void; onDone: (s: SplitSession | null) => void }) {
  const [reason, setReason] = useState("");
  const [policy, setPolicy] = useState<RefundFeePolicy>("manual_review");
  return (
    <div className={styles.panelInset} style={{ marginTop: 14 }}>
      <label className={styles.field}>
        <span className={styles.fieldLabel}>Why is this sale being cancelled? (required)</span>
        <input className={styles.input} value={reason} onChange={(e) => setReason(e.target.value)} />
      </label>
      <label className={styles.field}>
        <span className={styles.fieldLabel}>Refund fees</span>
        <select className={styles.input} value={policy} onChange={(e) => setPolicy(e.target.value as RefundFeePolicy)}>
          {FEE_POLICIES.map((p) => (
            <option key={p.id} value={p.id}>
              {p.label}
            </option>
          ))}
        </select>
      </label>
      <div className={styles.rowEnd}>
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onCancel}>
          Keep it
        </button>
        <button
          type="button"
          className={styles.dangerButton}
          disabled={pos.busy || !reason.trim()}
          onClick={async () => {
            const res = await pos.gateway.cancelSplit(session.sessionId, reason.trim(), policy);
            if (res.ok) onDone(res.data);
            else pos.showError(res.error);
          }}
        >
          Cancel part payments
        </button>
      </div>
    </div>
  );
}
