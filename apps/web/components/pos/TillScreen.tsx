"use client";

import { ArrowDownToLine, ArrowUpFromLine, Lock, LockOpen, ShieldCheck, Users } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { formatMoney } from "@/lib/pos/money";
import type { CashMovementKind, DenominationCount, HandoverOperator, PosCurrency, ReasonCode, TillCloseResult } from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import { Modal } from "./PosDialogs";
import { Segment } from "./PosScreens";
import styles from "./pos.module.css";

/** Notes and coins the drawer is counted in, largest first. */
const DENOMINATIONS: Record<PosCurrency, number[]> = {
  USD: [100, 50, 20, 10, 5, 2, 1, 0.5, 0.25, 0.1, 0.05, 0.01],
  ZIG: [200, 100, 50, 20, 10, 5, 2, 1, 0.5, 0.25, 0.1],
};

/** "US$ 50" for notes, "50c" for coins — how a cashier names what is in the drawer. */
function denominationLabel(d: number, currency: PosCurrency): string {
  if (d >= 1) return `${currency === "USD" ? "US$" : "ZiG"} ${d}`;
  return `${Math.round(d * 100)}c`;
}

/** Cash-in reasons are the operator's own; cash-out reasons come from the configured list. */
const CASH_IN_REASONS: ReasonCode[] = [
  { code: "float_top_up", label: "Float top-up", requiresNotes: false },
  { code: "change_from_safe", label: "Change from the safe", requiresNotes: false },
  { code: "other_cash_in", label: "Other", requiresNotes: true },
];

/** A configured cash-out reason decides the movement kind the ledger records. */
function cashOutKind(code: string): CashMovementKind {
  if (code === "petty_cash") return "petty_cash";
  if (code === "bank_drop") return "bank_drop";
  if (code === "customer_refund") return "cash_refund";
  return "cash_out";
}

function when(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

const STATUS_LABEL = { open: "Open", variance_pending: "Variance awaiting manager", closed: "Closed" } as const;

function OpenTillCard({ pos }: { pos: PosStore }) {
  const [warehouseId, setWarehouseId] = useState(pos.setup.warehouseId ?? pos.warehouses[0]?.id ?? "");
  const [currency, setCurrency] = useState<PosCurrency>(pos.setup.currency);
  const [float, setFloat] = useState("");
  useEffect(() => {
    if (!warehouseId && pos.warehouses[0]) setWarehouseId(pos.warehouses[0].id);
  }, [pos.warehouses, warehouseId]);
  const amount = Number(float);
  return (
    <form
      className={styles.list}
      onSubmit={async (e) => {
        e.preventDefault();
        if (await pos.openTill(warehouseId, amount, currency)) setFloat("");
      }}
    >
      <div className={styles.listRow}>
        <span>
          <div className={styles.listTitle}>
            <LockOpen size={14} aria-hidden /> Open the till
          </div>
          <div className={styles.muted}>Count the float into the drawer, then open the till to start selling.</div>
        </span>
      </div>
      <div className={styles.formGrid}>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>Selling from</span>
          <select className={styles.input} value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)}>
            {pos.warehouses.map((w) => (
              <option key={w.id} value={w.id}>
                {w.name} ({w.code})
              </option>
            ))}
          </select>
        </label>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>Opening float</span>
          <input
            className={styles.input}
            inputMode="decimal"
            value={float}
            onChange={(e) => setFloat(e.target.value.replace(/[^0-9.]/g, ""))}
            placeholder="0.00"
            aria-label="Opening float"
          />
        </label>
      </div>
      <div className={styles.rowEnd}>
        <Segment
          label="Till currency"
          value={currency}
          options={[
            { id: "USD", label: "USD" },
            { id: "ZIG", label: "ZiG" },
          ]}
          onChange={setCurrency}
        />
        <button type="submit" className={styles.primaryButton} disabled={pos.busy || !warehouseId || float === "" || !(amount >= 0)}>
          Open till
        </button>
      </div>
    </form>
  );
}

