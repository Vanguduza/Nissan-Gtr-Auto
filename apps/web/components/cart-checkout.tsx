"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  iconSizeMd,
  iconStroke,
  ShoppingCart,
} from "@/components/icons";
import {
  checkoutCustomerCart,
  createCustomerContipayIntent,
  createCustomerEcocashIntent,
  createCustomerPaynowIntent,
  ensureOpenCart,
  fetchZigExchangeRate,
  formatMoney,
  fulfillmentLabel,
  loadCartLines,
  loadOpenCart,
  loadOwnCustomer,
  requireSession,
  type CartLineRow,
  type CartRow,
  type CustomerRow,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import { buildCheckoutDisplay, type PspMethod } from "@gtr/payments";
import { toAmountMinor } from "@gtr/shared";
import styles from "@/app/(storefront)/page.module.css";

function CartTitle() {
  return (
    <h1 className={styles.title}>
      <span className={styles.titleIcon} aria-hidden>
        <ShoppingCart size={iconSizeMd} strokeWidth={iconStroke} />
      </span>
      Cart
    </h1>
  );
}

type Fulfillment = "immediate" | "dispatch";
type Tender = "cash" | "contipay" | "paynow" | "ecocash";
type SettleCurrency = "USD" | "ZIG";
type EcoCashMode = "saved" | "other";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      cart: CartRow | null;
      lines: CartLineRow[];
      customer: CustomerRow | null;
    };

