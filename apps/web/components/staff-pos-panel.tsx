"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  addCartLine,
  checkoutPosCart,
  createPosCart,
  listSaleableWarehouses,
  loadPosCart,
  loadPosCartLines,
  requireSession,
  searchStockItems,
  type CurrencyCode,
  type FulfillmentMode,
  type PosCartLineRow,
  type PosCartRow,
  type StockItemOption,
  type WarehouseOption,
} from "@/lib/staff-pos";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; warehouses: WarehouseOption[] };

const CART_KEY = "gtr.staff.pos_cart_id";

function readStoredCartId(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(CART_KEY);
}

function writeStoredCartId(id: string | null) {
  if (typeof window === "undefined") return;
  if (id) window.localStorage.setItem(CART_KEY, id);
  else window.localStorage.removeItem(CART_KEY);
}

export function StaffPosPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [warehouseId, setWarehouseId] = useState("");
  const [currency, setCurrency] = useState<CurrencyCode>("USD");
  const [fulfillment, setFulfillment] = useState<FulfillmentMode>("immediate");
  const [cart, setCart] = useState<PosCartRow | null>(null);
  const [lines, setLines] = useState<PosCartLineRow[]>([]);
  const [itemQuery, setItemQuery] = useState("");
  const [hits, setHits] = useState<StockItemOption[]>([]);
  const [selectedItem, setSelectedItem] = useState<StockItemOption | null>(null);
  const [qty, setQty] = useState("1");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const refreshCart = useCallback(async (cartId: string) => {
    const client = createWebClient();
    if (!client) return;
    const [c, l] = await Promise.all([
      loadPosCart(client, cartId),
      loadPosCartLines(client, cartId),
    ]);
    if (!c.ok) {
      setMessage(c.error);
      setCart(null);
      setLines([]);
      writeStoredCartId(null);
      return;
    }
    if (!c.data || c.data.status !== "open") {
      setCart(null);
      setLines([]);
      writeStoredCartId(null);
      return;
    }
    if (!l.ok) {
      setMessage(l.error);
      return;
    }
    setCart(c.data);
    setLines(l.data);
    setWarehouseId(c.data.warehouse_id);
    setCurrency(c.data.currency);
    setFulfillment(c.data.fulfillment_mode);
  }, []);

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

    const wh = await listSaleableWarehouses(client);
    if (!wh.ok) {
      setBoot({ kind: "error", message: wh.error });
      return;
    }

    setBoot({ kind: "ready", warehouses: wh.data });
    setWarehouseId((prev) => prev || wh.data[0]?.id || "");

    const stored = readStoredCartId();
    if (stored) await refreshCart(stored);
  }, [refreshCart]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (boot.kind !== "ready") return;
    const q = itemQuery.trim();
    if (q.length < 2) {
      setHits([]);
      return;
    }
    const t = window.setTimeout(() => {
      void (async () => {
        const client = createWebClient();
        if (!client) return;
        const res = await searchStockItems(client, q);
        if (!res.ok) {
          setMessage(res.error);
          setHits([]);
          return;
        }
        setHits(res.data);
      })();
    }, 250);
    return () => window.clearTimeout(t);
  }, [boot.kind, itemQuery]);

  async function onCreateCart(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !warehouseId) return;
    setBusy(true);
    setMessage(null);
    const res = await createPosCart(client, {
      warehouseId,
      currency,
      fulfillmentMode: fulfillment,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    writeStoredCartId(res.data);
    setMessage(`Cart created · ${res.data.slice(0, 8)}… · ${currency}`);
    await refreshCart(res.data);
  }

  async function onAddLine(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !cart || !selectedItem) return;
    if (!selectedItem.base_uom_id) {
      setMessage("Selected item has no base UOM.");
      return;
    }
    const n = Number(qty);
    if (!Number.isFinite(n) || n <= 0) {
      setMessage("Qty must be a positive number.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await addCartLine(client, {
      cartId: cart.id,
      stockItemId: selectedItem.id,
      uomId: selectedItem.base_uom_id,
      qty: n,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Line added · ${selectedItem.oem_part_number}`);
    setItemQuery("");
    setSelectedItem(null);
    setHits([]);
    setQty("1");
    await refreshCart(cart.id);
  }

  async function onCheckout() {
    const client = createWebClient();
    if (!client || !cart) return;
    setBusy(true);
    setMessage(null);
    const res = await checkoutPosCart(client, cart.id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    writeStoredCartId(null);
    setCart(null);
    setLines([]);
    setMessage(`Checked out · invoice ${res.data.slice(0, 8)}…`);
  }

  function clearCartLocal() {
    writeStoredCartId(null);
    setCart(null);
    setLines([]);
    setMessage("Cleared local cart reference.");
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading POS…</p>;
  }

  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with sales/admin staff to run POS
        carts. Roles enforced by RPCs.
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

  const lineTotal = lines.reduce((sum, l) => sum + Number(l.line_total), 0);

  return (
    <div className={styles.form}>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>1 · Open cart</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Typed OEM / stock id only — no browser QR. Currency is set on the cart
          (<code>USD</code> | <code>ZIG</code>).
        </p>
        <form onSubmit={(e) => void onCreateCart(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Warehouse
              <select
                value={warehouseId}
                onChange={(e) => setWarehouseId(e.target.value)}
                disabled={busy || !!cart}
              >
                {boot.warehouses.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.code} — {w.name}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              Currency
              <select
                value={currency}
                onChange={(e) => setCurrency(e.target.value as CurrencyCode)}
                disabled={busy || !!cart}
              >
                <option value="USD">USD</option>
                <option value="ZIG">ZIG</option>
              </select>
            </label>
            <label className={styles.field}>
              Fulfillment
              <select
                value={fulfillment}
                onChange={(e) =>
                  setFulfillment(e.target.value as FulfillmentMode)
                }
                disabled={busy || !!cart}
              >
                <option value="immediate">Immediate</option>
                <option value="dispatch">Dispatch</option>
              </select>
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy || !!cart || !warehouseId}>
              Create cart
            </button>
            {cart ? (
              <button
                type="button"
                className={styles.btnGhost}
                disabled={busy}
                onClick={clearCartLocal}
              >
                Clear local
              </button>
            ) : null}
          </div>
        </form>
        {cart ? (
          <p className={styles.muted} style={{ marginTop: "0.65rem" }}>
            Open cart {cart.document_number ?? cart.id.slice(0, 8)} ·{" "}
            {cart.currency} · {cart.fulfillment_mode}
          </p>
        ) : null}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>2 · Add line</legend>
        <form onSubmit={(e) => void onAddLine(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              OEM / stock id
              <input
                value={itemQuery}
                onChange={(e) => {
                  setItemQuery(e.target.value);
                  setSelectedItem(null);
                }}
                placeholder="Type OEM…"
                disabled={busy || !cart}
                autoComplete="off"
              />
            </label>
            <label className={styles.field}>
              Qty
              <input
                type="number"
                min="0.0001"
                step="any"
                value={qty}
                onChange={(e) => setQty(e.target.value)}
                disabled={busy || !cart}
              />
            </label>
          </div>
          {hits.length > 0 && !selectedItem ? (
            <ul className={styles.list}>
              {hits.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={() => {
                      setSelectedItem(item);
                      setItemQuery(item.oem_part_number);
                      setHits([]);
                    }}
                  >
                    {item.oem_part_number}
                    {item.description ? ` — ${item.description}` : ""}
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
          {selectedItem ? (
            <p className={styles.muted} style={{ marginTop: "0.65rem" }}>
              Selected {selectedItem.oem_part_number}
              {!selectedItem.base_uom_id ? " · missing base UOM" : ""}
            </p>
          ) : null}
          <div className={styles.formActions}>
            <button
              type="submit"
              className={styles.btn}
              disabled={busy || !cart || !selectedItem}
            >
              Add line
            </button>
          </div>
        </form>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>3 · Lines · checkout</legend>
        {lines.length === 0 ? (
          <p className={styles.muted}>No lines yet.</p>
        ) : (
          <ul className={styles.list}>
            {lines.map((line) => (
              <li key={line.id}>
                <code>{line.stock_items?.oem_part_number ?? line.stock_item_id}</code>
                {line.is_core_charge ? " · core" : ""} · qty {line.qty} ·{" "}
                {Number(line.unit_price).toFixed(2)} × {Number(line.line_total).toFixed(2)}{" "}
                {cart?.currency ?? ""}
              </li>
            ))}
          </ul>
        )}
        {lines.length > 0 && cart ? (
          <p className={styles.muted} style={{ marginTop: "0.65rem" }}>
            Subtotal {lineTotal.toFixed(2)} {cart.currency}
          </p>
        ) : null}
        <div className={styles.formActions}>
          <button
            type="button"
            className={styles.btn}
            disabled={busy || !cart || lines.length === 0}
            onClick={() => void onCheckout()}
          >
            Checkout
          </button>
        </div>
      </fieldset>

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
