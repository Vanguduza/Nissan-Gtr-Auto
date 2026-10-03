"use client";

import { BadgeCheck, IdCard, Printer, ShieldCheck, ShieldOff, UserMinus, UserPlus } from "lucide-react";
import QRCode from "qrcode";
import { useCallback, useEffect, useState } from "react";
import { createPortal } from "react-dom";
import type { ApprovalTrailRow, IssuedBadge, ManagerBadge, ManagerCandidate } from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import { Modal } from "./PosDialogs";
import { Segment } from "./PosScreens";
import styles from "./pos.module.css";

const SOURCE_LABEL: Record<string, string> = {
  admin_role: "Manager (admin role)",
  assigned: "Manager (assigned)",
  hr_grade_or_role: "Manager (HR grade / role)",
};
const ACTION_LABEL: Record<string, string> = {
  discount: "Discount",
  discount_applied: "Discount",
  price_override: "Price override",
  void_sale: "Void sale",
  cart_voided: "Void sale",
  refund: "Refund",
  refund_posted: "Refund",
  cash_out: "Cash out",
  till_variance: "Till variance",
  till_handover: "Till handover",
  repair_paid_order: "Repair paid order",
  paid_order_repaired: "Repair paid order",
  assigned: "Manager assigned",
  unassigned: "Manager unassigned",
  badge_issued: "Badge issued",
  badge_revoked: "Badge revoked",
};

