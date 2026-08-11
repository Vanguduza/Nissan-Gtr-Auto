"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import Link from "next/link";
import styles from "@/components/account.module.css";
import { ProcurementProgressTracker } from "@/components/procurement-progress-tracker";
import { createWebClient } from "@/lib/supabase";
import {
  listApprovedPosForGrn,
  listPoLines,
  requireSession,
} from "@/lib/preferred-po";

type PoOpt = {
  id: string;
  document_number: string | null;
  suppliers: { code: string; name: string } | null;
};

type LineRecv = {
  id: string;
  oem_part_number: string;
  description: string | null;
  qty_ordered: number;
  qty_received: number;
  unit_price: number;
  recvQty: string;
};

export function GoodsReceiptPanel() {
  const [auth, setAuth] = useState<"loading" | "ok" | "auth">("loading");
  const [pos, setPos] = useState<PoOpt[]>([]);
  const [poId, setPoId] = useState("");
  const [lines, setLines] = useState<LineRecv[]>([]);
  const [notes, setNotes] = useState("");
  const [invoiceFile, setInvoiceFile] = useState<File | null>(null);
  const [oemFast, setOemFast] = useState("");
  const [oemQty, setOemQty] = useState("1");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const loadPos = useCallback(async () => {
    const client = createWebClient();
    const session = await requireSession(client);
    if (!session.ok) {
      setAuth("auth");
      return;
    }
    setAuth("ok");
    const res = await listApprovedPosForGrn(client);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setPos(res.data);
    setPoId((p) => p || res.data[0]?.id || "");
  }, []);

  useEffect(() => {
    void loadPos();
  }, [loadPos]);

  useEffect(() => {
    if (!poId || auth !== "ok") return;
    void (async () => {
      const client = createWebClient();
      const res = await listPoLines(client, poId);
      if (!res.ok) {
        setMessage(res.error);
        return;
      }
      setLines(
        res.data.map((l) => ({
          ...l,
          recvQty: String(Math.max(0, l.qty_ordered - l.qty_received) || ""),
        })),
      );
    })();
  }, [poId, auth]);

  function applyOemFast() {
    const oem = oemFast.trim().toUpperCase();
    const q = Number(oemQty);
    if (!oem || !(q > 0)) {
      setMessage("Enter OEM part number and qty > 0.");
      return;
    }
    setLines((prev) => {
      const idx = prev.findIndex(
        (l) => l.oem_part_number.toUpperCase() === oem,
      );
      if (idx < 0) {
        setMessage(`OEM ${oem} not on this PO.`);
        return prev;
      }
      const next = [...prev];
      next[idx] = { ...next[idx]!, recvQty: String(q) };
      setMessage(`Set ${oem} receive qty to ${q}.`);
      return next;
    });
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    const payload = lines
      .filter((l) => Number(l.recvQty) > 0)
      .map((l) => ({
        purchase_order_line_id: l.id,
        qty: Number(l.recvQty),
        unit_cost: l.unit_price,
      }));
    if (payload.length === 0) {
      setBusy(false);
      setMessage("Enter receive qty on at least one line.");
      return;
    }

    let invoicePath: string | null = null;
    if (invoiceFile) {
      const path = `${poId}/${Date.now()}_${invoiceFile.name.replace(/[^\w.\-]+/g, "_")}`;
      const up = await client.storage
        .from("procurement-invoices")
        .upload(path, invoiceFile, { upsert: false });
      if (up.error) {
        setBusy(false);
        setMessage(up.error.message);
        return;
      }
      invoicePath = path;
    }

    const { data: grnId, error } = await client.rpc("create_goods_receipt", {
      p_purchase_order_id: poId,
      p_lines: payload,
      p_notes: notes || null,
    });
    if (error) {
      setBusy(false);
      setMessage(error.message);
      return;
    }

    if (invoicePath) {
      const att = await client.rpc("attach_goods_receipt_invoice", {
        p_goods_receipt_id: grnId as string,
        p_storage_path: invoicePath,
      });
      if (att.error) {
        setBusy(false);
        setMessage(`GRN created but invoice attach failed: ${att.error.message}`);
        return;
      }
    }

    const sub = await client.rpc("submit_goods_receipt", {
      p_goods_receipt_id: grnId as string,
    });
    setBusy(false);
    if (sub.error) {
      setMessage(`GRN ${String(grnId).slice(0, 8)} created; submit: ${sub.error.message}`);
      return;
    }
    setMessage(
      `GRN posted to WH1${invoicePath ? " with supplier invoice" : ""}.`,
    );
    setInvoiceFile(null);
    await loadPos();
  }

  if (auth === "loading") return <p className={styles.formStatus}>Loading…</p>;
  if (auth === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> to receive goods.
      </p>
    );
  }

  return (
    <div className={styles.panel}>
      <h2 className={styles.title}>Goods received (GRN)</h2>
      <p className={styles.lede}>
        Receive approved POs into WH1. Supplier invoice attaches as the GRN
        document. Fast path: OEM part number + qty.
      </p>
      <ProcurementProgressTracker step="funds_released" documentLabel="GRN" />
      {message ? <p className={styles.formStatus}>{message}</p> : null}

      <form className={styles.form} onSubmit={(e) => void onSubmit(e)}>
        <label className={styles.field}>
          Approved PO
          <select
            required
            value={poId}
            onChange={(e) => setPoId(e.target.value)}
          >
            {pos.map((p) => (
              <option key={p.id} value={p.id}>
                {p.document_number ?? p.id.slice(0, 8)} —{" "}
                {p.suppliers
                  ? `${p.suppliers.code} ${p.suppliers.name}`
                  : "supplier"}
              </option>
            ))}
          </select>
        </label>

        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Fast OEM receive</legend>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Part number
              <input
                value={oemFast}
                onChange={(e) => setOemFast(e.target.value)}
                placeholder="OEM"
              />
            </label>
            <label className={styles.field}>
              Qty received
              <input value={oemQty} onChange={(e) => setOemQty(e.target.value)} />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="button" className={styles.btn} onClick={applyOemFast}>
              Apply to matching line
            </button>
          </div>
        </fieldset>

        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>OEM</th>
                <th>Ordered</th>
                <th>Already recv</th>
                <th>Recv now</th>
              </tr>
            </thead>
            <tbody>
              {lines.map((l) => (
                <tr key={l.id}>
                  <td>
                    {l.oem_part_number}
                    {l.description ? ` — ${l.description}` : ""}
                  </td>
                  <td>{l.qty_ordered}</td>
                  <td>{l.qty_received}</td>
                  <td>
                    <input
                      value={l.recvQty}
                      onChange={(e) =>
                        setLines((prev) =>
                          prev.map((x) =>
                            x.id === l.id
                              ? { ...x, recvQty: e.target.value }
                              : x,
                          ),
                        )
                      }
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <label className={styles.field}>
          Supplier invoice (PDF/image) — acts as GRN attachment
          <input
            type="file"
            accept="application/pdf,image/*"
            onChange={(e) => setInvoiceFile(e.target.files?.[0] ?? null)}
          />
        </label>
        <label className={styles.field}>
          Notes
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            rows={2}
          />
        </label>
        <div className={styles.formActions}>
          <button type="submit" className={styles.btn} disabled={busy || !poId}>
            {busy ? "Posting…" : "Post GRN to stock"}
          </button>
          <Link href="/staff/warehouse/master-stock" className={styles.btnGhost}>
            Master stock
          </Link>
        </div>
      </form>
    </div>
  );
}
