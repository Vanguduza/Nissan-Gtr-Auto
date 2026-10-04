"use client";

import { ArrowLeft, PackageCheck, Recycle, Search, ShieldAlert, Undo2, Wrench } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { formatMoney, roundMoney } from "@/lib/pos/money";
import { RETURN_CONDITIONS, RETURN_RESOLUTIONS, WARRANTY_RESOLUTION_LABEL, WARRANTY_STATUS_LABEL, warrantyApproveArgs } from "@/lib/pos/returns";
import type {
  CoreReturnResolution,
  InvoiceDetail,
  InvoiceDetailLine,
  ManagerProof,
  ReasonCode,
  RecentInvoice,
  ReturnCondition,
  ReturnResolution,
  WarrantyClaim,
  WarrantyDecision,
  WarrantySerial,
} from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import { ApproverProofFields } from "./SplitPayment";
import styles from "./pos.module.css";

const when = (iso: string | null) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" }) : null);

function useReasons(pos: PosStore, action: string): ReasonCode[] {
  const [reasons, setReasons] = useState<ReasonCode[]>([]);
  useEffect(() => {
    let live = true;
    void pos.gateway.listReasons(action).then((res) => live && res.ok && setReasons(res.data));
    return () => {
      live = false;
    };
  }, [pos.gateway, action]);
  return reasons;
}

/**
 * Returns (Blueprint §10, phase 6): find the sale, take back what can still come back, and give the
 * customer the right outcome. A sales person prepares it; an approver (badge, password or signed in)
 * posts it. Old cores and warranty claims are handled from the same sale.
 */
export function ReturnsScreen({ pos }: { pos: PosStore }) {
  const [tab, setTab] = useState<"sales" | "warranty">("sales");
  const [sale, setSale] = useState<RecentInvoice | null>(null);
  return (
    <section className={styles.panel}>
      <div className={styles.sectionHead}>
        <h2 className={styles.panelTitle}>Returns</h2>
        <div className={styles.segment} role="tablist" aria-label="Returns view">
          {(
            [
              ["sales", "Sales"],
              ["warranty", "Warranty claims"],
            ] as const
          ).map(([k, label]) => (
            <button
              key={k}
              type="button"
              role="tab"
              aria-selected={tab === k}
              className={`${styles.segmentItem} ${tab === k ? styles.segmentActive : ""}`}
              onClick={() => setTab(k)}
            >
              {label}
            </button>
          ))}
        </div>
      </div>
      {tab === "warranty" ? (
        <WarrantyClaims pos={pos} />
      ) : sale ? (
        <SaleReturn pos={pos} sale={sale} onBack={() => setSale(null)} />
      ) : (
        <SaleSearch pos={pos} onOpen={setSale} />
      )}
    </section>
  );
}

function SaleSearch({ pos, onOpen }: { pos: PosStore; onOpen: (sale: RecentInvoice) => void }) {
  const [query, setQuery] = useState("");
  const [rows, setRows] = useState<RecentInvoice[]>([]);
  const load = useCallback(async () => {
    const res = await pos.gateway.listRecentInvoices(query);
    if (res.ok) setRows(res.data);
  }, [pos.gateway, query]);
  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pos.notice]);
  return (
    <>
      <p className={styles.muted}>Open the sale the customer brings back. Only what is still returnable can come back.</p>
      <form
        className={styles.row}
        style={{ marginTop: 12 }}
        onSubmit={(e) => {
          e.preventDefault();
          void load();
        }}
      >
        <input className={styles.input} style={{ flex: 1 }} placeholder="Invoice number or customer" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search sales" />
        <button type="submit" className={`${styles.softButton} ${styles.inlineButton}`}>
          <Search size={16} aria-hidden /> Search
        </button>
      </form>
      <div className={styles.list}>
        {rows.length === 0 ? <div className={styles.emptyCard}>No completed sales found.</div> : null}
        {rows.map((r) => (
          <button key={r.id} type="button" className={`${styles.listRow} ${styles.focusable}`} style={{ width: "100%", textAlign: "left" }} onClick={() => onOpen(r)}>
            <span>
              <div className={styles.listTitle}>{r.documentNumber ?? r.id}</div>
              <div className={styles.muted}>{[r.customerName ?? "Walk-in", formatMoney(r.total, r.currency), r.vehicleLabel, when(r.postedAt)].filter(Boolean).join(" · ")}</div>
            </span>
            <span className={styles.badge}>Open</span>
          </button>
        ))}
      </div>
    </>
  );
}