function CashMovementDialog({ pos, direction, onClose }: { pos: PosStore; direction: "in" | "out"; onClose: () => void }) {
  const [reasons, setReasons] = useState<ReasonCode[]>(direction === "in" ? CASH_IN_REASONS : []);
  const [reason, setReason] = useState("");
  const [amount, setAmount] = useState("");
  const [notes, setNotes] = useState("");
  useEffect(() => {
    if (direction === "out") void pos.gateway.listReasons("cash_out").then((r) => setReasons(r.ok ? r.data : []));
  }, [direction, pos.gateway]);
  const chosen = reasons.find((r) => r.code === reason) ?? null;
  const value = Number(amount);
  const currency = pos.till?.currency ?? "USD";
  return (
    <Modal title={direction === "in" ? "Cash in" : "Cash out"} onClose={onClose}>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          if (!chosen) return;
          const kind = direction === "in" ? "cash_in" : cashOutKind(chosen.code);
          if (await pos.cashMovement(kind, value, chosen.code, chosen.label, notes.trim() || null)) onClose();
        }}
      >
        <p className={styles.muted}>
          {direction === "in" ? (
            "Money added to the drawer during the shift."
          ) : (
            <>
              <ShieldCheck size={14} aria-hidden /> Money taken out of the drawer needs a manager to approve.
            </>
          )}
        </p>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Amount ({currency})</span>
            <input className={styles.input} inputMode="decimal" value={amount} onChange={(e) => setAmount(e.target.value.replace(/[^0-9.]/g, ""))} autoFocus />
          </label>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Reason</span>
            <select className={styles.input} value={reason} onChange={(e) => setReason(e.target.value)}>
              <option value="">Choose a reason</option>
              {reasons.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.label}
                </option>
              ))}
            </select>
          </label>
        </div>
        <label className={styles.field} style={{ marginTop: 12 }}>
          <span className={styles.fieldLabel}>{chosen?.requiresNotes ? "Notes (required)" : "Notes (optional)"}</span>
          <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>
        {pos.error ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{pos.error}</p> : null}
        <div className={styles.rowEnd}>
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            className={styles.primaryButton}
            disabled={pos.busy || !chosen || !(value > 0) || Boolean(chosen?.requiresNotes && !notes.trim())}
          >
            {direction === "in" ? "Record cash in" : "Ask manager"}
          </button>
        </div>
      </form>
    </Modal>
  );
}

function HandoverDialog({ pos, onClose }: { pos: PosStore; onClose: () => void }) {
  const [operators, setOperators] = useState<HandoverOperator[]>([]);
  const [userId, setUserId] = useState("");
  useEffect(() => {
    void pos.loadHandoverOperators().then(setOperators);
  }, [pos]);
  const till = pos.till;
  const others = operators.filter((o) => o.userId !== till?.operatorUserId);
  const chosen = others.find((o) => o.userId === userId) ?? null;
  return (
    <Modal title="Hand over the till" onClose={onClose}>
      <p className={styles.muted}>
        The drawer and its cash move to the next operator without closing. A manager approves the handover.
      </p>
      <div className={styles.list} role="radiogroup" aria-label="Next operator">
        {others.length === 0 ? <div className={styles.emptyCard}>No other active sales staff.</div> : null}
        {others.map((o) => (
          <button
            key={o.userId}
            type="button"
            role="radio"
            aria-checked={userId === o.userId}
            className={`${styles.listRow} ${userId === o.userId ? styles.listRowSelected : ""}`}
            style={{ textAlign: "left", border: 0, font: "inherit", cursor: "pointer" }}
            onClick={() => setUserId(o.userId)}
          >
            <span>
              <span className={styles.listTitle}>{o.fullName}</span>
              <div className={styles.muted}>
                {o.employeeCode} · {o.roles.join(", ")}
              </div>
            </span>
          </button>
        ))}
      </div>
      <div className={styles.rowEnd}>
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onClose}>
          Cancel
        </button>
        <button
          type="button"
          className={styles.primaryButton}
          disabled={!chosen || !till}
          onClick={() => {
            if (!chosen || !till) return;
            pos.requestManager({ kind: "handover", sessionId: till.id, userId: chosen.userId, name: chosen.fullName });
            onClose();
          }}
        >
          Ask manager
        </button>
      </div>
    </Modal>
  );
}

