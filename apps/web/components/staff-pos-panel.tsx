"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useRef, useState } from "react";
import styles from "@/components/account.module.css";
import {
  addCartLine,
  addCatalogPartToCart,
  checkoutPosCart,
  createPosCart,
  createPosScanSession,
  listSaleableWarehouses,
  loadPosCart,
  loadPosCartLines,
  requireSession,
  revokePosScanSession,
  searchPosCatalog,
  searchStockItems,
  type CurrencyCode,
  type FulfillmentMode,
  type PartHit,
  type PosCartLineRow,
  type PosCartRow,
  type PosScanSession,
  type SearchMode,
  type StockItemOption,
  type WarehouseOption,
} from "@/lib/staff-pos";
import { searchCustomers, type CustomerOption } from "@/lib/staff-finance";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; warehouses: WarehouseOption[] };

const CART_KEY = "gtr.staff.pos_cart_id";
const CART_POLL_MS = 4000;

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
  const [catalogMode, setCatalogMode] = useState<SearchMode>("part");
  const [catalogQuery, setCatalogQuery] = useState("");
  const [catalogHits, setCatalogHits] = useState<PartHit[]>([]);
  const [qty, setQty] = useState("1");
  const [receiptEmail, setReceiptEmail] = useState("");
  const [receiptWhatsapp, setReceiptWhatsapp] = useState("");
  const [pairing, setPairing] = useState<PosScanSession | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [customerQuery, setCustomerQuery] = useState("");
  const [customerHits, setCustomerHits] = useState<CustomerOption[]>([]);
  const [customerId, setCustomerId] = useState<string | null>(null);
  const [customerLabel, setCustomerLabel] = useState("");
  const pollRef = useRef<number | null>(null);

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

  // Poll open cart so companion phone scans appear without Realtime.
  useEffect(() => {
    if (pollRef.current != null) {
      window.clearInterval(pollRef.current);
      pollRef.current = null;
    }
    if (!cart?.id) return;
    pollRef.current = window.setInterval(() => {
      void refreshCart(cart.id);
    }, CART_POLL_MS);
    return () => {
      if (pollRef.current != null) {
        window.clearInterval(pollRef.current);
        pollRef.current = null;
      }
    };
  }, [cart?.id, refreshCart]);

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

  useEffect(() => {
    if (boot.kind !== "ready" || cart) return;
    const q = customerQuery.trim();
    if (q.length < 2) {
      setCustomerHits([]);
      return;
    }
    const t = window.setTimeout(() => {
      void (async () => {
        const client = createWebClient();
        if (!client) return;
        const res = await searchCustomers(client, q);
        if (!res.ok) {
          setMessage(res.error);
          setCustomerHits([]);
          return;
        }
        setCustomerHits(res.data);
      })();
    }, 250);
    return () => window.clearTimeout(t);
  }, [boot.kind, customerQuery, cart]);

  async function onCreateCart(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !warehouseId) return;
    setBusy(true);
    setMessage(null);
    setPairing(null);
    const res = await createPosCart(client, {
      warehouseId,
      currency,
      fulfillmentMode: fulfillment,
      customerId,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    writeStoredCartId(res.data);
    setMessage(
      `Cart created · ${res.data.slice(0, 8)}… · ${currency}${
        customerLabel ? ` · ${customerLabel}` : " · walk-in"
      } (no pairing required)`,
    );
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

  async function onCatalogSearch(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client) return;
    const q = catalogQuery.trim();
    if (q.length < 2) {
      setMessage("Enter at least 2 characters for catalog search.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await searchPosCatalog(client, catalogMode, q);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      setCatalogHits([]);
      return;
    }
    setCatalogHits(res.data);
    setMessage(
      res.data.length === 0
        ? `No catalog hits for “${q}”`
        : `${res.data.length} part(s) — Add to open cart`,
    );
  }

  async function onAddCatalogHit(hit: PartHit) {
    const client = createWebClient();
    if (!client || !cart) {
      setMessage("Create an open cart first.");
      return;
    }
    const n = Number(qty);
    if (!Number.isFinite(n) || n <= 0) {
      setMessage("Qty must be a positive number.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await addCatalogPartToCart(client, {
      cartId: cart.id,
      oemPartNumber: hit.oem_part_number,
      qty: n,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Added ${hit.oem_part_number}`);
    await refreshCart(cart.id);
  }

  async function onShowPairing() {
    const client = createWebClient();
    if (!client || !cart) return;
    setBusy(true);
    setMessage(null);
    const res = await createPosScanSession(client, cart.id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setPairing(res.data);
    setMessage(
      `Pairing code ${res.data.pairingCode} — claim on Android Scan companion (web does not scan)`,
    );
  }

  async function onRevokePairing() {
    const client = createWebClient();
    if (!client || !pairing) return;
    setBusy(true);
    setMessage(null);
    const res = await revokePosScanSession(client, pairing.sessionId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setPairing(null);
    setMessage("Companion pairing revoked");
  }

  async function onCheckout() {
    const client = createWebClient();
    if (!client || !cart) return;
    setBusy(true);
    setMessage(null);
    const res = await checkoutPosCart(client, {
      cartId: cart.id,
      receiptEmail: receiptEmail.trim() || null,
      receiptWhatsappE164: receiptWhatsapp.trim() || null,
      receiptPhoneE164: receiptWhatsapp.trim() || null,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    writeStoredCartId(null);
    setCart(null);
    setLines([]);
    setPairing(null);
    setReceiptEmail("");
    setReceiptWhatsapp("");
    setMessage(
      `Checked out · invoice ${res.data.invoiceId.slice(0, 8)}… · ${res.data.bindMessage}`,
    );
  }

  function clearCartLocal() {
    writeStoredCartId(null);
    setCart(null);
    setLines([]);
    setPairing(null);
    setMessage("Cleared local cart reference.");
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading POS…</p>;
  }

  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login?next=/staff/pos">Sign in</Link> with sales/admin staff
        to run POS carts. Roles enforced by RPCs.
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

  if (boot.warehouses.length === 0) {
    return (
      <p className={styles.muted}>
        No saleable warehouses available. Activate a non-quarantine warehouse
        before opening a POS cart.
      </p>
    );
  }

  const lineTotal = lines.reduce((sum, l) => sum + Number(l.line_total), 0);

  return (
    <div className={styles.form}>
      <p className={styles.storeLinkWrap}>
        <Link href="/catalog" className={styles.storeLink}>
          Open store catalog
        </Link>
        <span className={styles.muted}>
          {" "}
          — full browse in another view. Standalone till works without pairing.
        </span>
      </p>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>1 · Open cart</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Typed OEM / catalog only. Inventory QR scan: Android companion or
          management bridge — never the browser. Currency{" "}
          <code>USD</code> | <code>ZIG</code>.
        </p>
        {!cart ? (
          <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
            No open cart — choose warehouse and create one to add lines.
          </p>
        ) : null}
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
            <button
              type="submit"
              className={styles.btn}
              disabled={busy || !!cart || !warehouseId}
            >
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
        <legend className={styles.legend}>2 · Parts search</legend>
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
        <legend className={styles.legend}>3 · Catalog browse</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          <code>search_catalog</code> → resolve OEM in stock →{" "}
          <code>add_cart_line</code>. No scan session required.
        </p>
        <form onSubmit={(e) => void onCatalogSearch(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Mode
              <select
                value={catalogMode}
                onChange={(e) => setCatalogMode(e.target.value as SearchMode)}
                disabled={busy}
              >
                <option value="part">Part</option>
                <option value="vin">VIN</option>
                <option value="model">Model</option>
                <option value="pnc">PNC</option>
              </select>
            </label>
            <label className={styles.field}>
              Query
              <input
                value={catalogQuery}
                onChange={(e) => setCatalogQuery(e.target.value)}
                placeholder="OEM, VIN, model…"
                disabled={busy}
                autoComplete="off"
              />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy}>
              Search catalog
            </button>
          </div>
        </form>
        {catalogHits.length > 0 ? (
          <ul className={styles.list}>
            {catalogHits.map((hit, i) => (
              <li key={`${hit.oem_part_number}-${i}`}>
                <code>{hit.oem_part_number}</code>
                {hit.category_name ? ` — ${hit.category_name}` : ""}
                {hit.pnc_code ? ` · PNC ${hit.pnc_code}` : ""}{" "}
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy || !cart}
                  onClick={() => void onAddCatalogHit(hit)}
                >
                  Add
                </button>
              </li>
            ))}
          </ul>
        ) : null}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>4 · Optional phone companion</legend>
        <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
          Show a pairing code for the Android Scan companion. Web displays the
          code only — no HTML5 / camera QR scanning.
        </p>
        {pairing ? (
          <div style={{ marginBottom: "0.75rem" }}>
            <p
              style={{
                margin: 0,
                fontFamily: "var(--font-display)",
                fontSize: "2rem",
                fontWeight: 700,
                letterSpacing: "0.2em",
              }}
            >
              {pairing.pairingCode}
            </p>
            <p className={styles.muted}>
              Expires {new Date(pairing.expiresAt).toLocaleString()}
            </p>
          </div>
        ) : null}
        <div className={styles.formActions}>
          <button
            type="button"
            className={styles.btn}
            disabled={busy || !cart}
            onClick={() => void onShowPairing()}
          >
            Show pairing code
          </button>
          {pairing ? (
            <button
              type="button"
              className={styles.btnGhost}
              disabled={busy}
              onClick={() => void onRevokePairing()}
            >
              Revoke companion
            </button>
          ) : null}
          {cart ? (
            <button
              type="button"
              className={styles.btnGhost}
              disabled={busy}
              onClick={() => void refreshCart(cart.id)}
            >
              Refresh lines
            </button>
          ) : null}
        </div>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>5 · Lines · checkout</legend>
        {lines.length === 0 ? (
          <p className={styles.muted}>
            {cart
              ? "No lines yet — search an OEM or catalog above, or wait for companion scans."
              : "Create a cart first, then add OEM lines."}
          </p>
        ) : (
          <ul className={styles.list}>
            {lines.map((line) => (
              <li key={line.id}>
                <code>
                  {line.stock_items?.oem_part_number ??
                    line.stock_item_id.slice(0, 8)}
                </code>
                {line.stock_items?.description
                  ? ` — ${line.stock_items.description}`
                  : ""}
                {line.is_core_charge ? " · core" : ""} · qty {line.qty} ·{" "}
                {Number(line.unit_price).toFixed(2)} ×{" "}
                {Number(line.line_total).toFixed(2)} {cart?.currency ?? ""}
              </li>
            ))}
          </ul>
        )}
        {lines.length > 0 && cart ? (
          <p className={styles.muted} style={{ marginTop: "0.65rem" }}>
            Subtotal {lineTotal.toFixed(2)} {cart.currency}
          </p>
        ) : null}

        <p className={styles.muted} style={{ margin: "1rem 0 0.75rem" }}>
          Receipt contacts — WhatsApp and/or email for PDF. Unique match binds
          registered / trade account; otherwise walk-in.
        </p>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Receipt email
            <input
              type="email"
              value={receiptEmail}
              onChange={(e) => setReceiptEmail(e.target.value)}
              placeholder="optional"
              disabled={busy || !cart}
              autoComplete="email"
            />
          </label>
          <label className={styles.field}>
            WhatsApp (E.164)
            <input
              value={receiptWhatsapp}
              onChange={(e) => setReceiptWhatsapp(e.target.value)}
              placeholder="+263…"
              disabled={busy || !cart}
              autoComplete="tel"
            />
          </label>
        </div>
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