type Pick = { qty: number; condition: ReturnCondition };

function SaleReturn({ pos, sale, onBack }: { pos: PosStore; sale: RecentInvoice; onBack: () => void }) {
  const invoiceId = sale.id;
  const [detail, setDetail] = useState<InvoiceDetail | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [picks, setPicks] = useState<Record<string, Pick>>({});
  const [warrantyLine, setWarrantyLine] = useState<InvoiceDetailLine | null>(null);
  const reload = useCallback(async () => {
    const res = await pos.gateway.getInvoiceDetail(invoiceId);
    if (res.ok) {
      setDetail(res.data);
      setLoadError(null);
    } else setLoadError(res.error);
  }, [pos.gateway, invoiceId]);
  useEffect(() => {
    void reload();
  }, [reload]);

  if (!detail) {
    return (
      <>
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onBack} style={{ marginTop: 12 }}>
          <ArrowLeft size={16} aria-hidden /> Sales
        </button>
        <div className={styles.emptyCard}>{loadError ?? "Loading the sale…"}</div>
      </>
    );
  }
  const parts = detail.lines.filter((l) => !l.isCore);
  const cores = detail.lines.filter((l) => l.isCore);
  const picked = parts.filter((l) => (picks[l.id]?.qty ?? 0) > 0);
  // A whole-sale refund only while nothing has come back yet: otherwise it would pay twice.
  const untouched = parts.length > 0 && parts.every((l) => l.returnableQty >= l.qty) && cores.every((l) => l.returnableQty >= l.qty);
  return (
    <>
      <div className={styles.row} style={{ marginTop: 12, justifyContent: "space-between", flexWrap: "wrap" }}>
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onBack}>
          <ArrowLeft size={16} aria-hidden /> Sales
        </button>
        <button
          type="button"
          className={styles.dangerButton}
          disabled={!untouched}
          title={untouched ? undefined : "Part of this sale was already returned: return the rest line by line"}
          onClick={() => pos.requestManager({ kind: "refund", invoiceId: detail.id, documentNumber: detail.documentNumber })}
        >
          <Undo2 size={16} aria-hidden /> Refund whole sale
        </button>
      </div>
      <dl className={styles.facts}>
        <dt>Sale</dt>
        <dd>{detail.documentNumber ?? detail.id}</dd>
        <dt>Customer</dt>
        <dd>{sale.customerName ?? (detail.customerId ? "Named customer" : "Walk-in")}</dd>
        {sale.vehicleLabel ? (
          <>
            <dt>Vehicle</dt>
            <dd>{sale.vehicleLabel}</dd>
          </>
        ) : null}
        <dt>Paid</dt>
        <dd>
          {formatMoney(detail.amountPaid, detail.currency)} of {formatMoney(detail.total, detail.currency)}
        </dd>
        <dt>Date</dt>
        <dd>{when(detail.postedAt)}</dd>
      </dl>

      <h3 className={styles.sectionTitle} style={{ marginTop: 18 }}>
        Parts on this sale
      </h3>
      <div className={styles.list}>
        {parts.map((l) => {
          const p = picks[l.id] ?? { qty: 0, condition: "opened" as ReturnCondition };
          const set = (next: Partial<Pick>) => setPicks((all) => ({ ...all, [l.id]: { ...p, ...next } }));
          return (
            <div key={l.id} className={styles.listRow} style={{ flexWrap: "wrap", gap: 10 }}>
              <span style={{ flex: "1 1 220px" }}>
                <div className={styles.listTitle}>{l.description ?? l.partNumber}</div>
                <div className={styles.muted}>
                  {[l.partNumber, `sold ${l.qty}`, `${formatMoney(l.unitPrice, detail.currency)} each`, l.returnableQty < l.qty ? `${l.returnableQty} can come back` : null].filter(Boolean).join(" · ")}
                </div>
              </span>
              {l.returnableQty > 0 ? (
                <>
                  <div className={styles.stepper} aria-label={`Return quantity for ${l.partNumber}`}>
                    <button type="button" className={styles.stepperButton} aria-label="Less" disabled={p.qty <= 0} onClick={() => set({ qty: Math.max(0, p.qty - 1) })}>
                      −
                    </button>
                    <span className={styles.stepperValue}>{p.qty}</span>
                    <button
                      type="button"
                      className={`${styles.stepperButton} ${styles.stepperPlus}`}
                      aria-label="More"
                      disabled={p.qty >= l.returnableQty}
                      onClick={() => set({ qty: Math.min(l.returnableQty, p.qty + 1) })}
                    >
                      +
                    </button>
                  </div>
                  <select className={styles.input} style={{ width: 140 }} value={p.condition} onChange={(e) => set({ condition: e.target.value as ReturnCondition })} aria-label="Condition">
                    {RETURN_CONDITIONS.map((c) => (
                      <option key={c.value} value={c.value}>
                        {c.label}
                      </option>
                    ))}
                  </select>
                  <button type="button" className={styles.linkButton} onClick={() => setWarrantyLine(l)}>
                    Warranty claim
                  </button>
                </>
              ) : (
                <span className={styles.badge}>Returned</span>
              )}
            </div>
          );
        })}
      </div>

      {picked.length > 0 ? (
        <ReturnForm
          pos={pos}
          detail={detail}
          lines={picked.map((l) => ({ line: l, ...picks[l.id] }))}
          onDone={() => {
            setPicks({});
            void reload();
          }}
        />
      ) : null}

      {cores.length > 0 ? (
        <>
          <h3 className={styles.sectionTitle} style={{ marginTop: 18 }}>
            Old cores
          </h3>
          <p className={styles.muted}>The customer brings back the old unit and gets the core charge back.</p>
          <div className={styles.list}>
            {cores.map((l) => (
              <CoreRow key={l.id} pos={pos} detail={detail} line={l} onDone={reload} />
            ))}
          </div>
        </>
      ) : null}

      {warrantyLine ? <OpenClaim pos={pos} detail={detail} line={warrantyLine} onClose={() => setWarrantyLine(null)} /> : null}
    </>
  );
}

