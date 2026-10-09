"use client";

import { CreditCard } from "lucide-react";
import { Fragment, useCallback, useEffect, useState } from "react";
import type { PosGateway } from "@/lib/pos/gateway";
import { formatMoney } from "@/lib/pos/money";
import type { CardTerminal, CardTerminalConfig, CardTerminalInput, Warehouse } from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import { Modal } from "./PosDialogs";
import { PaymentLetters } from "./PaymentLetters";
import styles from "./pos.module.css";

const STATUS: Record<string, string> = {
  approved: "Charged, sale not posted",
  recovery_required: "Charged, sale not posted",
  unknown: "Answer not received",
  initiated: "Answer not received",
};

function when(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

/**
 * Card-machine payments to resolve (Blueprint §10.7, §10.11). A charge the machine approved can be
 * posted from here; asking the machine again or reversing on it needs the paired counter tablet.
 */
export function TerminalRecoveryList({ pos, orderId }: { pos: PosStore; orderId: string | null }) {
  const { refreshTerminalRecovery } = pos;
  useEffect(() => {
    void refreshTerminalRecovery();
  }, [refreshTerminalRecovery]);
  const list = (pos.terminalRecovery ?? []).filter((t) => orderId == null || t.orderId === orderId);
  if (list.length === 0) return null;
  return (
    <>
      <h3 className={styles.panelTitle} style={{ fontSize: 15, marginTop: 12 }}>
        <CreditCard size={14} aria-hidden /> Card machine
      </h3>
      {list.map((t) => {
        const charged = t.operation === "purchase" && (t.status === "approved" || t.status === "recovery_required");
        return (
          <Fragment key={t.attemptId}>
          <div className={styles.listRow}>
            <span>
              <div className={styles.listTitle}>
                {formatMoney(t.amount, t.currency)} <span className={styles.badge}>{STATUS[t.status] ?? t.status.replace(/_/g, " ")}</span>
              </div>
              <div className={styles.muted}>
                {[t.terminalLabel, t.cardLast4 ? `•••• ${t.cardLast4}` : null, t.transactionId, t.message, when(t.updatedAt)].filter(Boolean).join(" · ")}
              </div>
              {!charged ? <div className={styles.muted}>Check on the paired counter tablet: it asks the machine again before anything else.</div> : null}
            </span>
            {charged ? (
              <button type="button" className={styles.primaryButton} disabled={pos.busy} onClick={() => void pos.finishTerminalPayment(t.attemptId)}>
                Finish the sale
              </button>
            ) : null}
          </div>
          {/* On one order's recovery: a signed letter of what the card machine reported. */}
          {orderId ? <PaymentLetters pos={pos} kind="card_terminal" sourceId={t.attemptId} /> : null}
          </Fragment>
        );
      })}
    </>
  );
}

const CONFIG_FIELDS: Array<{ key: keyof CardTerminalConfig; label: string; required?: boolean; hint?: string }> = [
  { key: "package_name", label: "Card machine app package", required: true, hint: "e.g. zw.co.bank.pos" },
  { key: "purchase_action", label: "Purchase intent action", required: true },
  { key: "status_action", label: "Status intent action (asks the machine again)" },
  { key: "reversal_action", label: "Reversal intent action" },
  { key: "refund_action", label: "Refund intent action" },
];
const KEY_FIELDS: Array<keyof CardTerminalConfig> = [
  "amount_minor_key",
  "currency_key",
  "reference_key",
  "operation_key",
  "original_transaction_id_key",
  "result_status_key",
  "result_transaction_id_key",
  "result_rrn_key",
  "result_auth_code_key",
  "result_last4_key",
  "result_scheme_key",
  "result_response_code_key",
  "result_response_message_key",
];

const blank = (): CardTerminalInput => ({
  id: null,
  code: "",
  label: "",
  acquirerName: null,
  externalTerminalId: null,
  config: {},
  warehouseId: null,
  deviceId: null,
  isActive: true,
  allowDelivery: false,
});

/**
 * Settings → Card machines (admin, server-enforced): the acquirer's terminal app each machine is
 * reached through. Only intent and extra names are stored — no keys, card data or secrets. A tablet
 * is then paired with its machine from the tablet's own Settings.
 */
export function CardMachinesSetting({ gateway }: { gateway: PosGateway }) {
  const [open, setOpen] = useState(false);
  const [list, setList] = useState<CardTerminal[] | null>(null);
  const [warehouses, setWarehouses] = useState<Warehouse[]>([]);
  const [editing, setEditing] = useState<CardTerminalInput | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [advanced, setAdvanced] = useState(false);

  const load = useCallback(async () => {
    const [r, w] = await Promise.all([gateway.listCardTerminals(), gateway.listWarehouses()]);
    if (r.ok) setList(r.data);
    else setError(r.error);
    if (w.ok) setWarehouses(w.data);
  }, [gateway]);

  useEffect(() => {
    if (open) void load();
  }, [open, load]);

  const save = async () => {
    if (!editing) return;
    setSaving(true);
    setError(null);
    try {
      const r = await gateway.saveCardTerminal(editing);
      if (!r.ok) {
        setError(r.error.includes("admin") ? "Only an admin can set up card machines." : r.error);
        return;
      }
      setEditing(null);
      await load();
    } finally {
      setSaving(false);
    }
  };

  const set = (patch: Partial<CardTerminalInput>) => setEditing((e) => (e ? { ...e, ...patch } : e));
  const setConfig = (key: keyof CardTerminalConfig, value: string) => setEditing((e) => (e ? { ...e, config: { ...e.config, [key]: value } } : e));

  return (
    <div className={styles.listRow}>
      <span>
        <div className={styles.listTitle}>Card machines</div>
        <div className={styles.muted}>The swipe machines the counter tablets take card payments on. Only an admin can change them.</div>
      </span>
      <button type="button" className={styles.primaryButton} onClick={() => setOpen(true)}>
        View
      </button>
      {open ? (
        <Modal title="Card machines" onClose={() => (editing ? setEditing(null) : setOpen(false))} wide>
          {editing ? (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void save();
              }}
            >
              <div className={styles.formGrid}>
                <label className={styles.field}>
                  <span className={styles.fieldLabel}>Code (unique)</span>
                  <input className={styles.input} value={editing.code} onChange={(e) => set({ code: e.target.value })} required />
                </label>
                <label className={styles.field}>
                  <span className={styles.fieldLabel}>Name shown at the counter</span>
                  <input className={styles.input} value={editing.label} onChange={(e) => set({ label: e.target.value })} required />
                </label>
                <label className={styles.field}>
                  <span className={styles.fieldLabel}>Bank / acquirer</span>
                  <input className={styles.input} value={editing.acquirerName ?? ""} onChange={(e) => set({ acquirerName: e.target.value })} />
                </label>
                <label className={styles.field}>
                  <span className={styles.fieldLabel}>Terminal ID (from the bank)</span>
                  <input className={styles.input} value={editing.externalTerminalId ?? ""} onChange={(e) => set({ externalTerminalId: e.target.value })} />
                </label>
                <label className={styles.field}>
                  <span className={styles.fieldLabel}>Warehouse</span>
                  <select className={styles.input} value={editing.warehouseId ?? ""} onChange={(e) => set({ warehouseId: e.target.value || null })}>
                    <option value="">Any warehouse</option>
                    {warehouses.map((w) => (
                      <option key={w.id} value={w.id}>
                        {w.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className={styles.field}>
                  <span className={styles.fieldLabel}>Only on device (tablet or driver phone id, optional)</span>
                  <input className={styles.input} value={editing.deviceId ?? ""} onChange={(e) => set({ deviceId: e.target.value })} />
                </label>
              </div>
              <div className={styles.formGrid}>
                {CONFIG_FIELDS.map((f) => (
                  <label key={f.key} className={styles.field}>
                    <span className={styles.fieldLabel}>
                      {f.label}
                      {f.required ? " (required)" : ""}
                    </span>
                    <input className={styles.input} placeholder={f.hint} value={editing.config[f.key] ?? ""} onChange={(e) => setConfig(f.key, e.target.value)} required={f.required} />
                  </label>
                ))}
              </div>
              <button type="button" className={styles.linkButton} style={{ marginTop: 8 }} onClick={() => setAdvanced((a) => !a)}>
                {advanced ? "Hide extra names" : "Extra names the bank's app uses (only if it differs from the defaults)"}
              </button>
              {advanced ? (
                <div className={styles.formGrid}>
                  {KEY_FIELDS.map((k) => (
                    <label key={k} className={styles.field}>
                      <span className={styles.fieldLabel}>{k.replace(/_key$/, "").replace(/_/g, " ")}</span>
                      <input className={styles.input} value={editing.config[k] ?? ""} onChange={(e) => setConfig(k, e.target.value)} />
                    </label>
                  ))}
                </div>
              ) : null}
              <label className={styles.checkRow}>
                <input type="checkbox" checked={editing.isActive} onChange={(e) => set({ isActive: e.target.checked })} /> In use
              </label>
              <label className={styles.checkRow}>
                <input type="checkbox" checked={editing.allowDelivery} onChange={(e) => set({ allowDelivery: e.target.checked })} /> Drivers can take card on
                delivery with it (set the driver&apos;s phone ID above; the driver app shows it)
              </label>
              {error ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{error}</p> : null}
              <div className={styles.rowEnd}>
                <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setEditing(null)}>
                  Cancel
                </button>
                <button type="submit" className={styles.primaryButton} disabled={saving}>
                  {saving ? "Saving…" : "Save"}
                </button>
              </div>
            </form>
          ) : (
            <>
              {error ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{error}</p> : null}
              <div className={styles.list}>
                {list == null ? <p className={styles.muted}>Loading…</p> : null}
                {list?.length === 0 ? <div className={styles.emptyCard}>No card machine set up yet.</div> : null}
                {list?.map((t) => (
                  <div key={t.id} className={styles.listRow}>
                    <span>
                      <div className={styles.listTitle}>
                        {t.label} <span className={styles.badge}>{t.code}</span>
                      </div>
                      <div className={styles.muted}>
                        {[t.acquirerName, t.externalTerminalId, t.config.package_name, t.allowDelivery ? "deliveries" : null, t.deviceId ? `device ${t.deviceId}` : null]
                          .filter(Boolean)
                          .join(" · ")}
                      </div>
                    </span>
                    <button
                      type="button"
                      className={`${styles.softButton} ${styles.inlineButton}`}
                      onClick={() => {
                        setError(null);
                        setEditing({ ...t });
                      }}
                    >
                      Edit
                    </button>
                  </div>
                ))}
              </div>
              <p className={styles.muted}>
                After adding a machine, pair each counter tablet with it from the tablet&apos;s Settings → Card machine (an admin signs in once). A driver pairs from Proof of delivery → Payment in the driver app once the machine is set for deliveries.
              </p>
              <div className={styles.rowEnd}>
                <button
                  type="button"
                  className={styles.primaryButton}
                  onClick={() => {
                    setError(null);
                    setEditing(blank());
                  }}
                >
                  Add card machine
                </button>
              </div>
            </>
          )}
        </Modal>
      ) : null}
    </div>
  );
}