/** Blind count by denomination: the expected cash stays hidden until the count is submitted. */
function CloseTillDialog({ pos, onClose }: { pos: PosStore; onClose: () => void }) {
  const currency = pos.till?.currency ?? "USD";
  const [qty, setQty] = useState<Record<string, string>>({});
  const [notes, setNotes] = useState("");
  const [needsReason, setNeedsReason] = useState(false);
  const [reasons, setReasons] = useState<ReasonCode[]>([]);
  const [reason, setReason] = useState("");
  const [result, setResult] = useState<TillCloseResult | null>(null);
  const counts: DenominationCount[] = useMemo(
    () => DENOMINATIONS[currency].map((d) => ({ denomination: d, quantity: Math.max(0, Math.floor(Number(qty[String(d)] ?? 0)) || 0) })),
    [currency, qty],
  );
  const counted = counts.reduce((s, c) => s + c.denomination * c.quantity, 0);
  useEffect(() => {
    if (needsReason) void pos.gateway.listReasons("till_variance").then((r) => setReasons(r.ok ? r.data : []));
  }, [needsReason, pos.gateway]);
  const chosen = reasons.find((r) => r.code === reason) ?? null;

  if (result) {
    const over = result.variance > 0;
    return (
      <Modal title={result.status === "closed" ? "Till closed" : "Variance awaiting manager"} onClose={onClose}>
        <div className={styles.list}>
          <div className={styles.listRow}>
            <span className={styles.listTitle}>Counted</span>
            <span>{formatMoney(result.countedCash, currency)}</span>
          </div>
          <div className={styles.listRow}>
            <span className={styles.listTitle}>Expected</span>
            <span>{formatMoney(result.expectedCash, currency)}</span>
          </div>
          <div className={styles.listRow}>
            <span className={styles.listTitle}>Variance</span>
            <span>
              {Math.abs(result.variance) < 0.005 ? "None" : `${formatMoney(Math.abs(result.variance), currency)} ${over ? "over" : "short"}`}
            </span>
          </div>
        </div>
        <p className={styles.muted}>
          {result.status === "closed"
            ? "The till is closed. Open a new till to sell again."
            : "A manager must approve the variance before this till is closed."}
        </p>
        <div className={styles.rowEnd}>
          {result.status === "variance_pending" ? (
            <button
              type="button"
              className={styles.primaryButton}
              onClick={() => {
                pos.requestManager({ kind: "tillVariance", sessionId: result.sessionId, variance: result.variance });
                onClose();
              }}
            >
              Manager approval
            </button>
          ) : null}
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onClose}>
            Done
          </button>
        </div>
      </Modal>
    );
  }

  return (
    <Modal title="Close the till" onClose={onClose} wide>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          const res = await pos.closeTill(counts, needsReason ? reason || null : null, notes.trim() || null);
          if (res === "needs_reason") setNeedsReason(true);
          else if (res) setResult(res);
        }}
      >
        <p className={styles.muted}>Count every note and coin in the drawer. The expected amount is shown after you submit.</p>
        <div className={styles.formGrid} style={{ gridTemplateColumns: "repeat(auto-fill, minmax(120px, 1fr))" }}>
          {DENOMINATIONS[currency].map((d) => (
            <label key={d} className={styles.field}>
              <span className={styles.fieldLabel}>{denominationLabel(d, currency)}</span>
              <input
                className={styles.input}
                inputMode="numeric"
                value={qty[String(d)] ?? ""}
                onChange={(e) => setQty((cur) => ({ ...cur, [String(d)]: e.target.value.replace(/[^0-9]/g, "") }))}
                placeholder="0"
                aria-label={`Number of ${denominationLabel(d, currency)}`}
              />
            </label>
          ))}
        </div>
        <div className={styles.listRow} style={{ marginTop: 12 }}>
          <span className={styles.listTitle}>Counted</span>
          <span>{formatMoney(counted, currency)}</span>
        </div>
        {needsReason ? (
          <div className={`${styles.statusBanner} ${styles.statusError}`} role="alert">
            The count does not match the till. Choose a reason, then submit again.
          </div>
        ) : null}
        {needsReason ? (
          <label className={styles.field} style={{ marginTop: 12 }}>
            <span className={styles.fieldLabel}>Reason for the difference</span>
            <select className={styles.input} value={reason} onChange={(e) => setReason(e.target.value)}>
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
          <span className={styles.fieldLabel}>{chosen?.requiresNotes ? "Notes (required)" : "Notes (optional)"}</span>
          <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>
        {pos.error && !needsReason ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{pos.error}</p> : null}
        <div className={styles.rowEnd}>
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onClose}>
            Cancel
          </button>
          <button
            type="submit"
            className={styles.primaryButton}
            disabled={pos.busy || counted <= 0 || (needsReason && (!reason || Boolean(chosen?.requiresNotes && !notes.trim())))}
          >
            Submit count
          </button>
        </div>
      </form>
    </Modal>
  );
}