/** Why a return outcome cannot be chosen for this sale, if anything. */
function resolutionBlock(r: ReturnResolution, detail: InvoiceDetail, lineCount: number, tillOpen: boolean): string | null {
  if ((r === "credit_note" || r === "store_credit") && !detail.customerId) return "Needs a named customer on the sale";
  if (r === "cash_refund" && !tillOpen && !detail.tillSessionId) return "Open the till first";
  if (r === "cash_refund" && detail.amountPaid <= 0) return "Nothing was paid on this sale";
  if (r === "warranty" && lineCount !== 1) return "One part per warranty claim";
  return null;
}

function ReturnForm({
  pos,
  detail,
  lines,
  onDone,
}: {
  pos: PosStore;
  detail: InvoiceDetail;
  lines: { line: InvoiceDetailLine; qty: number; condition: ReturnCondition }[];
  onDone: () => void;
}) {
  const reasons = useReasons(pos, "return_post");
  const [resolution, setResolution] = useState<ReturnResolution | null>(null);
  const [reasonCode, setReasonCode] = useState("");
  const [notes, setNotes] = useState("");
  const [proof, setProof] = useState<ManagerProof | null>(null);
  const [working, setWorking] = useState(false);
  /** A drafted case the approver did not post yet: posting again reuses it (no second draft). */
  const [draft, setDraft] = useState<{ id: string; key: string } | null>(null);
  const tillOpen = Boolean(pos.till && pos.till.status !== "closed");
  const value = roundMoney(lines.reduce((sum, l) => sum + l.qty * (l.line.qty > 0 ? l.line.lineTotal / l.line.qty : l.line.unitPrice), 0));
  const key = JSON.stringify([resolution, reasonCode, notes.trim(), lines.map((l) => [l.line.id, l.qty, l.condition])]);
  const reason = reasons.find((r) => r.code === reasonCode);
  const blocked = resolution ? resolutionBlock(resolution, detail, lines.length, tillOpen) : null;
  const ready = resolution && reasonCode && !blocked && (!reason?.requiresNotes || notes.trim()) && (pos.selfApprover || proof);

  const submit = async () => {
    if (!resolution) return;
    setWorking(true);
    try {
      let caseId = draft?.key === key ? draft.id : null;
      if (!caseId) {
        const res = await pos.gateway.createReturnCase({
          invoiceId: detail.id,
          resolution,
          reasonCode,
          notes: notes.trim() || null,
          lines: lines.map((l) => ({ invoiceLineId: l.line.id, qty: l.qty, condition: l.condition })),
          replacementLines: resolution === "replacement" ? lines.map((l) => ({ stockItemId: l.line.stockItemId, uomId: l.line.uomId, qty: l.qty })) : null,
          tillSessionId: pos.till && pos.till.status !== "closed" ? pos.till.id : null,
        });
        if (!res.ok) {
          pos.showError(res.error);
          return;
        }
        caseId = res.data;
        setDraft({ id: caseId, key });
      }
      const id = caseId;
      const posted = await pos.runApproved(proof, { action: "return_post", args: { return_case_id: id } }, (m) => pos.gateway.postReturnCase(id, m));
      if (!posted) return;
      setDraft(null);
      pos.showNotice(
        resolution === "cash_refund"
          ? `Return posted. Pay ${formatMoney(value, detail.currency)} from the till.`
          : resolution === "warranty"
            ? "Return posted and a warranty claim opened. Decide it under Warranty claims."
            : resolution === "replacement"
              ? "Return posted. Hand over the replacement part."
              : "Return posted.",
      );
      onDone();
    } finally {
      setWorking(false);
    }
  };

  return (
    <div className={styles.panelInset} style={{ marginTop: 16 }}>
      <h3 className={styles.sectionTitle} style={{ margin: 0 }}>
        Return {lines.reduce((n, l) => n + l.qty, 0)} item(s) · about {formatMoney(value, detail.currency)}
      </h3>
      <p className={styles.muted}>The final value comes from the sale, discounts included.</p>
      <div className={styles.tenderGrid} role="radiogroup" aria-label="What the customer gets" style={{ marginTop: 12 }}>
        {RETURN_RESOLUTIONS.map((r) => {
          const why = resolutionBlock(r.value, detail, lines.length, tillOpen);
          return (
            <button
              key={r.value}
              type="button"
              role="radio"
              aria-checked={resolution === r.value}
              disabled={Boolean(why)}
              className={`${styles.tenderCard} ${resolution === r.value ? styles.tenderCardActive : ""}`}
              onClick={() => setResolution(r.value)}
              title={why ?? r.hint}
            >
              <strong>{r.label}</strong>
              <span className={styles.muted}>{why ?? r.hint}</span>
            </button>
          );
        })}
      </div>
      <div className={styles.formGrid} style={{ marginTop: 12 }}>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>Reason</span>
          <select className={styles.input} value={reasonCode} onChange={(e) => setReasonCode(e.target.value)}>
            <option value="">Choose…</option>
            {reasons.map((r) => (
              <option key={r.code} value={r.code}>
                {r.label}
              </option>
            ))}
          </select>
        </label>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>Notes{reason?.requiresNotes ? " (required)" : ""}</span>
          <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>
      </div>
      <div style={{ marginTop: 12 }}>
        <ApproverProofFields pos={pos} onProof={setProof} />
      </div>
      {draft ? <p className={styles.muted}>Drafted and waiting for an approver. Posting again uses the same return.</p> : null}
      <div className={styles.rowEnd}>
        <button type="button" className={styles.primaryButton} disabled={!ready || working || !pos.online} onClick={() => void submit()}>
          <PackageCheck size={16} aria-hidden /> {working ? "Posting…" : "Post return"}
        </button>
      </div>
    </div>
  );
}