export function CartCheckout() {
  const router = useRouter();
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [fulfillment, setFulfillment] = useState<Fulfillment>("immediate");
  const [settleCurrency, setSettleCurrency] = useState<SettleCurrency>("USD");
  const [zigRate, setZigRate] = useState<number>(1);
  const [tender, setTender] = useState<Tender>("cash");
  const [ecocashMode, setEcocashMode] = useState<EcoCashMode>("saved");
  const [ecocashOther, setEcocashOther] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setStatus({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }

    const session = await requireSession(client);
    if (!session.ok) {
      setStatus({ kind: "auth" });
      return;
    }

    const rate = await fetchZigExchangeRate(client);
    setZigRate(rate);

    const cart = await loadOpenCart(client);
    if (!cart.ok) {
      setStatus({ kind: "error", message: cart.error });
      return;
    }

    const customer = await loadOwnCustomer(client);
    if (!customer.ok) {
      setStatus({ kind: "error", message: customer.error });
      return;
    }

    if (cart.data) {
      setFulfillment(cart.data.fulfillment_mode);
      const lines = await loadCartLines(client, cart.data.id);
      if (!lines.ok) {
        setStatus({ kind: "error", message: lines.error });
        return;
      }
      setStatus({
        kind: "ready",
        cart: cart.data,
        lines: lines.data,
        customer: customer.data,
      });
      return;
    }

    setStatus({
      kind: "ready",
      cart: null,
      lines: [],
      customer: customer.data,
    });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const totalUsd = useMemo(() => {
    if (status.kind !== "ready") return 0;
    return status.lines.reduce((sum, line) => sum + Number(line.line_total), 0);
  }, [status]);

  const zigTotal = useMemo(
    () => Math.round(totalUsd * zigRate * 100) / 100,
    [totalUsd, zigRate],
  );

  const checkoutDisplay = useMemo(() => {
    const usdMinor = toAmountMinor(totalUsd, "USD");
    const method: PspMethod =
      tender === "ecocash"
        ? "ecocash"
        : tender === "contipay"
          ? "contipay"
          : tender === "paynow"
            ? "paynow"
            : "cash";
    try {
      return buildCheckoutDisplay({
        usdMinor,
        payMethod: settleCurrency === "ZIG" || method === "ecocash" ? "ecocash" : method,
        zigRatePerUsd: zigRate,
        fxRateId: null,
      });
    } catch {
      return buildCheckoutDisplay({
        usdMinor,
        payMethod: "cash",
        zigRatePerUsd: zigRate,
        fxRateId: null,
      });
    }
  }, [totalUsd, zigRate, tender, settleCurrency]);

  const zigTotal = useMemo(() => {
    const minor =
      checkoutDisplay.payCurrency === "ZIG"
        ? checkoutDisplay.payable.amountMinor
        : checkoutDisplay.indicativeZigMinor ?? 0n;
    return Number(minor) / 100;
  }, [checkoutDisplay]);

  async function onCheckout() {
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }

    let cartId = status.kind === "ready" ? status.cart?.id : null;
    if (!cartId) {
      const created = await ensureOpenCart(client, {
        currency: "USD",
        fulfillmentMode: fulfillment,
        exchangeRate: 1,
      });
      if (!created.ok) {
        setMessage(created.error);
        setBusy(false);
        return;
      }
      cartId = created.data.id;
    }

    const linesResult = await loadCartLines(client, cartId);
    if (!linesResult.ok) {
      setMessage(linesResult.error);
      setBusy(false);
      return;
    }
    if (!linesResult.data.length) {
      setMessage("Add a part before checkout.");
      setBusy(false);
      return;
    }

    const invoice = await checkoutCustomerCart(client, cartId);
    if (!invoice.ok) {
      setMessage(invoice.error);
      setBusy(false);
      return;
    }

    const { data: invRow } = await client
      .from("sales_invoices")
      .select("status, total")
      .eq("id", invoice.data)
      .maybeSingle();
    if (invRow?.status === "on_hold") {
      setBusy(false);
      setMessage(
        "Order created on hold (credit hold or over credit limit). Sales must clear it before fulfillment. Opening order…",
      );
      router.push(`/account/orders/${invoice.data}`);
      return;
    }

    const invTotal = Number(invRow?.total ?? totalUsd);
    const rate = await fetchZigExchangeRate(client);
    const settlement =
      settleCurrency === "ZIG"
        ? {
            currency: "ZIG" as const,
            amount: Math.round(invTotal * rate * 100) / 100,
            exchangeRate: rate,
          }
        : undefined;

    if (tender === "contipay") {
      const intent = await createCustomerContipayIntent(
        client,
        invoice.data,
        "ecocash",
        settlement,
      );
      if (!intent.ok) {
        setMessage(
          `Invoice created, but ContiPay failed: ${intent.error}. Pay from order page.`,
        );
        setBusy(false);
        router.push(`/account/orders/${invoice.data}`);
        return;
      }
      if (intent.data.checkoutUrl) {
        setBusy(false);
        window.location.assign(intent.data.checkoutUrl);
        return;
      }
      setMessage(
        settlement
          ? `Invoice created. ContiPay intent ready (settle ZiG ${settlement.amount.toFixed(2)} @ ${rate}). Opening order…`
          : "Invoice created. ContiPay intent ready. Opening order…",
      );
      setBusy(false);
      router.push(`/account/orders/${invoice.data}`);
      return;
    }

    if (tender === "paynow") {
      const intent = await createCustomerPaynowIntent(
        client,
        invoice.data,
        "ecocash",
        settlement,
      );
      if (!intent.ok) {
        setMessage(
          `Invoice created, but Paynow failed: ${intent.error}. Pay from order page.`,
        );
        setBusy(false);
        router.push(`/account/orders/${invoice.data}`);
        return;
      }
      if (intent.data.checkoutUrl) {
        setBusy(false);
        window.location.assign(intent.data.checkoutUrl);
        return;
      }
      setMessage(
        settlement
          ? `Invoice created. Paynow intent ready (settle ZiG ${settlement.amount.toFixed(2)} @ ${rate}). Opening order…`
          : "Invoice created. Paynow intent ready. Opening order…",
      );
      setBusy(false);
      router.push(`/account/orders/${invoice.data}`);
      return;
    }

    if (tender === "ecocash") {
      const intent = await createCustomerEcocashIntent(client, invoice.data, {
        payerMode: ecocashMode,
        payerMsisdn: ecocashMode === "other" ? ecocashOther : null,
        settlement,
      });
      if (!intent.ok) {
        setMessage(
          `Invoice created, but EcoCash direct failed: ${intent.error}. Pay from order page.`,
        );
        setBusy(false);
        router.push(`/account/orders/${invoice.data}`);
        return;
      }
      setMessage(
        intent.data.message ??
          "Invoice created. EcoCash PIN request sent — approve on the EcoCash handset.",
      );
      setBusy(false);
      router.push(`/account/orders/${invoice.data}`);
      return;
    }

    setBusy(false);
    setMessage(
      settlement
        ? `Order placed. Pay ZiG ${settlement.amount.toFixed(2)} (rate ${rate} ZiG/USD) at counter or transfer.`
        : "Order placed. Pay USD at counter or transfer — invoice stays open.",
    );
    router.push(`/account/orders/${invoice.data}`);
  }

  if (status.kind === "loading") {
    return (
      <div className={styles.page}>
        <CartTitle />
        <p className={styles.lede}>Loading cart…</p>
      </div>
    );
  }
  if (status.kind === "auth") {
    return (
      <div className={styles.page}>
        <CartTitle />
        <p className={styles.lede}>
          <Link href="/login">Sign in</Link> to view your cart and checkout.
        </p>
      </div>
    );
  }
  if (status.kind === "error") {
    return (
      <div className={styles.page}>
        <CartTitle />
        <p className={styles.lede} role="alert">
          {status.message}
        </p>
        <button type="button" className={styles.button} onClick={() => void refresh()}>
          Retry
        </button>
      </div>
    );
  }

  const { cart, lines, customer } = status;
  const creditHold = !!customer?.credit_hold;
  const creditLimit = Number(customer?.credit_limit ?? 0);
  const openBalance = Number(customer?.open_balance ?? 0);
  const projectedOpen = openBalance + totalUsd;
  const overLimit = creditLimit > 0 && projectedOpen > creditLimit;

  return (
    <div className={styles.page}>
      <CartTitle />
      <p className={styles.lede}>
        Prices and cart totals are in USD. After the total is calculated you can
        choose to settle in ZiG at today&apos;s finance rate.
      </p>

      {customer && (creditHold || overLimit) ? (
        <p className={styles.lede} role="status">
          {creditHold
            ? "Your account is on credit hold. Checkout will still create an invoice, but it will remain on_hold until sales clears the hold."
            : `This cart would put you over your credit limit (${formatMoney(creditLimit, "USD")}; open ${formatMoney(openBalance, "USD")} + cart ${formatMoney(totalUsd, "USD")}). Checkout will post on_hold.`}
          {" "}
          See <Link href="/b2b">B2B credit</Link>.
        </p>
      ) : null}

      {cart ? (
        <p className={styles.muted}>
          Cart <code className={styles.sku}>{cart.document_number ?? cart.id}</code>
          {" · "}
          {fulfillmentLabel(cart.fulfillment_mode)}
          {" · USD"}
        </p>
      ) : (
        <p className={styles.muted}>
          No open cart yet — add a part from search or choose options below.
        </p>
      )}

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Part</th>
              <th>Qty</th>
              <th>Price (USD)</th>
            </tr>
          </thead>
          <tbody>
            {lines.length === 0 ? (
              <tr>
                <td colSpan={3} className={styles.muted}>
                  Cart is empty.
                </td>
              </tr>
            ) : (
              lines.map((line) => (
                <tr key={line.id}>
                  <td>
                    <code className={styles.sku}>
                      {line.stock_items?.oem_part_number ?? line.stock_item_id}
                    </code>
                    <br />
                    {line.is_core_charge
                      ? "Core / deposit"
                      : (line.stock_items?.description ?? "Part")}
                  </td>
                  <td>{line.qty}</td>
                  <td>
                    <span className={styles.moneyUsd}>
                      {formatMoney(Number(line.line_total), "USD")}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {lines.length > 0 ? (
        <p className={styles.lede}>
          Total{" "}
          <strong className={styles.moneyUsd}>
            {formatMoney(totalUsd, "USD")}
          </strong>
        </p>
      ) : null}

      {!cart ? (
        <section className={styles.fulfill} aria-labelledby="fulfill-heading">
          <h2 id="fulfill-heading" className={styles.fulfillTitle}>
            How do you want it?
          </h2>
          <div className={styles.fulfillOptions}>
            <label className={styles.fulfillCard}>
              <input
                type="radio"
                name="fulfill"
                checked={fulfillment === "immediate"}
                onChange={() => setFulfillment("immediate")}
              />
              <span>
                <strong>Click &amp; collect</strong>
                <span className={styles.muted}>
                  Pick up at Harare counter when ready
                </span>
              </span>
            </label>
            <label className={styles.fulfillCard}>
              <input
                type="radio"
                name="fulfill"
                checked={fulfillment === "dispatch"}
                onChange={() => setFulfillment("dispatch")}
              />
              <span>
                <strong>Nationwide dispatch</strong>
                <span className={styles.muted}>Courier to your address</span>
              </span>
            </label>
          </div>
        </section>
      ) : null}

      {lines.length > 0 ? (
        <section className={styles.fulfill} aria-labelledby="settle-heading">
          <h2 id="settle-heading" className={styles.fulfillTitle}>
            Settle in
          </h2>
          <div className={styles.fulfillOptions}>
            <label className={styles.fulfillCard}>
              <input
                type="radio"
                name="settle"
                checked={settleCurrency === "USD"}
                onChange={() => setSettleCurrency("USD")}
              />
              <span>
                <strong>USD</strong>
                <span className={styles.muted}>
                  {formatMoney(totalUsd, "USD")}
                </span>
              </span>
            </label>
            <label className={styles.fulfillCard}>
              <input
                type="radio"
                name="settle"
                checked={settleCurrency === "ZIG"}
                onChange={() => setSettleCurrency("ZIG")}
              />
              <span>
                <strong>ZiG</strong>
                <span className={styles.muted}>
                  ≈{" "}
                  {formatMoney(
                    Number(
                      checkoutDisplay.payCurrency === "ZIG"
                        ? checkoutDisplay.payable.amountMinor
                        : checkoutDisplay.indicativeZigMinor ?? 0n,
                    ) / 100,
                    "ZIG",
                  )}{" "}
                  @ {zigRate} ZiG per USD (ops daily rate)
                </span>
              </span>
            </label>
          </div>
        </section>
      ) : null}

      <section className={styles.fulfill} aria-labelledby="pay-heading">
        <h2 id="pay-heading" className={styles.fulfillTitle}>
          How will you pay?
        </h2>
        <div className={styles.fulfillOptions}>
          <label className={styles.fulfillCard}>
            <input
              type="radio"
              name="tender"
              checked={tender === "cash"}
              onChange={() => setTender("cash")}
            />
            <span>
              <strong>Cash / bank</strong>
              <span className={styles.muted}>
                Pay at counter or transfer — invoice stays open
              </span>
            </span>
          </label>
          <label className={styles.fulfillCard}>
            <input
              type="radio"
              name="tender"
              checked={tender === "contipay"}
              onChange={() => setTender("contipay")}
            />
            <span>
              <strong>ContiPay</strong>
              <span className={styles.muted}>EcoCash, Visa, ZimSwitch</span>
            </span>
          </label>
          <label className={styles.fulfillCard}>
            <input
              type="radio"
              name="tender"
              checked={tender === "paynow"}
              onChange={() => setTender("paynow")}
            />
            <span>
              <strong>Paynow</strong>
              <span className={styles.muted}>Mobile money &amp; card</span>
            </span>
          </label>
          <label className={styles.fulfillCard}>
            <input
              type="radio"
              name="tender"
              checked={tender === "ecocash"}
              onChange={() => setTender("ecocash")}
            />
            <span>
              <strong>EcoCash direct</strong>
              <span className={styles.muted}>
                PIN on phone — not via ContiPay/Paynow
              </span>
            </span>
          </label>
        </div>
        {tender === "ecocash" ? (
          <div className={styles.fulfillOptions} style={{ marginTop: "0.75rem" }}>
            <label className={styles.fulfillCard}>
              <input
                type="radio"
                name="ecocashMode"
                checked={ecocashMode === "saved"}
                onChange={() => setEcocashMode("saved")}
              />
              <span>
                <strong>Use saved / profile number</strong>
                <span className={styles.muted}>Fastest if your EcoCash is on file</span>
              </span>
            </label>
            <label className={styles.fulfillCard}>
              <input
                type="radio"
                name="ecocashMode"
                checked={ecocashMode === "other"}
                onChange={() => setEcocashMode("other")}
              />
              <span>
                <strong>Different EcoCash number</strong>
                <span className={styles.muted}>07… or +263…</span>
              </span>
            </label>
            {ecocashMode === "other" ? (
              <input
                type="tel"
                className={styles.input ?? undefined}
                placeholder="EcoCash number"
                value={ecocashOther}
                onChange={(e) => setEcocashOther(e.target.value)}
                style={{ width: "100%", padding: "0.5rem", marginTop: "0.5rem" }}
              />
            ) : null}
          </div>
        ) : null}
      </section>

      <div style={{ display: "flex", flexWrap: "wrap", gap: "0.75rem" }}>
        <button
          type="button"
          className={styles.button}
          disabled={busy || lines.length === 0}
          onClick={() => void onCheckout()}
        >
          {busy ? "Checking out…" : "Checkout"}
        </button>
        <Link href="/search" className={styles.button} style={{ background: "var(--gtr-steel)" }}>
          Continue shopping
        </Link>
      </div>
      {message ? (
        <p className={styles.lede} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
