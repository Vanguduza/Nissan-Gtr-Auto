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
  createCustomerPaynowIntent,
  ensureOpenCart,
  formatMoney,
  fulfillmentLabel,
  loadCartLines,
  loadOpenCart,
  requireSession,
  type CartLineRow,
  type CartRow,
  zigExchangeRate,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
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

type Currency = "USD" | "ZIG";
type Fulfillment = "immediate" | "dispatch";
type Tender = "cash" | "contipay" | "paynow";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | {
      kind: "ready";
      cart: CartRow | null;
      lines: CartLineRow[];
    };

export function CartCheckout() {
  const router = useRouter();
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [fulfillment, setFulfillment] = useState<Fulfillment>("immediate");
  const [currency, setCurrency] = useState<Currency>("USD");
  const [tender, setTender] = useState<Tender>("cash");
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

    const cart = await loadOpenCart(client);
    if (!cart.ok) {
      setStatus({ kind: "error", message: cart.error });
      return;
    }

    if (cart.data) {
      setFulfillment(cart.data.fulfillment_mode);
      setCurrency(cart.data.currency === "ZIG" ? "ZIG" : "USD");
      const lines = await loadCartLines(client, cart.data.id);
      if (!lines.ok) {
        setStatus({ kind: "error", message: lines.error });
        return;
      }
      setStatus({ kind: "ready", cart: cart.data, lines: lines.data });
      return;
    }

    setStatus({ kind: "ready", cart: null, lines: [] });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const total = useMemo(() => {
    if (status.kind !== "ready") return 0;
    return status.lines.reduce((sum, line) => sum + Number(line.line_total), 0);
  }, [status]);

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
        currency,
        fulfillmentMode: fulfillment,
        exchangeRate: currency === "ZIG" ? zigExchangeRate() : 1,
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

    if (tender === "contipay") {
      const intent = await createCustomerContipayIntent(client, invoice.data);
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
        `ContiPay intent ${intent.data.intentId} created. Settlement confirms via webhook — or open the order to retry when a checkout URL is available.`,
      );
    } else if (tender === "paynow") {
      const intent = await createCustomerPaynowIntent(client, invoice.data);
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
        `Paynow intent ${intent.data.intentId} created. Settlement confirms via webhook — or open the order to retry when a checkout URL is available.`,
      );
    }

    setBusy(false);
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
          Sign in to open a storefront cart and checkout.
        </p>
        <Link href="/login" className={styles.button}>
          Sign in
        </Link>
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

  const { cart, lines } = status;
  const displayCurrency = cart?.currency ?? currency;

  return (
    <div className={styles.page}>
      <CartTitle />
      <p className={styles.lede}>
        Checkout posts your invoice via customer cart RPCs. ContiPay and Paynow
        create intents on your unpaid invoice — settle stays server-side.
      </p>

      {cart ? (
        <p className={styles.muted}>
          Cart <code className={styles.sku}>{cart.document_number ?? cart.id}</code>
          {" · "}
          {fulfillmentLabel(cart.fulfillment_mode)}
          {" · "}
          {cart.currency}
          {cart.currency === "ZIG"
            ? ` @ ${cart.exchange_rate_applied}`
            : null}
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
              <th>Price</th>
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
                      {formatMoney(Number(line.line_total), displayCurrency)}
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
            {formatMoney(total, displayCurrency)}
          </strong>
        </p>
      ) : null}

      {!cart ? (
        <>
          <section className={styles.fulfill} aria-labelledby="currency-heading">
            <h2 id="currency-heading" className={styles.fulfillTitle}>
              Currency
            </h2>
            <div className={styles.fulfillOptions}>
              <label className={styles.fulfillCard}>
                <input
                  type="radio"
                  name="currency"
                  checked={currency === "USD"}
                  onChange={() => setCurrency("USD")}
                />
                <span>
                  <strong>USD</strong>
                  <span className={styles.muted}>Exchange rate 1</span>
                </span>
              </label>
              <label className={styles.fulfillCard}>
                <input
                  type="radio"
                  name="currency"
                  checked={currency === "ZIG"}
                  onChange={() => setCurrency("ZIG")}
                />
                <span>
                  <strong>ZiG</strong>
                  <span className={styles.muted}>
                    Rate {zigExchangeRate()} (NEXT_PUBLIC_ZIG_EXCHANGE_RATE)
                  </span>
                </span>
              </label>
            </div>
          </section>

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
                  <span className={styles.muted}>
                    Courier to your address
                  </span>
                </span>
              </label>
            </div>
          </section>
        </>
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
        </div>
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