function CoreRow({ pos, detail, line, onDone }: { pos: PosStore; detail: InvoiceDetail; line: InvoiceDetailLine; onDone: () => void }) {
  const reasons = useReasons(pos, "core_return");
  const [open, setOpen] = useState(false);
  const [qty, setQty] = useState(1);
  const [resolution, setResolution] = useState<CoreReturnResolution>(detail.customerId ? "account_credit" : "cash_refund");
  const [reasonCode, setReasonCode] = useState("");
  const [notes, setNotes] = useState("");
  const [proof, setProof] = useState<ManagerProof | null>(null);
  const [working, setWorking] = useState(false);
  const tillId = pos.till && pos.till.status !== "closed" ? pos.till.id : null;
  const options: { value: CoreReturnResolution; label: string; why: string | null }[] = [
    { value: "cash_refund", label: "Cash from till", why: !tillId && !detail.tillSessionId ? "Open the till first" : null },
    { value: "account_credit", label: "Credit to account", why: detail.customerId ? null : "Needs a named customer" },
    { value: "store_credit", label: "Store credit", why: detail.customerId ? null : "Needs a named customer" },
  ];
  const submit = async () => {
    setWorking(true);
    try {
      const input = { invoiceId: detail.id, coreLineId: line.id, qty, resolution, reasonCode, tillSessionId: tillId, notes: notes.trim() || null };
      const okDone = await pos.runApproved(
        proof,
        {
          action: "core_return",
          args: { invoice_id: detail.id, core_line_id: line.id, qty, resolution, reason_code: reasonCode, till_session_id: tillId, notes: input.notes },
        },
        (m) => pos.gateway.postCoreReturn(input, m),
      );
      if (!okDone) return;
      pos.showNotice(`Core taken back: ${formatMoney(roundMoney(qty * line.unitPrice), detail.currency)} ${resolution === "cash_refund" ? "to pay from the till" : "credited"}.`);
      setOpen(false);
      onDone();
    } finally {
      setWorking(false);
    }
  };
  return (
    <div className={styles.listRow} style={{ flexWrap: "wrap" }}>
      <span style={{ flex: "1 1 220px" }}>
        <div className={styles.listTitle}>{line.description ?? line.partNumber}</div>
        <div className={styles.muted}>
          {[line.partNumber, `${formatMoney(line.unitPrice, detail.currency)} per core`, `${line.returnableQty} of ${line.qty} still due back`].join(" · ")}
        </div>
      </span>
      {line.returnableQty <= 0 ? (
        <span className={styles.badge}>Core returned</span>
      ) : !open ? (
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setOpen(true)}>
          <Recycle size={16} aria-hidden /> Take back core
        </button>
      ) : (
        <div className={styles.panelInset} style={{ flex: "1 1 100%" }}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Cores</span>
              <input className={styles.input} type="number" min={1} max={line.returnableQty} step={1} value={qty} onChange={(e) => setQty(Math.max(1, Math.min(line.returnableQty, Number(e.target.value) || 1)))} />
            </label>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Give back as</span>
              <select className={styles.input} value={resolution} onChange={(e) => setResolution(e.target.value as CoreReturnResolution)}>
                {options.map((o) => (
                  <option key={o.value} value={o.value} disabled={Boolean(o.why)}>
                    {o.label}
                    {o.why ? ` (${o.why})` : ""}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Reason</span>
              <select className={styles.input} value={reasonCode} onChange={(e) => setReasonCode(e.target.value)}>
                <option value="">Choose…</option>
                {reasons.map((r) => (
                  <option key={r.code} value={r.code}>
                    {r.label}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Notes</span>
              <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} />
            </label>
          </div>
          <div style={{ marginTop: 12 }}>
        <ApproverProofFields pos={pos} onProof={setProof} />
      </div>
          <div className={styles.rowEnd}>
            <button type="button" className={styles.softButton} onClick={() => setOpen(false)}>
              Cancel
            </button>
            <button
              type="button"
              className={styles.primaryButton}
              disabled={working || !reasonCode || Boolean(options.find((o) => o.value === resolution)?.why) || !(pos.selfApprover || proof) || !pos.online}
              onClick={() => void submit()}
            >
              {working ? "Posting…" : `Give back ${formatMoney(roundMoney(qty * line.unitPrice), detail.currency)}`}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

/** Open a warranty claim on one sold part (sales staff); a serial number ties it to the exact unit. */
function OpenClaim({ pos, detail, line, onClose }: { pos: PosStore; detail: InvoiceDetail; line: InvoiceDetailLine; onClose: () => void }) {
  const [serial, setSerial] = useState("");
  const [found, setFound] = useState<WarrantySerial | null>(null);
  const [lookup, setLookup] = useState<string | null>(null);
  const [notes, setNotes] = useState("");
  const [working, setWorking] = useState(false);
  const find = async () => {
    const res = await pos.gateway.findWarrantySerial(serial);
    if (!res.ok) return setLookup(res.error);
    const match = res.data.find((s) => s.stockItemId === line.stockItemId) ?? null;
    setFound(match);
    setLookup(match ? null : res.data.length ? "That serial belongs to a different part." : "Serial number not found.");
  };
  const submit = async () => {
    setWorking(true);
    const res = await pos.gateway.openWarrantyClaim(detail.id, line.id, found?.id ?? null, notes.trim() || null);
    setWorking(false);
    if (!res.ok) return pos.showError(res.error);
    pos.showNotice("Warranty claim opened. A manager decides it under Warranty claims.");
    onClose();
  };
  return (
    <div className={styles.panelInset} style={{ marginTop: 16 }}>
      <h3 className={styles.sectionTitle} style={{ margin: 0 }}>
        <ShieldAlert size={16} aria-hidden /> Warranty claim · {line.description ?? line.partNumber}
      </h3>
      <div className={styles.formGrid} style={{ marginTop: 10 }}>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>Serial number (if the part has one)</span>
          <span className={styles.row}>
            <input
              className={styles.input}
              style={{ flex: 1 }}
              value={serial}
              onChange={(e) => {
                setSerial(e.target.value);
                setFound(null);
                setLookup(null);
              }}
            />
            <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} disabled={!serial.trim()} onClick={() => void find()}>
              Check
            </button>
          </span>
        </label>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>What is wrong</span>
          <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>
      </div>
      {found ? <p className={styles.muted}>Serial {found.serialNumber} matches this part.</p> : null}
      {lookup ? <p className={styles.muted}>{lookup}</p> : null}
      <div className={styles.rowEnd}>
        <button type="button" className={styles.softButton} onClick={onClose}>
          Cancel
        </button>
        <button type="button" className={styles.primaryButton} disabled={working || !notes.trim() || (Boolean(serial.trim()) && !found) || !pos.online} onClick={() => void submit()}>
          <Wrench size={16} aria-hidden /> Open claim
        </button>
      </div>
    </div>
  );
}

const STATUS_FILTERS: { value: string | null; label: string }[] = [
  { value: "open", label: "Waiting" },
  { value: "approved", label: "Approved" },
  { value: "rejected", label: "Rejected" },
  { value: "closed", label: "Closed" },
  { value: null, label: "All" },
];

function WarrantyClaims({ pos }: { pos: PosStore }) {
  const [status, setStatus] = useState<string | null>("open");
  const [query, setQuery] = useState("");
  const [rows, setRows] = useState<WarrantyClaim[]>([]);
  const [deciding, setDeciding] = useState<WarrantyClaim | null>(null);
  const load = useCallback(async () => {
    const res = await pos.gateway.listWarrantyClaims(query, status);
    if (res.ok) setRows(res.data);
    else pos.showError(res.error);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [pos.gateway, query, status]);
  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status, pos.notice]);
  const close = async (c: WarrantyClaim) => {
    const res = await pos.gateway.closeWarrantyClaim(c.id);
    if (!res.ok) return pos.showError(res.error);
    pos.showNotice(`${c.documentNumber ?? "Claim"} closed.`);
    void load();
  };
  return (
    <>
      <div className={styles.row} style={{ marginTop: 12, flexWrap: "wrap" }}>
        <div className={styles.segment} aria-label="Claim status">
          {STATUS_FILTERS.map((f) => (
            <button key={f.label} type="button" className={`${styles.segmentItem} ${status === f.value ? styles.segmentActive : ""}`} onClick={() => setStatus(f.value)}>
              {f.label}
            </button>
          ))}
        </div>
        <form
          className={styles.row}
          style={{ flex: 1, minWidth: 220 }}
          onSubmit={(e) => {
            e.preventDefault();
            void load();
          }}
        >
          <input className={styles.input} style={{ flex: 1 }} placeholder="Claim, invoice, part or serial" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search claims" />
          <button type="submit" className={`${styles.softButton} ${styles.inlineButton}`}>
            <Search size={16} aria-hidden /> Search
          </button>
        </form>
      </div>
      <div className={styles.list}>
        {rows.length === 0 ? <div className={styles.emptyCard}>No warranty claims here.</div> : null}
        {rows.map((c) => (
          <div key={c.id} className={styles.listRow} style={{ flexWrap: "wrap" }}>
            <span style={{ flex: "1 1 240px" }}>
              <div className={styles.listTitle}>
                {c.documentNumber ?? c.id} · {c.partNumber ?? "part"}
                {c.serialNumber ? ` · S/N ${c.serialNumber}` : ""}
              </div>
              <div className={styles.muted}>
                {[
                  WARRANTY_STATUS_LABEL[c.status] ?? c.status,
                  c.resolution ? WARRANTY_RESOLUTION_LABEL[c.resolution] ?? c.resolution : null,
                  c.invoiceNumber,
                  c.notes,
                  c.rejectReason ? `Rejected: ${c.rejectReason}` : null,
                  when(c.decidedAt ?? c.createdAt),
                ]
                  .filter(Boolean)
                  .join(" · ")}
              </div>
            </span>
            {c.status === "open" ? (
              <button type="button" className={styles.primaryButton} onClick={() => setDeciding(c)}>
                Decide
              </button>
            ) : c.status === "approved" || c.status === "rejected" ? (
              <button type="button" className={styles.softButton} onClick={() => void close(c)}>
                Close claim
              </button>
            ) : null}
          </div>
        ))}
      </div>
      {deciding ? (
        <WarrantyDecide
          pos={pos}
          claim={deciding}
          onClose={() => setDeciding(null)}
          onDone={() => {
            setDeciding(null);
            void load();
          }}
        />
      ) : null}
    </>
  );
}

const DECISIONS: { value: "replacement" | "credit_note" | "return_only" | "reject"; label: string; hint: string }[] = [
  { value: "replacement", label: "Replace the part", hint: "Faulty part to quarantine, same part handed over." },
  { value: "credit_note", label: "Credit the customer", hint: "Credited at the price it was sold for. Named customer only." },
  { value: "return_only", label: "Take back, no credit", hint: "Faulty part goes to quarantine; nothing is credited." },
  { value: "reject", label: "Reject the claim", hint: "The customer keeps the part. Give the reason." },
];

function WarrantyDecide({ pos, claim, onClose, onDone }: { pos: PosStore; claim: WarrantyClaim; onClose: () => void; onDone: () => void }) {
  const [choice, setChoice] = useState<(typeof DECISIONS)[number]["value"] | null>(null);
  const [qty, setQty] = useState(1);
  const [reason, setReason] = useState("");
  const [proof, setProof] = useState<ManagerProof | null>(null);
  const [working, setWorking] = useState(false);
  const [sale, setSale] = useState<InvoiceDetail | null>(null);
  useEffect(() => {
    if (claim.invoiceId) void pos.gateway.getInvoiceDetail(claim.invoiceId).then((res) => res.ok && setSale(res.data));
  }, [pos.gateway, claim.invoiceId]);
  const soldLine = useMemo(() => sale?.lines.find((l) => !l.isCore && l.stockItemId === claim.stockItemId) ?? null, [sale, claim.stockItemId]);
  const why = (v: (typeof DECISIONS)[number]["value"]): string | null => {
    if (v === "credit_note" && sale && !sale.customerId) return "Needs a named customer on the sale";
    if (v === "credit_note" && !claim.invoiceId) return "Needs the original sale";
    if (v === "replacement" && !soldLine) return "Needs the part from the original sale";
    return null;
  };
  const submit = async () => {
    if (!choice) return;
    const decision: WarrantyDecision =
      choice === "reject"
        ? { kind: "reject", reason: reason.trim() }
        : {
            kind: "approve",
            resolution: choice,
            qty,
            replacement: choice === "replacement" && soldLine ? [{ stockItemId: soldLine.stockItemId, uomId: soldLine.uomId, qty }] : null,
          };
    setWorking(true);
    try {
      const badge =
        decision.kind === "reject"
          ? { action: "warranty_reject" as const, args: { claim_id: claim.id, reason: decision.reason } }
          : { action: "warranty_approve" as const, args: warrantyApproveArgs(claim, decision, "") };
      const done = await pos.runApproved(proof, badge, (m) => pos.gateway.decideWarrantyClaim(claim, decision, m));
      if (!done) return;
      pos.showNotice(
        decision.kind === "reject" ? "Claim rejected." : choice === "replacement" ? "Claim approved. Hand over the replacement part." : "Claim approved.",
      );
      onDone();
    } finally {
      setWorking(false);
    }
  };
  return (
    <div className={styles.panelInset} style={{ marginTop: 16 }}>
      <h3 className={styles.sectionTitle} style={{ margin: 0 }}>
        Decide {claim.documentNumber ?? "claim"} · {claim.partNumber}
      </h3>
      {claim.notes ? <p className={styles.muted}>{claim.notes}</p> : null}
      <div className={styles.tenderGrid} role="radiogroup" aria-label="Decision" style={{ marginTop: 12 }}>
        {DECISIONS.map((d) => {
          const blocked = why(d.value);
          return (
            <button
              key={d.value}
              type="button"
              role="radio"
              aria-checked={choice === d.value}
              disabled={Boolean(blocked)}
              className={`${styles.tenderCard} ${choice === d.value ? styles.tenderCardActive : ""}`}
              onClick={() => setChoice(d.value)}
            >
              <strong>{d.label}</strong>
              <span className={styles.muted}>{blocked ?? d.hint}</span>
            </button>
          );
        })}
      </div>
      {choice === "reject" ? (
        <label className={styles.field} style={{ marginTop: 12 }}>
          <span className={styles.fieldLabel}>Reason the customer is told</span>
          <input className={styles.input} value={reason} onChange={(e) => setReason(e.target.value)} />
        </label>
      ) : choice === "credit_note" || choice === "replacement" ? (
        <label className={styles.field} style={{ marginTop: 12, maxWidth: 160 }}>
          <span className={styles.fieldLabel}>Quantity</span>
          <input className={styles.input} type="number" min={1} max={soldLine?.qty ?? 1} step={1} value={qty} onChange={(e) => setQty(Math.max(1, Math.min(soldLine?.qty ?? 1, Number(e.target.value) || 1)))} />
        </label>
      ) : null}
      <div style={{ marginTop: 12 }}>
        <ApproverProofFields pos={pos} onProof={setProof} />
      </div>
      <div className={styles.rowEnd}>
        <button type="button" className={styles.softButton} onClick={onClose}>
          Cancel
        </button>
        <button
          type="button"
          className={styles.primaryButton}
          disabled={working || !choice || Boolean(choice && why(choice)) || (choice === "reject" && !reason.trim()) || !(pos.selfApprover || proof) || !pos.online}
          onClick={() => void submit()}
        >
          {working ? "Saving…" : "Save decision"}
        </button>
      </div>
    </div>
  );
}
