"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import type { ProcurementProgressStep } from "@gtr/procurement";
import styles from "@/components/account.module.css";
import { ProcurementProgressTracker } from "@/components/procurement-progress-tracker";
import {
  approveMaterialRequest,
  approvePurchaseOrder,
  listSubmittedMaterialRequests,
  listSubmittedPurchaseOrders,
  rejectMaterialRequest,
  rejectPurchaseOrder,
  requireSession,
  type PendingMaterialRequest,
  type PendingPurchaseOrder,
} from "@/lib/procurement-approvals";
import { loadPurchaseOrderProgress } from "@/lib/preferred-po";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      purchaseOrders: PendingPurchaseOrder[];
      materialRequests: PendingMaterialRequest[];
    };

export function StaffProcurementApprovalsPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState("");
  const [poSteps, setPoSteps] = useState<
    Record<string, ProcurementProgressStep>
  >({});

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }
    const [pos, mrs] = await Promise.all([
      listSubmittedPurchaseOrders(client),
      listSubmittedMaterialRequests(client),
    ]);
    if (!pos.ok) {
      setBoot({ kind: "error", message: pos.error });
      return;
    }
    if (!mrs.ok) {
      setBoot({ kind: "error", message: mrs.error });
      return;
    }
    setBoot({
      kind: "ready",
      purchaseOrders: pos.data,
      materialRequests: mrs.data,
    });
    const client2 = createWebClient();
    if (client2 && pos.data.length > 0) {
      const steps: Record<string, ProcurementProgressStep> = {};
      await Promise.all(
        pos.data.map(async ({ po }) => {
          const prog = await loadPurchaseOrderProgress(client2, po.id);
          if (prog.ok) steps[po.id] = prog.data.step;
        }),
      );
      setPoSteps(steps);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onApprovePo(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await approvePurchaseOrder(client, id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Purchase order approved.");
    await refresh();
  }

  async function onRejectPo(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await rejectPurchaseOrder(client, {
      purchaseOrderId: id,
      reason: rejectReason || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setRejectReason("");
    setMessage("Purchase order rejected.");
    await refresh();
  }

  async function onApproveMr(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await approveMaterialRequest(client, id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Material request approved.");
    await refresh();
  }

  async function onRejectMr(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await rejectMaterialRequest(client, {
      materialRequestId: id,
      reason: rejectReason || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setRejectReason("");
    setMessage("Material request rejected.");
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading approvals queue…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with finance or admin to approve
        submitted POs and material requests.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {boot.message}{" "}
        <button
          type="button"
          className={styles.btnGhost}
          onClick={() => void refresh()}
        >
          Retry
        </button>
      </p>
    );
  }

  return (
    <div className={styles.form}>
      <label className={styles.field}>
        Reject reason (required for reject)
        <input
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          disabled={busy}
          placeholder="Optional note stored on reject"
        />
      </label>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>
          Submitted purchase orders ({boot.purchaseOrders.length})
        </legend>
        {boot.purchaseOrders.length === 0 ? (
          <p className={styles.muted}>No POs awaiting approval.</p>
        ) : (
          <ul className={styles.list}>
            {boot.purchaseOrders.map(({ po, supplier, warehouse }) => (
              <li key={po.id}>
                <Link href={`/procurement/orders/${po.id}`}>
                  <strong>{po.document_number ?? po.id.slice(0, 8)}</strong>
                </Link>
                {po.is_blanket ? " · blanket" : ""}
                {" · "}
                {po.currency}
                <br />
                <span className={styles.muted}>
                  {supplier
                    ? `${supplier.code} — ${supplier.name}`
                    : "Supplier"}
                  {" · "}
                  {warehouse
                    ? `${warehouse.code} — ${warehouse.name}`
                    : "Warehouse"}
                  {po.submitted_at
                    ? ` · submitted ${po.submitted_at.slice(0, 10)}`
                    : ""}
                </span>
                <ProcurementProgressTracker
                  step={poSteps[po.id] ?? "submitted"}
                  documentLabel={po.document_number ?? po.id.slice(0, 8)}
                />
                <div className={styles.formActions}>
                  <button
                    type="button"
                    className={styles.btn}
                    disabled={busy}
                    onClick={() => void onApprovePo(po.id)}
                  >
                    Approve
                  </button>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy || !rejectReason.trim()}
                    onClick={() => void onRejectPo(po.id)}
                  >
                    Reject
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>
          Submitted material requests ({boot.materialRequests.length})
        </legend>
        {boot.materialRequests.length === 0 ? (
          <p className={styles.muted}>No MRs awaiting approval.</p>
        ) : (
          <ul className={styles.list}>
            {boot.materialRequests.map(({ mr, warehouse, lineCount }) => (
              <li key={mr.id}>
                <strong>{mr.document_number ?? mr.id.slice(0, 8)}</strong>
                {" · "}
                {lineCount} line{lineCount === 1 ? "" : "s"}
                <br />
                <span className={styles.muted}>
                  {warehouse
                    ? `${warehouse.code} — ${warehouse.name}`
                    : "Warehouse"}
                  {mr.needed_by ? ` · needed ${mr.needed_by}` : ""}
                  {mr.submitted_at
                    ? ` · submitted ${mr.submitted_at.slice(0, 10)}`
                    : ""}
                </span>
                <div className={styles.formActions}>
                  <button
                    type="button"
                    className={styles.btn}
                    disabled={busy}
                    onClick={() => void onApproveMr(mr.id)}
                  >
                    Approve
                  </button>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy || !rejectReason.trim()}
                    onClick={() => void onRejectMr(mr.id)}
                  >
                    Reject
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
