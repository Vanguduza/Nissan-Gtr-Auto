"use client";

import { Banknote, Car, Plus, Printer, ShieldCheck, Smartphone, Trash2, X } from "lucide-react";
import { useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { formatMoney, roundMoney } from "@/lib/pos/money";
import { THERMAL_COLUMNS, thermalLines } from "@gtr/shared";
import { webReceiptRows } from "@/lib/pos/receipt";
import type { ReasonCode, ReceiptDocument, Tender, TenderLine } from "@/lib/pos/types";
import { reasonActionFor, type PosStore } from "@/lib/pos/use-pos";
import styles from "./pos.module.css";

/** Open dialog layers, oldest first. Everything beneath the top layer is inert. */
const layers: HTMLElement[] = [];
const SHELL_ID = "pos-shell";
const LAYERS_ID = "pos-layers";

function syncInert() {
  const shell = document.getElementById(SHELL_ID);
  const top = layers[layers.length - 1];
  shell?.toggleAttribute("inert", layers.length > 0);
  layers.forEach((el) => el.toggleAttribute("inert", el !== top));
  document.documentElement.classList.toggle(styles.scrollLocked, layers.length > 0);
}

const FOCUSABLE =
  'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Focus dialog: the dialog is the only interactive thing on screen. The page behind is blurred,
 * dimmed and inert (no clicks, no tab stops, no screen-reader access); focus moves in, is trapped,
 * Escape closes, and focus returns to what opened it. On phones it rises as a bottom sheet.
 */
export function Modal({
  title,
  children,
  onClose,
  wide,
  sheet,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  wide?: boolean;
  /** Bottom sheet on every size (cart, vehicle picker on phones). Dialogs become sheets on Compact anyway. */
  sheet?: boolean;
}) {
  const layerRef = useRef<HTMLDivElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  const [mounted, setMounted] = useState(false);
  const titleId = useId();

  useEffect(() => setMounted(true), []);

  useEffect(() => {
    if (!mounted) return;
    const layer = layerRef.current;
    const dialog = dialogRef.current;
    if (!layer || !dialog) return;
    const opener = document.activeElement as HTMLElement | null;
    layers.push(layer);
    syncInert();
    const first = dialog.querySelector<HTMLElement>("[autofocus]") ?? dialog.querySelector<HTMLElement>(FOCUSABLE);
    (first ?? dialog).focus({ preventScroll: true });

    const onKey = (e: KeyboardEvent) => {
      if (layers[layers.length - 1] !== layer) return;
      if (e.key === "Escape") {
        e.stopPropagation();
        closeRef.current();
        return;
      }
      if (e.key !== "Tab") return;
      const items = Array.from(dialog.querySelectorAll<HTMLElement>(FOCUSABLE)).filter((el) => el.offsetParent !== null);
      if (items.length === 0) {
        e.preventDefault();
        return;
      }
      const firstItem = items[0];
      const lastItem = items[items.length - 1];
      if (e.shiftKey && document.activeElement === firstItem) {
        e.preventDefault();
        lastItem.focus();
      } else if (!e.shiftKey && document.activeElement === lastItem) {
        e.preventDefault();
        firstItem.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      const i = layers.indexOf(layer);
      if (i >= 0) layers.splice(i, 1);
      syncInert();
      if (opener && document.contains(opener)) opener.focus({ preventScroll: true });
    };
  }, [mounted]);

  if (!mounted) return null;
  return createPortal(
    <div ref={layerRef} className={`${styles.modalScrim} ${sheet ? styles.modalScrimSheet : ""}`} role="presentation" onMouseDown={(e) => {
      if (e.target === e.currentTarget) onClose();
    }}>
      <div
        ref={dialogRef}
        className={`${styles.modal} ${wide ? styles.modalWide : ""} ${sheet ? styles.modalSheet : ""}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
      >
        <div className={styles.modalHead}>
          <h2 id={titleId} className={styles.panelTitle}>{title}</h2>
          <button type="button" className={styles.iconButton} aria-label="Close" onClick={onClose}>
            <X size={18} aria-hidden />
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.getElementById(LAYERS_ID) ?? document.body,
  );
}

const PROMPT_TITLE = {
  void: "Void sale",
  discount: "Discount",
  override: "Price override",
  refund: "Refund",
  cashOut: "Approve cash out",
  tillVariance: "Approve cash variance",
  handover: "Approve till handover",
} as const;

function promptDetail(prompt: NonNullable<PosStore["managerPrompt"]>, pos: PosStore): string {
  switch (prompt.kind) {
    case "void":
      return "All lines on this sale will be voided.";
    case "discount":
      return `${prompt.percent}% off every non-core line.`;
    case "override":
      return `${prompt.lineName}: new unit price ${formatMoney(prompt.unitPrice, pos.currency)}.`;
    case "refund":
      return `Refund ${prompt.documentNumber ?? "this sale"} through the finance refund pipeline.`;
    case "cashOut":
      return `${prompt.label}: ${formatMoney(prompt.amount, pos.till?.currency ?? pos.currency)} out of the till.`;
    case "tillVariance":
      return prompt.variance == null
        ? "The counted cash does not match the till. Approving closes the till with the variance recorded."
        : `The till is ${formatMoney(Math.abs(prompt.variance), pos.till?.currency ?? pos.currency)} ${prompt.variance < 0 ? "short" : "over"}. Approving closes the till with the variance recorded.`;
    case "handover":
      return `Hand this till to ${prompt.name}. The current operator stops selling on it.`;
  }
}

/** Manager approval — an approver signs in on an isolated session for this one action (D4). */
export function ManagerDialog({ pos }: { pos: PosStore }) {
  const prompt = pos.managerPrompt;
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [notes, setNotes] = useState("");
  const [reasons, setReasons] = useState<ReasonCode[]>([]);
  const [reasonCode, setReasonCode] = useState("");
  const reasonAction = prompt ? reasonActionFor(prompt) : null;
  useEffect(() => {
    setReasonCode("");
    if (!reasonAction) {
      setReasons([]);
      return;
    }
    void pos.gateway.listReasons(reasonAction).then((r) => setReasons(r.ok ? r.data : []));
  }, [reasonAction, pos.gateway]);
  if (!prompt) return null;
  const detail = promptDetail(prompt, pos);
  const chosen = reasons.find((r) => r.code === reasonCode) ?? null;
  const reasonMissing = Boolean(reasonAction) && (!reasonCode || Boolean(chosen?.requiresNotes && !notes.trim()));
  // Policy (`pos_action_requires_manager`) decides; drawer actions always need a manager.
  const needsManager = pos.promptNeedsManager;
  return (
    <Modal title={PROMPT_TITLE[prompt.kind]} onClose={pos.cancelManager}>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          const ok = await pos.confirmManager(needsManager ? { identifier, password } : null, notes.trim() || null, reasonCode || null);
          if (ok) {
            setPassword("");
            setNotes("");
          }
        }}
      >
        <p className={styles.muted}>
          <ShieldCheck size={14} aria-hidden /> {detail}{" "}
          {needsManager ? "An admin or shop manager must approve." : "Within your limit: no manager needed. The reason is recorded in the audit trail."}
        </p>
        {needsManager ? (
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
        ) : null}
        {reasonAction ? (
          <label className={styles.field} style={{ marginTop: 12 }}>
            <span className={styles.fieldLabel}>Reason</span>
            <select className={styles.input} value={reasonCode} onChange={(e) => setReasonCode(e.target.value)}>
              <option value="">Choose a reason</option>
              {reasons.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.label}
                </option>
              ))}
            </select>
          </label>
        ) : null}
        <label className={styles.field} style={{ marginTop: 12 }}>
          <span className={styles.fieldLabel}>
            {reasonAction ? (chosen?.requiresNotes ? "Notes (required for this reason)" : "Notes (optional)") : "Reason (recorded in the audit trail)"}
          </span>
          <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>
        {pos.error ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{pos.error}</p> : null}
        <div className={styles.rowEnd}>
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={pos.cancelManager}>
            Cancel
          </button>
          <button
            type="submit"
            className={styles.primaryButton}
            disabled={pos.busy || (needsManager && (!identifier.trim() || !password)) || reasonMissing}
          >
            {needsManager ? "Approve" : "Confirm"}
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

/** Shared counter-receipt format v1: exactly the text the tablet prints (42 columns on 80 mm, 80 on A4). */
export function ReceiptView({ receipt, paper }: { receipt: ReceiptDocument; paper: "80mm" | "A4" }) {
  const width = paper === "80mm" ? THERMAL_COLUMNS : 80;
  return (
    <div className={`${styles.receipt} ${paper === "80mm" ? styles.receiptPaper80 : styles.receiptPaperA4}`}>
      {webReceiptRows(receipt).flatMap((row, i) =>
        thermalLines(row.left, row.right, width).map((text, j) => (
          <div key={`${i}-${j}`} className={styles.receiptText} style={row.strong ? { fontWeight: 700 } : undefined}>
            {text || "\u00a0"}
          </div>
        )),
      )}
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
