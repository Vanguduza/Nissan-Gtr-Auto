"use client";

import { useCallback, useEffect, useState } from "react";
import type { PosGateway } from "@/lib/pos/gateway";
import type { ApprovalPolicy } from "@/lib/pos/types";
import { Modal } from "./PosDialogs";
import styles from "./pos.module.css";

/** Operator-facing names for `pos_approval_policies.action`; the threshold unit follows the action. */
const ACTIONS: Record<string, { label: string; unit: string | null }> = {
  discount_percent: { label: "Discount", unit: "%" },
  price_override_delta_percent: { label: "Price override", unit: "% change" },
  void_cart: { label: "Void sale", unit: null },
  refund_full_invoice: { label: "Refund", unit: null },
  return_post: { label: "Return", unit: null },
  core_return: { label: "Core return", unit: null },
  warranty_decision: { label: "Warranty decision", unit: null },
  cash_out: { label: "Cash out", unit: null },
  till_variance: { label: "Till variance", unit: null },
};

/** Drawer actions: the server functions require a manager whatever the policy says. */
const MANAGER_FIXED = new Set(["cash_out", "till_variance"]);

function describe(p: ApprovalPolicy): string {
  const unit = ACTIONS[p.action]?.unit;
  const manager = p.alwaysRequireManager
    ? "Always needs a manager"
    : unit
      ? `Manager above ${p.thresholdValue}${unit.startsWith("%") ? unit : ` ${unit}`}`
      : "No manager needed";
  return `${manager} · ${p.reasonRequired ? "reason required" : "reason optional"}`;
}

/**
 * Settings row for approval policies (`list_pos_approval_policies` / `set_pos_approval_policy`):
 * everyone can see the rules the counter works under; only an admin can change them (server-enforced).
 */
export function ApprovalPolicySetting({ gateway }: { gateway: PosGateway }) {
  const [policies, setPolicies] = useState<ApprovalPolicy[] | null>(null);
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ApprovalPolicy | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const load = useCallback(async () => {
    const r = await gateway.listApprovalPolicies();
    if (r.ok) setPolicies(r.data);
    else setError(r.error);
  }, [gateway]);

  useEffect(() => {
    if (open) void load();
  }, [open, load]);

  const save = async () => {
    if (!editing) return;
    setSaving(true);
    setError(null);
    try {
      const r = await gateway.setApprovalPolicy({
        action: editing.action,
        thresholdValue: editing.thresholdValue,
        alwaysRequireManager: editing.alwaysRequireManager,
        reasonRequired: editing.reasonRequired,
      });
      if (!r.ok) {
        setError(r.error.includes("admin") ? "Only an admin can change approval policies." : r.error);
        return;
      }
      setEditing(null);
      await load();
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className={styles.listRow}>
      <span>
        <div className={styles.listTitle}>Approval policies</div>
        <div className={styles.muted}>When discounts, overrides, voids, refunds and cash out need a manager, and whether a reason is required.</div>
      </span>
      <button type="button" className={styles.primaryButton} onClick={() => setOpen(true)}>
        View
      </button>
      {open ? (
        <Modal title="Approval policies" onClose={() => (editing ? setEditing(null) : setOpen(false))}>
          {editing ? (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                void save();
              }}
            >
              <p className={styles.muted}>{ACTIONS[editing.action]?.label ?? editing.action}</p>
              <label className={styles.field} style={{ marginTop: 8, flexDirection: "row", alignItems: "center", gap: 8 }}>
                <input
                  type="checkbox"
                  checked={editing.alwaysRequireManager || MANAGER_FIXED.has(editing.action)}
                  disabled={MANAGER_FIXED.has(editing.action)}
                  onChange={(e) => setEditing({ ...editing, alwaysRequireManager: e.target.checked })}
                />
                <span>Always needs a manager</span>
              </label>
              {MANAGER_FIXED.has(editing.action) ? (
                <p className={styles.muted}>Cash leaving the drawer and till variances always need a manager.</p>
              ) : null}
              {ACTIONS[editing.action]?.unit && !editing.alwaysRequireManager ? (
                <label className={styles.field} style={{ marginTop: 8 }}>
                  <span className={styles.fieldLabel}>Manager needed above ({ACTIONS[editing.action]?.unit})</span>
                  <input
                    className={styles.input}
                    inputMode="decimal"
                    value={String(editing.thresholdValue)}
                    onChange={(e) => setEditing({ ...editing, thresholdValue: Math.max(0, Number(e.target.value) || 0) })}
                  />
                </label>
              ) : null}
              <label className={styles.field} style={{ marginTop: 8, flexDirection: "row", alignItems: "center", gap: 8 }}>
                <input type="checkbox" checked={editing.reasonRequired} onChange={(e) => setEditing({ ...editing, reasonRequired: e.target.checked })} />
                <span>Reason required</span>
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
                {policies == null ? <p className={styles.muted}>Loading…</p> : null}
                {policies?.map((p) => (
                  <div key={p.action} className={styles.listRow}>
                    <span>
                      <div className={styles.listTitle}>{ACTIONS[p.action]?.label ?? p.action}</div>
                      <div className={styles.muted}>{describe(p)}</div>
                    </span>
                    <button
                      type="button"
                      className={`${styles.softButton} ${styles.inlineButton}`}
                      onClick={() => {
                        setError(null);
                        setEditing({ ...p });
                      }}
                    >
                      Edit
                    </button>
                  </div>
                ))}
              </div>
              <p className={styles.muted}>Only an admin can save changes.</p>
            </>
          )}
        </Modal>
      ) : null}
    </div>
  );
}
