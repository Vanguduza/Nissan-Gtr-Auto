"use client";

import { Banknote, Building2, Car, CircleAlert, CreditCard, KeyRound, Layers, Lock, PackageCheck, Plus, Printer, RefreshCw, ScanLine, ShieldCheck, Smartphone, Trash2, X } from "lucide-react";
import { useEffect, useId, useMemo, useRef, useState, type ReactNode } from "react";
import { createPortal } from "react-dom";
import { formatMoney, roundMoney } from "@/lib/pos/money";
import { THERMAL_COLUMNS, thermalLines } from "@gtr/shared";
import { webReceiptRows } from "@/lib/pos/receipt";
import type { ContipayMethod, DigitalProvider, ManagerProof, ManualTender, ManualTenderLine, PaynowMethod, ReasonCode, ReceiptDocument } from "@/lib/pos/types";
import { reasonActionFor, type PosStore } from "@/lib/pos/use-pos";
import styles from "./pos.module.css";
import { SplitPanel } from "./SplitPayment";

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
  dismissible = true,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
  wide?: boolean;
  /** False while something must be answered first (money in flight): no close button, Escape or scrim. */
  dismissible?: boolean;
  /** Bottom sheet on every size (cart, vehicle picker on phones). Dialogs become sheets on Compact anyway. */
  sheet?: boolean;
}) {
  const layerRef = useRef<HTMLDivElement>(null);
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = dismissible ? onClose : () => undefined;
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
      if (e.target === e.currentTarget && dismissible) onClose();
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
          {dismissible ? (
            <button type="button" className={styles.iconButton} aria-label="Close" onClick={onClose}>
              <X size={18} aria-hidden />
            </button>
          ) : null}
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
  const [badge, setBadge] = useState("");
  const [via, setVia] = useState<"badge" | "password">("badge");
  const [notes, setNotes] = useState("");
  const [reasons, setReasons] = useState<ReasonCode[]>([]);
  const [reasonCode, setReasonCode] = useState("");
  const reasonAction = prompt ? reasonActionFor(prompt) : null;
  useEffect(() => {
    setReasonCode("");
    setBadge("");
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
  // Policy (`pos_action_requires_manager`) decides; a signed-in manager approves as themselves.
  const needsManager = pos.promptNeedsManager;
  const self = pos.selfApprover;
  const proofReady = !needsManager || (via === "badge" ? badge.trim().length > 0 : Boolean(identifier.trim() && password));
  const submit = async () => {
    if (reasonMissing) {
      pos.reportError(chosen?.requiresNotes && !notes.trim() ? "This reason needs a note." : "Choose a reason first.");
      setBadge("");
      return;
    }
    const proof: ManagerProof | null = !needsManager
      ? self
        ? { kind: "self" }
        : null
      : via === "badge"
        ? { kind: "badge", payload: badge.trim() }
        : { kind: "password", credentials: { identifier, password } };
    const ok = await pos.confirmManager(proof, notes.trim() || null, reasonCode || null);
    setBadge("");
    if (ok) {
      setPassword("");
      setNotes("");
    }
  };
  return (
    <Modal title={PROMPT_TITLE[prompt.kind]} onClose={pos.cancelManager}>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          void submit();
        }}
      >
        <p className={styles.muted}>
          <ShieldCheck size={14} aria-hidden /> {detail}{" "}
          {!needsManager
            ? self
              ? "You are signed in as an approver: this is approved under your name."
              : "Within your limit: no manager needed. The reason is recorded in the audit trail."
            : "An approver (any manager, or staff with the approval role) scans their ID badge, or signs in."}
        </p>
        {reasonAction ? (
          <label className={styles.field} style={{ marginTop: 12 }}>
            <span className={styles.fieldLabel}>Reason</span>
            <select className={styles.input} value={reasonCode} onChange={(e) => setReasonCode(e.target.value)} autoFocus>
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
            {reasonAction ? (chosen?.requiresNotes ? "Notes (required for this reason)" : "Notes (optional)") : "Notes (recorded in the audit trail)"}
          </span>
          <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>
        {needsManager ? (
          <>
            <div className={styles.segment} role="group" aria-label="How the manager approves" style={{ marginTop: 14 }}>
              {(
                [
                  ["badge", "Scan badge"],
                  ["password", "Manager password"],
                ] as const
              ).map(([id, label]) => (
                <button key={id} type="button" className={`${styles.segmentItem} ${via === id ? styles.segmentActive : ""}`} onClick={() => setVia(id)}>
                  {id === "badge" ? <ScanLine size={14} aria-hidden /> : <KeyRound size={14} aria-hidden />} {label}
                </button>
              ))}
            </div>
            {via === "badge" ? (
              <label className={styles.field} style={{ marginTop: 10 }}>
                <span className={styles.fieldLabel}>Approver ID badge</span>
                {/* USB / Bluetooth scanners type the badge and press Enter; never the browser camera. */}
                <input
                  className={styles.input}
                  type="password"
                  value={badge}
                  onChange={(e) => setBadge(e.target.value)}
                  placeholder={reasonMissing ? "Choose a reason, then scan" : "Scan the approver's badge now"}
                  autoComplete="off"
                  autoFocus={!reasonAction}
                  aria-describedby="badge-hint"
                />
                <span id="badge-hint" className={styles.muted}>
                  Hold the badge QR under the counter scanner. On the tablet the front camera reads it.
                </span>
              </label>
            ) : (
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
            )}
          </>
        ) : null}
        {pos.error ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{pos.error}</p> : null}
        <div className={styles.rowEnd}>
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={pos.cancelManager}>
            Cancel
          </button>
          <button type="submit" className={styles.primaryButton} disabled={pos.busy || !proofReady || reasonMissing}>
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

const MANUAL_TENDERS: Array<{ id: ManualTender; label: string }> = [
  { id: "cash", label: "Cash" },
  { id: "bank", label: "Card / bank" },
  { id: "store_credit", label: "Store credit" },
];

type PayMode = "manual" | DigitalProvider | "account" | "parts" | "terminal";

const PAYNOW_METHODS: Array<{ id: PaynowMethod; label: string }> = [
  { id: "ecocash", label: "EcoCash" },
  { id: "onemoney", label: "OneMoney" },
  { id: "innbucks", label: "InnBucks" },
  { id: "visa", label: "Visa / Mastercard" },
];
const CONTIPAY_METHODS: Array<{ id: ContipayMethod; label: string }> = [
  { id: "ecocash", label: "EcoCash" },
  { id: "visa", label: "Visa / Mastercard" },
  { id: "zimswitch", label: "ZimSwitch" },
];

function remainingLabel(expiresAt: string | null, now: number): string | null {
  const at = expiresAt ? Date.parse(expiresAt) : NaN;
  if (!Number.isFinite(at)) return null;
  const s = Math.max(0, Math.round((at - now) / 1000));
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
}

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
 * Payment (Blueprint §10.5–10.7). Opening it reserves the sale: stock is held and the sale is locked
 * until it is paid, the operator goes back to the sale, or the hold expires. The amount due is the
 * server's. Every tender is shown; one that cannot be used now is disabled with its reason.
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
  const co = pos.checkout;
  const receipt = pos.lastReceipt;
  const due = co ? roundMoney(co.status.total) : roundMoney(pos.subtotal);
  const currency = co?.status.currency ?? pos.currency;
  const [mode, setMode] = useState<PayMode>("manual");
  const [tenders, setTenders] = useState<ManualTenderLine[]>([{ tender: "cash", amount: due }]);
  const [cashGiven, setCashGiven] = useState("");
  const [email, setEmail] = useState("");
  const [whatsapp, setWhatsapp] = useState("");
  const [msisdn, setMsisdn] = useState("");
  const [method, setMethod] = useState<string>("ecocash");
  const [now, setNow] = useState(() => Date.now());
  const contacts = { email: email.trim() || null, whatsappE164: whatsapp.trim() || null, phoneE164: null };

  // Reserve on open (idempotent); keep the first tender at the server's total once it is known.
  useEffect(() => {
    if (!receipt && !co) void pos.beginCheckout(contacts);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  useEffect(() => {
    if (co) setTenders((cur) => (cur.length === 1 ? [{ ...cur[0]!, amount: roundMoney(co.status.total) }] : cur));
  }, [co?.status.total]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, []);

  const paid = roundMoney(tenders.reduce((sum, t) => sum + (Number.isFinite(t.amount) ? t.amount : 0), 0));
  const remaining = roundMoney(due - paid);
  const cashTender = tenders.filter((t) => t.tender === "cash").reduce((sum, t) => sum + t.amount, 0);
  const change = useMemo(() => {
    const given = Number(cashGiven);
    return Number.isFinite(given) && given > 0 ? roundMoney(given - cashTender) : null;
  }, [cashGiven, cashTender]);
  const update = (i: number, patch: Partial<ManualTenderLine>) => setTenders((cur) => cur.map((t, idx) => (idx === i ? { ...t, ...patch } : t)));

  if (receipt) {
    const close = () => {
      pos.clearReceipt();
      onClose();
    };
    return (
      <Modal title={`Sale complete · ${receipt.documentNumber ?? ""}`} onClose={close} wide>
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
        <div className={styles.rowEnd}>
          {pos.receiptOrderId ? (
            <>
              <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={close}>
                Customer collects later
              </button>
              <button
                type="button"
                className={styles.primaryButton}
                disabled={pos.busy}
                onClick={async () => {
                  if (await pos.collectOrder(pos.receiptOrderId!)) close();
                }}
              >
                <PackageCheck size={16} aria-hidden /> Handed over · New sale
              </button>
            </>
          ) : (
            <button type="button" className={styles.primaryButton} onClick={close}>
              New sale
            </button>
          )}
        </div>
      </Modal>
    );
  }

  const inFlight = Boolean(co?.attempt) && !co?.outcome;
  const split = pos.split;
  // Money already received on a part-paid sale: going back would need a refund, so it is a deliberate cancel.
  const splitLocked = Boolean(split && (split.locked > 0 || split.pending > 0));
  const choosing = Boolean(co) && !inFlight && co?.outcome !== "unknown" && !split;
  const back = async () => {
    if (split) {
      if (splitLocked) return;
      if (await pos.cancelSplit("Operator returned to the sale")) onClose();
      return;
    }
    if (await pos.cancelCheckout()) onClose();
  };
  const hold = remainingLabel(co?.status.reservationExpiresAt ?? null, now);
  const registered = Boolean(pos.cart?.customerId) && pos.cart?.customerName !== "POS Walk-in";
  // A customer suspended for failing to settle cannot buy on account (the server refuses it too).
  const [suspension, setSuspension] = useState<{ reason: string; owing: number } | null>(null);
  const customerId = registered ? pos.cart?.customerId ?? null : null;
  useEffect(() => {
    setSuspension(null);
    if (!customerId || !pos.online) return;
    let live = true;
    void pos.gateway.customerSuspension(customerId).then((r) => {
      if (live && r.ok) setSuspension(r.data);
    });
    return () => {
      live = false;
    };
  }, [customerId, pos.gateway, pos.online]);
  const providerReason = (p: DigitalProvider): string | null => {
    if (!pos.online) return "Needs a connection.";
    if (!pos.providers) return "Checking…";
    return pos.providers[p];
  };
  const options: Array<{ id: PayMode; label: string; icon: ReactNode; reason: string | null }> = [
    { id: "manual", label: "Cash · card · store credit", icon: <Banknote size={16} aria-hidden />, reason: null },
    // Card machines answer to the paired counter tablet, which signs their results; a browser cannot.
    { id: "terminal", label: "Card machine", icon: <CreditCard size={16} aria-hidden />, reason: "Use the counter tablet paired with the card machine." },
    { id: "ecocash", label: "EcoCash", icon: <Smartphone size={16} aria-hidden />, reason: providerReason("ecocash") },
    { id: "paynow", label: "Paynow", icon: <Smartphone size={16} aria-hidden />, reason: providerReason("paynow") },
    { id: "contipay", label: "ContiPay", icon: <Smartphone size={16} aria-hidden />, reason: providerReason("contipay") },
    { id: "account", label: "On account", icon: <Building2 size={16} aria-hidden />, reason: !registered
        ? "Choose a registered customer with credit first."
        : suspension
          ? `Account suspended (${suspension.reason}); owing ${formatMoney(suspension.owing, currency)}. Take payment now, or a manager lifts it in Customer credit.`
          : null },
    { id: "parts", label: "Pay in parts", icon: <Layers size={16} aria-hidden />, reason: pos.online ? null : "Needs a connection." },
  ];

  return (
    <Modal title="Payment" onClose={() => void back()} dismissible={!inFlight && co?.outcome !== "unknown" && !splitLocked} wide>
      <div className={styles.dueRow}>
        <span>{split ? "Sale total" : "Amount due"}</span>
        <strong style={{ fontSize: 22 }}>{co ? formatMoney(due, currency) : "Reserving…"}</strong>
      </div>
      {co ? (
        <p className={styles.muted} style={{ marginTop: 6 }}>
          <Lock size={12} aria-hidden /> Stock is held for this sale{hold ? ` for ${hold}` : ""}. The sale cannot change while you take payment.
        </p>
      ) : null}

      {co?.outcome === "unknown" ? (
        <div className={`${styles.statusBanner} ${styles.statusError}`} role="alert" style={{ marginTop: 12 }}>
          <CircleAlert size={16} aria-hidden />
          <span>{co.message ?? "We cannot prove whether the money moved. Do not take this payment again."}</span>
          <button
            type="button"
            className={`${styles.primaryButton} ${styles.statusDismiss}`}
            onClick={() => {
              pos.openRecovery(co.orderId);
              onClose();
            }}
          >
            Resolve payment
          </button>
        </div>
      ) : null}

      {choosing ? (
        <div className={styles.tenderGrid} role="radiogroup" aria-label="Payment method">
          {options.map((o) => (
            <button
              key={o.id}
              type="button"
              role="radio"
              aria-checked={mode === o.id}
              className={`${styles.tenderCard} ${mode === o.id ? styles.tenderCardActive : ""}`}
              disabled={Boolean(o.reason)}
              onClick={() => setMode(o.id)}
            >
              <span className={styles.listTitle}>
                {o.icon} {o.label}
              </span>
              {o.reason ? <span className={styles.muted}>{o.reason}</span> : null}
            </button>
          ))}
        </div>
      ) : null}

      {co && co.outcome && co.outcome !== "unknown" && co.message ? (
        <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert" style={{ marginTop: 12 }}>
          {co.outcome === "declined" ? "Declined: " : co.outcome === "cancelled" ? "Cancelled: " : ""}
          {co.message}
        </p>
      ) : null}

      {co && inFlight && co.attempt ? (
        <div className={styles.dueRow} role="status" style={{ flexDirection: "column", alignItems: "flex-start", gap: 6 }}>
          <strong>
            Waiting for {co.attempt.provider === "ecocash" ? "the customer to approve on their phone" : "the customer to pay"} ·{" "}
            {Math.floor((now - co.attempt.startedAt) / 1000)} s
          </strong>
          {co.message ? <span className={styles.muted}>{co.message}</span> : null}
          {co.attempt.checkoutUrl ? (
            <span className={styles.muted}>
              Payment page for the customer:{" "}
              <a href={co.attempt.checkoutUrl} target="_blank" rel="noreferrer">
                {co.attempt.checkoutUrl}
              </a>
            </span>
          ) : null}
          <span className={styles.muted}>The sale completes on its own when the provider confirms. Do not take another payment meanwhile.</span>
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => void pos.refreshCheckout()}>
            <RefreshCw size={14} aria-hidden /> Check now
          </button>
        </div>
      ) : null}

      {choosing && mode === "manual" ? (
        <>
          {tenders.map((t, i) => (
            <div key={i} className={styles.tenderRow}>
              <select className={styles.input} value={t.tender} onChange={(e) => update(i, { tender: e.target.value as ManualTender })} aria-label="Tender">
                {MANUAL_TENDERS.map((x) => (
                  <option key={x.id} value={x.id}>
                    {x.label}
                  </option>
                ))}
              </select>
              <input className={styles.input} type="number" min={0} step="0.01" value={Number.isFinite(t.amount) ? t.amount : ""} onChange={(e) => update(i, { amount: Number(e.target.value) })} aria-label="Amount" />
              <button type="button" className={styles.iconButton} aria-label="Remove tender" disabled={tenders.length === 1} onClick={() => setTenders((cur) => cur.filter((_, idx) => idx !== i))}>
                <Trash2 size={16} aria-hidden />
              </button>
            </div>
          ))}
          <div className={styles.row} style={{ marginTop: 10 }}>
            <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setTenders((cur) => [...cur, { tender: "bank", amount: Math.max(0, remaining) }])}>
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
        </>
      ) : null}

      {choosing && (mode === "ecocash" || mode === "paynow" || mode === "contipay") ? (
        <div className={styles.formGrid}>
          {mode !== "ecocash" ? (
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Method</span>
              <select className={styles.input} value={method} onChange={(e) => setMethod(e.target.value)}>
                {(mode === "paynow" ? PAYNOW_METHODS : CONTIPAY_METHODS).map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.label}
                  </option>
                ))}
              </select>
            </label>
          ) : null}
          <label className={styles.field}>
            <span className={styles.fieldLabel}>{mode === "ecocash" ? "Customer EcoCash number" : mode === "contipay" ? "Customer phone" : "Customer phone (optional)"}</span>
            <input className={styles.input} value={msisdn} onChange={(e) => setMsisdn(e.target.value)} placeholder="0771 234 567" inputMode="tel" />
          </label>
        </div>
      ) : null}

      {choosing && mode === "parts" ? (
        <p className={styles.muted} style={{ marginTop: 12 }}>
          Take the {formatMoney(due, currency)} in parts — for example some cash now and the rest by card, or store credit plus cash. Each part
          is recorded as it is taken; the sale posts when the parts cover it. If the customer cannot pay the rest, you can finish with only
          the items already paid for.
        </p>
      ) : null}

      {co && split && !inFlight ? <SplitPanel pos={pos} onCancelled={onClose} /> : null}

      {choosing && mode === "account" ? (
        <p className={styles.muted} style={{ marginTop: 12 }}>
          Charge {formatMoney(due, currency)} to {pos.cart?.customerName}&apos;s account. The server checks the credit limit, any credit hold and the account currency.
        </p>
      ) : null}

      {choosing ? (
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
      ) : null}

      {pos.error ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{pos.error}</p> : null}
      <div className={styles.rowEnd}>
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} disabled={inFlight || pos.busy || co?.outcome === "unknown" || splitLocked}
          title={splitLocked ? "Money has been received on this sale. Use Cancel part payments to stop it." : undefined}
          onClick={() => void back()}
        >
          Back to sale
        </button>
        {choosing && mode === "manual" ? (
          <button
            type="button"
            className={styles.primaryButton}
            disabled={pos.busy || remaining !== 0 || tenders.some((t) => !(t.amount > 0)) || (change != null && change < 0)}
            onClick={() => void pos.payManual(tenders.map((t) => ({ ...t, amount: roundMoney(t.amount) })), contacts)}
          >
            {!co?.outcome || co.outcome === "error" ? "Take payment" : "Retry payment"} · {formatMoney(due, currency)}
          </button>
        ) : null}
        {choosing && (mode === "ecocash" || mode === "paynow" || mode === "contipay") ? (
          <button
            type="button"
            className={styles.primaryButton}
            disabled={pos.busy || ((mode === "ecocash" || mode === "contipay") && !msisdn.trim())}
            onClick={() =>
              void pos.payProvider(mode, { msisdn: msisdn.trim() || undefined, method: mode === "ecocash" ? undefined : (method as PaynowMethod | ContipayMethod) }, contacts)
            }
          >
            {mode === "ecocash" ? "Send PIN request" : "Create payment page"} · {formatMoney(due, currency)}
          </button>
        ) : null}
        {choosing && mode === "account" ? (
          <button type="button" className={styles.primaryButton} disabled={pos.busy} onClick={() => void pos.payOnAccount(contacts)}>
            Charge to account · {formatMoney(due, currency)}
          </button>
        ) : null}
        {choosing && mode === "parts" ? (
          <button type="button" className={styles.primaryButton} disabled={pos.busy} onClick={() => void pos.startSplit()}>
            <Layers size={16} aria-hidden /> Start part payments
          </button>
        ) : null}
      </div>
    </Modal>
  );
}