function when(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

/**
 * Managers & ID badges (staff POS admin). Who may approve POS actions, their QR ID cards, and every
 * approval made. The server enforces access: admins change, admins and finance read.
 */
export function ManagersScreen({ pos }: { pos: PosStore }) {
  const [tab, setTab] = useState<"managers" | "badges" | "trail">("managers");
  const [candidates, setCandidates] = useState<ManagerCandidate[] | null>(null);
  const [badges, setBadges] = useState<ManagerBadge[] | null>(null);
  const [trail, setTrail] = useState<ApprovalTrailRow[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [issuing, setIssuing] = useState<ManagerCandidate | null>(null);
  const [issued, setIssued] = useState<IssuedBadge | null>(null);
  const [revoking, setRevoking] = useState<ManagerBadge | null>(null);
  const { gateway } = pos;

  const load = useCallback(async () => {
    const [c, b, t] = await Promise.all([gateway.listManagerCandidates(), gateway.listBadges(null), gateway.approvalTrail(200)]);
    setError(!c.ok ? c.error : !b.ok ? b.error : !t.ok ? t.error : null);
    setCandidates(c.ok ? c.data : []);
    setBadges(b.ok ? b.data : []);
    setTrail(t.ok ? t.data : []);
  }, [gateway]);
  useEffect(() => {
    void load();
  }, [load]);

  const run = async (res: Promise<{ ok: true } | { ok: false; error: string }>) => {
    const r = await res;
    if (!r.ok) setError(r.error.includes("admin role") ? "Only an admin can change managers and badges." : r.error);
    else setError(null);
    void load();
    return r.ok;
  };

  return (
    <section className={styles.panel}>
      <div className={styles.sectionHead}>
        <h2 className={styles.panelTitle}>Managers &amp; ID badges</h2>
        <Segment
          label="Managers view"
          value={tab}
          options={[
            { id: "managers", label: "Managers" },
            { id: "badges", label: `Badges (${badges?.filter((b) => b.status === "active").length ?? 0})` },
            { id: "trail", label: "Approval trail" },
          ]}
          onChange={setTab}
        />
      </div>
      <p className={styles.muted}>
        Managers approve discounts, overrides, voids, refunds, cash out, till variances and handovers. A signed-in manager approves
        without a prompt; at someone else&apos;s till they scan their ID badge. Badges expire, can be revoked at any time, and stop
        working the moment their holder is no longer a manager.
      </p>
      {error ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{error}</p> : null}

      <div className={styles.list}>
        {tab === "managers" ? (
          candidates == null ? (
            <div className={styles.emptyCard}>Loading…</div>
          ) : candidates.length === 0 ? (
            <div className={styles.emptyCard}>No staff accounts.</div>
          ) : (
            candidates.map((c) => (
              <div key={c.userId} className={styles.listRow}>
                <span>
                  <div className={styles.listTitle}>
                    {c.fullName} {c.isApprover ? <span className={styles.badge}>{SOURCE_LABEL[c.source ?? ""] ?? "Manager"}</span> : null}
                  </div>
                  <div className={styles.muted}>
                    {[c.employeeCode, c.email, c.roles.join(", ")].filter(Boolean).join(" · ")}
                    {c.activeBadges > 0 ? ` · ${c.activeBadges} active badge${c.activeBadges === 1 ? "" : "s"}` : ""}
                  </div>
                </span>
                <span className={styles.row}>
                  {c.assigned ? (
                    <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => void run(gateway.setManagerAssignment(c.userId, false, null))}>
                      <UserMinus size={16} aria-hidden /> Unassign
                    </button>
                  ) : !c.isApprover ? (
                    <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => void run(gateway.setManagerAssignment(c.userId, true, "Assigned in POS admin"))}>
                      <UserPlus size={16} aria-hidden /> Make manager
                    </button>
                  ) : null}
                  <button type="button" className={styles.primaryButton} disabled={!c.isApprover} onClick={() => setIssuing(c)} title={c.isApprover ? undefined : "Only managers get a badge"}>
                    <IdCard size={16} aria-hidden /> Issue badge
                  </button>
                </span>
              </div>
            ))
          )
        ) : tab === "badges" ? (
          badges == null ? (
            <div className={styles.emptyCard}>Loading…</div>
          ) : badges.length === 0 ? (
            <div className={styles.emptyCard}>No badges issued yet. Issue one from Managers.</div>
          ) : (
            badges.map((b) => (
              <div key={b.badgeId} className={styles.listRow}>
                <span>
                  <div className={styles.listTitle}>
                    {b.fullName} {b.label ? `· ${b.label}` : ""} <span className={styles.badge}>{b.status}</span>
                  </div>
                  <div className={styles.muted}>
                    Issued {when(b.issuedAt)} · expires {when(b.expiresAt)} · used {b.useCount}×{b.lastUsedAt ? `, last ${when(b.lastUsedAt)}` : ""}
                    {b.revokedAt ? ` · revoked ${when(b.revokedAt)}: ${b.revokeReason ?? ""}` : ""}
                  </div>
                </span>
                {b.status === "active" ? (
                  <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => setRevoking(b)}>
                    <ShieldOff size={16} aria-hidden /> Revoke
                  </button>
                ) : null}
              </div>
            ))
          )
        ) : trail == null ? (
          <div className={styles.emptyCard}>Loading…</div>
        ) : trail.length === 0 ? (
          <div className={styles.emptyCard}>No approvals recorded yet.</div>
        ) : (
          trail.map((t, i) => (
            <div key={`${t.at}-${i}`} className={styles.listRow}>
              <span>
                <div className={styles.listTitle}>
                  {ACTION_LABEL[t.action] ?? t.action.replace(/_/g, " ")}{" "}
                  <span className={styles.badge}>{t.method === "badge" ? `badge · ${t.outcome}` : t.method === "admin" ? "admin" : "manager sign-in"}</span>
                </div>
                <div className={styles.muted}>
                  {when(t.at)} · {t.method === "admin" ? `${t.managerName ?? "Admin"} → ${t.requestedByName ?? ""}` : `approved by ${t.managerName ?? "—"} for ${t.requestedByName ?? "—"}`}
                  {t.reasonCode ? ` · ${t.reasonCode.replace(/_/g, " ")}` : ""}
                  {t.detail ? ` · ${t.detail}` : ""}
                  {t.deviceId ? ` · ${t.deviceId}` : ""}
                </div>
              </span>
            </div>
          ))
        )}
      </div>

      {issuing && !issued ? (
        <IssueBadgeDialog
          candidate={issuing}
          onCancel={() => setIssuing(null)}
          onIssue={async (label, days) => {
            const r = await gateway.issueBadge(issuing.userId, label, days);
            if (!r.ok) {
              setError(r.error.includes("admin role") ? "Only an admin can issue badges." : r.error);
              setIssuing(null);
              return;
            }
            setIssued(r.data);
            void load();
          }}
        />
      ) : null}
      {issued ? (
        <BadgeCardDialog
          badge={issued}
          onClose={() => {
            setIssued(null);
            setIssuing(null);
          }}
        />
      ) : null}
      {revoking ? (
        <RevokeDialog
          badge={revoking}
          onCancel={() => setRevoking(null)}
          onRevoke={async (reason) => {
            if (await run(gateway.revokeBadge(revoking.badgeId, reason))) setRevoking(null);
          }}
        />
      ) : null}
    </section>
  );
}

