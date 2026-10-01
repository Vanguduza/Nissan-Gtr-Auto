"use client";

import { Banknote, Car, Plus, Printer, ShieldCheck, Smartphone, Trash2 } from "lucide-react";
import { useMemo, useState, type ReactNode } from "react";
import { formatMoney, roundMoney } from "@/lib/pos/money";
import type { ReceiptDocument, Tender, TenderLine } from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import styles from "./pos.module.css";

export function Modal({
  title,
  children,
  onClose,
  wide,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  wide?: boolean;
}) {
  return (
    <div className={styles.modalScrim} role="presentation" onClick={onClose}>
      <div
        className={`${styles.modal} ${wide ? styles.modalWide : ""}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
        onClick={(e) => e.stopPropagation()}
      >
        <h2 className={styles.panelTitle}>{title}</h2>
        {children}
      </div>
    </div>
  );
}

const PROMPT_TITLE = {
  void: "Void sale",
  discount: "Approve discount",
  override: "Approve price override",
  refund: "Approve refund",
} as const;

/** Manager approval — an approver signs in on an isolated session for this one action (D4). */
export function ManagerDialog({ pos }: { pos: PosStore }) {
  const prompt = pos.managerPrompt;
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [notes, setNotes] = useState("");
  if (!prompt) return null;
  const detail =
    prompt.kind === "void"
      ? "All lines on this sale will be voided."
      : prompt.kind === "discount"
        ? `${prompt.percent}% off every non-core line.`
        : prompt.kind === "override"
          ? `${prompt.lineName}: new unit price ${formatMoney(prompt.unitPrice, pos.currency)}.`
          : `Refund ${prompt.documentNumber ?? "this sale"} through the finance refund pipeline.`;
  return (
    <Modal title={PROMPT_TITLE[prompt.kind]} onClose={pos.cancelManager}>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          const ok = await pos.confirmManager({ identifier, password }, notes.trim() || null);
          if (ok) {
            setPassword("");
            setNotes("");
          }
        }}
      >
        <p className={styles.muted}>
          <ShieldCheck size={14} aria-hidden /> {detail} An admin or shop manager must approve.
        </p>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Manager emp# / email / phone</span>
            <input className={styles.input} value={identifier} onChange={(e) => setIdentifier(e.target.value)} autoComplete="off" autoFocus />
          </label>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Manager password</span>
            <input className={styles.input} type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="off" />
          </label>
        </div>
        <label className={styles.field} style={{ marginTop: 12 }}>
          <span className={styles.fieldLabel}>Reason (recorded in the audit trail)</span>
          <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>
        {pos.error ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{pos.error}</p> : null}
        <div className={styles.rowEnd}>
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={pos.cancelManager}>
            Cancel
          </button>
          <button type="submit" className={styles.primaryButton} disabled={pos.busy || !identifier.trim() || !password}>
            Approve
          </button>
        </div>
      </form>
    </Modal>
  );
}

/** Customer has several garage vehicles → operator picks the one being shopped for. */
export function GarageChooser({ pos }: { pos: PosStore }) {
  if (!pos.garageChoices) return null;
  return (
    <Modal title="Which vehicle?" onClose={() => void pos.chooseGarageVehicle(null)}>
      <div className={styles.list}>
        {pos.garageChoices.map((g) => (
          <button
            key={g.id}
            type="button"
            className={styles.listRow}
            style={{ textAlign: "left", border: 0, font: "inherit", cursor: "pointer" }}
            onClick={() => void pos.chooseGarageVehicle(g)}
          >
            <span>
              <span className={styles.listTitle}>
                <Car size={14} aria-hidden /> {g.model} {g.chassisCode}
              </span>
              <div className={styles.muted}>
                {g.engine ?? "Engine not set"}
                {g.vin ? ` · VIN ${g.vin}` : ""}
              </div>
            </span>
            {g.isPrimary ? <span className={styles.badge}>Primary</span> : null}
          </button>
        ))}
      </div>
      <div className={styles.rowEnd}>
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => void pos.chooseGarageVehicle(null)}>
          Shop for another vehicle
        </button>
      </div>
    </Modal>
  );
}

const TENDERS: Array<{ id: Tender; label: string }> = [
  { id: "cash", label: "Cash" },
  { id: "bank", label: "Card / bank" },
  { id: "ecocash", label: "EcoCash" },
  { id: "store_credit", label: "Store credit" },
];

export function ReceiptView({ receipt, paper }: { receipt: ReceiptDocument; paper: "80mm" | "A4" }) {
  return (
    <div className={`${styles.receipt} ${paper === "80mm" ? styles.receiptPaper80 : styles.receiptPaperA4}`}>
      <div className={styles.receiptHead}>
        <strong>NISSAN GTR AUTO</strong>
        <div>Genuine Parts · Real Performance</div>
        <div>{receipt.documentNumber ?? receipt.invoiceId}</div>
        <div>{receipt.postedAt ? new Date(receipt.postedAt).toLocaleString("en-ZW") : ""}</div>
      </div>
      {receipt.customerName ? <div>Customer: {receipt.customerName}</div> : null}
      {receipt.vehicleLabel ? <div>Vehicle: {receipt.vehicleLabel}</div> : null}
      <hr className={styles.receiptRule} />
      {receipt.lines.map((l, i) => (
        <div key={`${l.oemPartNumber}-${i}`} style={{ marginBottom: 6 }}>
          <div>{l.name}</div>
          <div>{l.oemPartNumber}</div>
          <div className={styles.receiptLine}>
            <span style={{ whiteSpace: "nowrap" }}>
              {l.qty} × {formatMoney(l.unitPrice, receipt.currency)}
            </span>
            <span>{formatMoney(l.lineTotal, receipt.currency)}</span>
          </div>
        </div>
      ))}
      <hr className={styles.receiptRule} />
      <div className={styles.receiptLine}>
        <strong>Total</strong>
        <strong>{formatMoney(receipt.total, receipt.currency)}</strong>
      </div>
      {receipt.tenders.map((t, i) => (
        <div key={`${t.tender}-${i}`} className={styles.receiptLine}>
          <span>{TENDERS.find((x) => x.id === t.tender)?.label ?? t.tender}</span>
          <span>{formatMoney(t.amount, receipt.currency)}</span>
        </div>
      ))}
      <hr className={styles.receiptRule} />
      <div className={styles.receiptHead}>
        {receipt.operator ? <div>Served by {receipt.operator}</div> : null}
        <div>Thank you</div>
      </div>
    </div>
  );
}

/**
 * Payment surface. Tenders must equal the balance exactly (`settle_invoice_tenders`); cash change
 * is computed from the amount handed over and is not posted.
 */
export function PaymentDialog({
  pos,
  onClose,
  paper,
  setPaper,
}: {
  pos: PosStore;
  onClose: () => void;
  paper: "80mm" | "A4";
  setPaper: (p: "80mm" | "A4") => void;
}) {
  const due = roundMoney(pos.subtotal);
  const currency = pos.currency;
  const [tenders, setTenders] = useState<TenderLine[]>([{ tender: "cash", amount: due }]);
  const [cashGiven, setCashGiven] = useState("");
  const [email, setEmail] = useState("");
  const [whatsapp, setWhatsapp] = useState("");
  const [msisdn, setMsisdn] = useState("");
  const receipt = pos.lastReceipt;

  const paid = roundMoney(tenders.reduce((s, t) => s + (Number.isFinite(t.amount) ? t.amount : 0), 0));
  const remaining = roundMoney(due - paid);
  const cashTender = tenders.filter((t) => t.tender === "cash").reduce((s, t) => s + t.amount, 0);
  const change = useMemo(() => {
    const given = Number(cashGiven);
    return Number.isFinite(given) && given > 0 ? roundMoney(given - cashTender) : null;
  }, [cashGiven, cashTender]);

  const update = (i: number, patch: Partial<TenderLine>) =>
    setTenders((cur) => cur.map((t, idx) => (idx === i ? { ...t, ...patch } : t)));

  if (receipt) {
    const usedEcocash = receipt.tenders.some((t) => t.tender === "ecocash");
    return (
      <Modal title={`Sale complete · ${receipt.documentNumber ?? ""}`} onClose={() => { pos.clearReceipt(); onClose(); }} wide>
        <div className={styles.receiptToolbar}>
          <div className={styles.segment} role="group" aria-label="Paper">
            {(["80mm", "A4"] as const).map((p) => (
              <button key={p} type="button" className={`${styles.segmentItem} ${paper === p ? styles.segmentActive : ""}`} onClick={() => setPaper(p)}>
                {p}
              </button>
            ))}
          </div>
          <button
            type="button"
            className={styles.primaryButton}
            onClick={() => {
              const style = document.createElement("style");
              style.textContent = `@page { size: ${paper === "80mm" ? "80mm auto" : "A4"}; margin: ${paper === "80mm" ? "4mm" : "14mm"}; }`;
              document.head.appendChild(style);
              window.print();
              style.remove();
            }}
          >
            <Printer size={16} aria-hidden /> Print receipt
          </button>
        </div>
        <div className={styles.receiptPreview}>
          <ReceiptView receipt={receipt} paper={paper} />
        </div>
        {usedEcocash ? (
          <div className={styles.row} style={{ marginTop: 14 }}>
            <label className={styles.field} style={{ flex: 1 }}>
              <span className={styles.fieldLabel}>Customer EcoCash number</span>
              <input className={styles.input} value={msisdn} onChange={(e) => setMsisdn(e.target.value)} placeholder="+26377…" inputMode="tel" />
            </label>
            <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} style={{ alignSelf: "flex-end" }} disabled={!msisdn.trim()} onClick={() => void pos.requestEcocash(receipt, msisdn.trim())}>
              <Smartphone size={16} aria-hidden /> Send EcoCash request
            </button>
          </div>
        ) : null}
        <div className={styles.rowEnd}>
          <button type="button" className={styles.primaryButton} onClick={() => { pos.clearReceipt(); onClose(); }}>
            New sale
          </button>
        </div>
      </Modal>
    );
  }

  return (
    <Modal title="Payment" onClose={onClose} wide>
      <div className={styles.dueRow}>
        <span>Amount due</span>
        <strong style={{ fontSize: 22 }}>{formatMoney(due, currency)}</strong>
      </div>
      {tenders.map((t, i) => (
        <div key={i} className={styles.tenderRow}>
          <select className={styles.input} value={t.tender} onChange={(e) => update(i, { tender: e.target.value as Tender })} aria-label="Tender">
            {TENDERS.map((x) => (
              <option key={x.id} value={x.id}>
                {x.label}
              </option>
            ))}
          </select>
          <input
            className={styles.input}
            type="number"
            min={0}
            step="0.01"
            value={Number.isFinite(t.amount) ? t.amount : ""}
            onChange={(e) => update(i, { amount: Number(e.target.value) })}
            aria-label="Amount"
          />
          <button type="button" className={styles.iconButton} aria-label="Remove tender" disabled={tenders.length === 1} onClick={() => setTenders((cur) => cur.filter((_, idx) => idx !== i))}>
            <Trash2 size={16} aria-hidden />
          </button>
        </div>
      ))}
      <div className={styles.row} style={{ marginTop: 10 }}>
        <button
          type="button"
          className={`${styles.softButton} ${styles.inlineButton}`}
          onClick={() => setTenders((cur) => [...cur, { tender: "bank", amount: Math.max(0, remaining) }])}
        >
          <Plus size={16} aria-hidden /> Split payment
        </button>
        <span className={remaining === 0 ? styles.stockIn : styles.stockOut}>
          {remaining === 0 ? "Balanced" : remaining > 0 ? `${formatMoney(remaining, currency)} still to allocate` : `${formatMoney(-remaining, currency)} over the balance`}
        </span>
      </div>
      {cashTender > 0 ? (
        <div className={styles.formGrid}>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>
              <Banknote size={12} aria-hidden /> Cash handed over
            </span>
            <input className={styles.input} type="number" min={0} step="0.01" value={cashGiven} onChange={(e) => setCashGiven(e.target.value)} />
          </label>
          <div className={styles.field}>
            <span className={styles.fieldLabel}>Change due</span>
            <strong style={{ fontSize: 20, lineHeight: "42px" }}>{change == null ? "—" : formatMoney(Math.max(0, change), currency)}</strong>
          </div>
        </div>
      ) : null}
      <div className={styles.formGrid}>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>Receipt email (optional)</span>
          <input className={styles.input} type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>Receipt WhatsApp (optional)</span>
          <input className={styles.input} inputMode="tel" placeholder="+26377…" value={whatsapp} onChange={(e) => setWhatsapp(e.target.value)} />
        </label>
      </div>
      {pos.error ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{pos.error}</p> : null}
      <div className={styles.rowEnd}>
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onClose}>
          Back to sale
        </button>
        <button
          type="button"
          className={styles.primaryButton}
          disabled={pos.busy || remaining !== 0 || tenders.some((t) => !(t.amount > 0)) || (change != null && change < 0)}
          onClick={() =>
            void pos.checkout(
              tenders.map((t) => ({ ...t, amount: roundMoney(t.amount) })),
              { email: email.trim() || null, whatsappE164: whatsapp.trim() || null, phoneE164: null },
            )
          }
        >
          Complete sale · {formatMoney(due, currency)}
        </button>
      </div>
    </Modal>
  );
}