/** Till destination: open, cash movements, handover, blind close, variance approval, history. */
export function TillScreen({ pos }: { pos: PosStore }) {
  const [dialog, setDialog] = useState<"in" | "out" | "handover" | "close" | null>(null);
  const till = pos.till;
  const warehouse = pos.warehouses.find((w) => w.id === till?.warehouseId);
  useEffect(() => {
    void pos.refreshTillHistory();
    // Reload when the till changes state.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [till?.id, till?.status]);

  return (
    <section className={styles.panel}>
      <div className={styles.sectionHead}>
        <h2 className={styles.panelTitle}>Till</h2>
        {till ? <span className={styles.badge}>{STATUS_LABEL[till.status]}</span> : null}
      </div>

      {!pos.tillLoaded ? <div className={styles.emptyCard}>Checking the till…</div> : null}
      {pos.tillLoaded && !till ? <OpenTillCard pos={pos} /> : null}

      {till ? (
        <div className={styles.list}>
          <div className={styles.listRow}>
            <span>
              <div className={styles.listTitle}>
                {till.status === "open" ? <LockOpen size={14} aria-hidden /> : <Lock size={14} aria-hidden />}{" "}
                {warehouse ? `${warehouse.name} (${warehouse.code})` : "Till"} · {till.currency}
              </div>
              <div className={styles.muted}>
                Opened {when(till.openedAt)} · float {formatMoney(till.openingFloat, till.currency)}
              </div>
            </span>
          </div>
          {till.status === "open" ? (
            <div className={styles.rowEnd} style={{ flexWrap: "wrap" }}>
              <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setDialog("in")}>
                <ArrowDownToLine size={16} aria-hidden /> Cash in
              </button>
              <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setDialog("out")}>
                <ArrowUpFromLine size={16} aria-hidden /> Cash out
              </button>
              <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setDialog("handover")}>
                <Users size={16} aria-hidden /> Hand over
              </button>
              <button type="button" className={styles.primaryButton} onClick={() => setDialog("close")}>
                Close till
              </button>
            </div>
          ) : (
            <div className={styles.listRow}>
              <span>
                <div className={styles.listTitle}>Counted {formatMoney(till.countedCash ?? 0, till.currency)}</div>
                <div className={styles.muted}>
                  {till.variance != null
                    ? `${formatMoney(Math.abs(till.variance), till.currency)} ${till.variance < 0 ? "short" : "over"} — a manager must approve before the till closes.`
                    : "A manager must approve the variance before the till closes."}
                </div>
              </span>
              <button
                type="button"
                className={styles.primaryButton}
                onClick={() => pos.requestManager({ kind: "tillVariance", sessionId: till.id, variance: till.variance })}
              >
                Manager approval
              </button>
            </div>
          )}
        </div>
      ) : null}

      <div className={styles.sectionHead} style={{ marginTop: 18 }}>
        <h3 className={styles.sectionTitle}>Recent tills</h3>
      </div>
      <div className={styles.list}>
        {pos.tillHistory.length === 0 ? <div className={styles.emptyCard}>No till sessions yet.</div> : null}
        {pos.tillHistory.map((t) => (
          <div key={t.id} className={styles.listRow}>
            <span>
              <div className={styles.listTitle}>{when(t.openedAt)}</div>
              <div className={styles.muted}>
                {STATUS_LABEL[t.status]} · float {formatMoney(t.openingFloat, t.currency)}
                {t.countedCash != null ? ` · counted ${formatMoney(t.countedCash, t.currency)}` : ""}
                {t.variance != null && Math.abs(t.variance) >= 0.005
                  ? ` · ${formatMoney(Math.abs(t.variance), t.currency)} ${t.variance < 0 ? "short" : "over"}`
                  : ""}
              </div>
            </span>
          </div>
        ))}
      </div>

      {dialog === "in" || dialog === "out" ? <CashMovementDialog pos={pos} direction={dialog} onClose={() => setDialog(null)} /> : null}
      {dialog === "handover" ? <HandoverDialog pos={pos} onClose={() => setDialog(null)} /> : null}
      {dialog === "close" ? <CloseTillDialog pos={pos} onClose={() => setDialog(null)} /> : null}
    </section>
  );
}