function IssueBadgeDialog({ candidate, onCancel, onIssue }: { candidate: ManagerCandidate; onCancel: () => void; onIssue: (label: string | null, days: number) => Promise<void> }) {
  const [label, setLabel] = useState("");
  const [days, setDays] = useState("365");
  const [busy, setBusy] = useState(false);
  const n = Math.floor(Number(days));
  return (
    <Modal title={`Issue badge · ${candidate.fullName}`} onClose={onCancel}>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          await onIssue(label.trim() || null, n);
          setBusy(false);
        }}
      >
        <p className={styles.muted}>
          <ShieldCheck size={14} aria-hidden /> The card&apos;s QR is shown once, right after this. Print it straight away. A lost card is revoked and
          replaced; it can never be shown again.
        </p>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Card label (optional)</span>
            <input className={styles.input} value={label} onChange={(e) => setLabel(e.target.value)} placeholder="e.g. Front counter" />
          </label>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Valid for (days)</span>
            <input className={styles.input} inputMode="numeric" value={days} onChange={(e) => setDays(e.target.value)} />
          </label>
        </div>
        <div className={styles.rowEnd}>
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onCancel}>
            Cancel
          </button>
          <button type="submit" className={styles.primaryButton} disabled={busy || !(n >= 1 && n <= 1095)}>
            <BadgeCheck size={16} aria-hidden /> Issue badge
          </button>
        </div>
      </form>
    </Modal>
  );
}

/** ID-1 card (85.6 × 54 mm): name, employee code, role, expiry and the approval QR. */
function BadgeCard({ badge, qr }: { badge: IssuedBadge; qr: string | null }) {
  return (
    <div className={styles.idCard}>
      <div className={styles.idCardBrand}>GTR AUTO · POS MANAGER</div>
      <div className={styles.idCardBody}>
        <div className={styles.idCardText}>
          <div className={styles.idCardName}>{badge.fullName}</div>
          {badge.employeeCode ? <div>{badge.employeeCode}</div> : null}
          <div className={styles.idCardMeta}>Approves POS actions</div>
          <div className={styles.idCardMeta}>Valid until {new Date(badge.expiresAt).toLocaleDateString(undefined, { dateStyle: "medium" })}</div>
          <div className={styles.idCardMeta}>Card {badge.badgeId.slice(0, 8)}</div>
        </div>
        {qr ? <img className={styles.idCardQr} src={qr} alt="Manager approval QR" /> : <div className={styles.idCardQr} />}
      </div>
      <div className={styles.idCardFoot}>If found, return to GTR Auto. Lost cards are revoked.</div>
    </div>
  );
}

function BadgeCardDialog({ badge, onClose }: { badge: IssuedBadge; onClose: () => void }) {
  const [qr, setQr] = useState<string | null>(null);
  const [slot, setSlot] = useState<HTMLElement | null>(null);
  useEffect(() => {
    void QRCode.toDataURL(badge.payload, { errorCorrectionLevel: "M", margin: 1, width: 360 }).then(setQr);
    setSlot(document.getElementById("pos-print-slot"));
  }, [badge.payload]);
  return (
    <Modal title="Manager ID card" onClose={onClose}>
      <p className={styles.muted}>
        <ShieldCheck size={14} aria-hidden /> Print this card now (ID-1 size, 85.6 × 54 mm). The QR will not be shown again; anyone holding the
        card can approve POS actions, so treat it like a key.
      </p>
      <div style={{ display: "flex", justifyContent: "center", margin: "14px 0" }}>
        <BadgeCard badge={badge} qr={qr} />
      </div>
      <div className={styles.rowEnd}>
        <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onClose}>
          Done
        </button>
        <button
          type="button"
          className={styles.primaryButton}
          disabled={!qr}
          onClick={() => {
            const style = document.createElement("style");
            style.textContent = "@page { size: 85.6mm 54mm; margin: 0; }";
            document.head.appendChild(style);
            window.print();
            style.remove();
          }}
        >
          <Printer size={16} aria-hidden /> Print card
        </button>
      </div>
      {slot && qr ? createPortal(<BadgeCard badge={badge} qr={qr} />, slot) : null}
    </Modal>
  );
}

function RevokeDialog({ badge, onCancel, onRevoke }: { badge: ManagerBadge; onCancel: () => void; onRevoke: (reason: string) => Promise<void> }) {
  const [reason, setReason] = useState("");
  return (
    <Modal title={`Revoke badge · ${badge.fullName}`} onClose={onCancel}>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          await onRevoke(reason.trim());
        }}
      >
        <p className={styles.muted}>The card stops working immediately on every till. This cannot be undone; issue a new card if needed.</p>
        <label className={styles.field}>
          <span className={styles.fieldLabel}>Reason</span>
          <input className={styles.input} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Card lost" autoFocus />
        </label>
        <div className={styles.rowEnd}>
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onCancel}>
            Cancel
          </button>
          <button type="submit" className={styles.primaryButton} disabled={!reason.trim()}>
            <ShieldOff size={16} aria-hidden /> Revoke badge
          </button>
        </div>
      </form>
    </Modal>
  );
}
