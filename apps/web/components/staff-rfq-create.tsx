"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  createRfq,
  loadSuppliers,
  loadWarehouses,
  requireSession,
  searchStockItems,
  type RfqCreateLine,
  type StockItemOption,
  type SupplierOption,
  type WarehouseOption,
} from "@/lib/rfq-portal";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      warehouses: WarehouseOption[];
      suppliers: SupplierOption[];
    };

type LineDraft = {
  stock_item_id: string;
  oem: string;
  uom_id: string;
  qty: string;
};

export function StaffRfqCreate() {
  const router = useRouter();
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [warehouseId, setWarehouseId] = useState("");
  const [neededBy, setNeededBy] = useState("");
  const [notes, setNotes] = useState("");
  const [supplierIds, setSupplierIds] = useState<string[]>([]);
  const [lines, setLines] = useState<LineDraft[]>([
    { stock_item_id: "", oem: "", uom_id: "", qty: "1" },
  ]);
  const [itemQuery, setItemQuery] = useState("");
  const [itemHits, setItemHits] = useState<StockItemOption[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

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
    const [wh, sup] = await Promise.all([
      loadWarehouses(client),
      loadSuppliers(client),
    ]);
    if (!wh.ok) {
      setBoot({ kind: "error", message: wh.error });
      return;
    }
    if (!sup.ok) {
      setBoot({ kind: "error", message: sup.error });
      return;
    }
    setBoot({ kind: "ready", warehouses: wh.data, suppliers: sup.data });
    if (wh.data[0] && !warehouseId) setWarehouseId(wh.data[0].id);
  }, [warehouseId]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    const client = createWebClient();
    if (!client || itemQuery.trim().length < 2) {
      setItemHits([]);
      return;
    }
    let cancelled = false;
    void (async () => {
      const res = await searchStockItems(client, itemQuery);
      if (!cancelled && res.ok) setItemHits(res.data);
    })();
    return () => {
      cancelled = true;
    };
  }, [itemQuery]);

  function toggleSupplier(id: string) {
    setSupplierIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id],
    );
  }

  function pickItem(item: StockItemOption, index: number) {
    setLines((prev) =>
      prev.map((line, i) =>
        i === index
          ? {
              ...line,
              stock_item_id: item.id,
              oem: item.oem_part_number,
              uom_id: item.base_uom_id ?? line.uom_id,
            }
          : line,
      ),
    );
    setItemQuery("");
    setItemHits([]);
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      return;
    }

    const parsed: RfqCreateLine[] = [];
    for (const line of lines) {
      const qty = Number(line.qty);
      if (!line.stock_item_id || !line.uom_id || !(qty > 0)) {
        setMessage("Each line needs stock item, UOM, and qty > 0.");
        return;
      }
      parsed.push({
        stock_item_id: line.stock_item_id,
        uom_id: line.uom_id,
        qty,
      });
    }
    if (!warehouseId || !neededBy || supplierIds.length === 0) {
      setMessage("Warehouse, needed-by date, and at least one supplier are required.");
      return;
    }

    setBusy(true);
    const res = await createRfq(client, {
      warehouseId,
      neededBy,
      supplierIds,
      lines: parsed,
      notes: notes.trim() || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    router.push(`/procurement/rfqs/${res.data}`);
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading form…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> as staff to create an RFQ.
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
    <form className={styles.form} onSubmit={onSubmit}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Header</legend>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Warehouse
            <select
              value={warehouseId}
              onChange={(e) => setWarehouseId(e.target.value)}
              required
            >
              {boot.warehouses.length === 0 ? (
                <option value="">No warehouses visible</option>
              ) : (
                boot.warehouses.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.code} — {w.name}
                  </option>
                ))
              )}
            </select>
          </label>
          <label className={styles.field}>
            Needed by
            <input
              type="date"
              value={neededBy}
              onChange={(e) => setNeededBy(e.target.value)}
              required
            />
          </label>
          <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
            Notes
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={2}
            />
          </label>
        </div>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Invite suppliers</legend>
        {boot.suppliers.length === 0 ? (
          <p className={styles.muted}>No active suppliers visible to this session.</p>
        ) : (
          <ul className={styles.list}>
            {boot.suppliers.map((s) => (
              <li key={s.id}>
                <label className={styles.checkField}>
                  <input
                    type="checkbox"
                    checked={supplierIds.includes(s.id)}
                    onChange={() => toggleSupplier(s.id)}
                  />
                  {s.code} — {s.name}
                </label>
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Lines</legend>
        {lines.map((line, index) => (
          <div key={index} className={styles.formGrid} style={{ marginBottom: "0.85rem" }}>
            <label className={styles.field}>
              Stock item
              <input
                value={line.oem || line.stock_item_id}
                readOnly
                placeholder="Pick from search"
                required
              />
            </label>
            <label className={styles.field}>
              UOM id
              <input
                value={line.uom_id}
                onChange={(e) =>
                  setLines((prev) =>
                    prev.map((l, i) =>
                      i === index ? { ...l, uom_id: e.target.value } : l,
                    ),
                  )
                }
                required
              />
            </label>
            <label className={styles.field}>
              Qty
              <input
                type="number"
                min="0.0001"
                step="any"
                value={line.qty}
                onChange={(e) =>
                  setLines((prev) =>
                    prev.map((l, i) =>
                      i === index ? { ...l, qty: e.target.value } : l,
                    ),
                  )
                }
                required
              />
            </label>
            {index === lines.length - 1 ? (
              <label className={styles.field}>
                Search OEM / description
                <input
                  value={itemQuery}
                  onChange={(e) => setItemQuery(e.target.value)}
                  placeholder="Type 2+ characters"
                />
              </label>
            ) : null}
          </div>
        ))}
        {itemHits.length > 0 ? (
          <ul className={styles.list}>
            {itemHits.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className={styles.btnGhost}
                  onClick={() => pickItem(item, lines.length - 1)}
                >
                  {item.oem_part_number}
                  {item.description ? ` — ${item.description}` : ""}
                </button>
              </li>
            ))}
          </ul>
        ) : null}
        <button
          type="button"
          className={styles.btnGhost}
          onClick={() =>
            setLines((prev) => [
              ...prev,
              { stock_item_id: "", oem: "", uom_id: "", qty: "1" },
            ])
          }
        >
          Add line
        </button>
      </fieldset>

      <div className={styles.formActions}>
        <button className={styles.btn} type="submit" disabled={busy}>
          {busy ? "Creating…" : "Create draft RFQ"}
        </button>
        {message ? <p className={styles.formStatus}>{message}</p> : null}
      </div>
    </form>
  );
}
