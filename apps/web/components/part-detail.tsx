"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AddToCartButton } from "@/components/add-to-cart-button";
import { CatalogCanvasStub } from "@/components/catalog-canvas-stub";
import { PriceDual } from "@/components/price-dual";
import { StockBadge } from "@/components/stock-badge";
import { WhatsAppCta } from "@/components/whatsapp-cta";
import {
  fitmentLabel,
  loadCatalogProduct,
  type CatalogProduct,
} from "@/lib/catalog-product";
import { createWebClient } from "@/lib/supabase";
import styles from "@/app/(storefront)/parts/[oem]/pdp.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "missing" }
  | { kind: "ready"; product: CatalogProduct };

export function PartDetail({ oem }: { oem: string }) {
  const [status, setStatus] = useState<Status>({ kind: "loading" });

  useEffect(() => {
    let cancelled = false;

    async function run() {
      setStatus({ kind: "loading" });
      const client = createWebClient();
      if (!client) {
        if (!cancelled) {
          setStatus({
            kind: "error",
            message: "Supabase is not configured on this environment.",
          });
        }
        return;
      }

      const { data: sessionData } = await client.auth.getSession();
      if (!sessionData.session) {
        if (!cancelled) setStatus({ kind: "auth" });
        return;
      }

      const result = await loadCatalogProduct(client, oem);
      if (cancelled) return;

      if (!result.ok) {
        if (result.missing) {
          setStatus({ kind: "missing" });
          return;
        }
        setStatus({ kind: "error", message: result.error });
        return;
      }

      setStatus({ kind: "ready", product: result.data });
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, [oem]);

  if (status.kind === "loading") {
    return (
      <article className={styles.wrap}>
        <div className={styles.gallery}>
          <div className={styles.photo}>
            <span>Loading…</span>
          </div>
        </div>
        <div className={styles.info}>
          <p className={styles.muted}>Loading part {oem}…</p>
        </div>
      </article>
    );
  }

  if (status.kind === "auth") {
    const next = `/parts/${encodeURIComponent(oem)}`;
    return (
      <article className={styles.wrap}>
        <div className={styles.info}>
          <h1 className={styles.title}>Sign in to view part</h1>
          <p className={styles.muted}>
            Catalog and inventory details require a signed-in account.
          </p>
          <Link href={`/login?next=${encodeURIComponent(next)}`} className={styles.add}>
            Sign in
          </Link>
        </div>
      </article>
    );
  }

  if (status.kind === "error") {
    return (
      <article className={styles.wrap}>
        <div className={styles.info}>
          <h1 className={styles.title}>Part unavailable</h1>
          <p className={styles.muted} role="alert">
            {status.message}
          </p>
          <Link href="/catalog" className={styles.wish}>
            Back to catalog
          </Link>
        </div>
      </article>
    );
  }

  if (status.kind === "missing") {
    return (
      <article className={styles.wrap}>
        <div className={styles.info}>
          <h1 className={styles.title}>Part not found</h1>
          <p className={styles.muted}>
            No inventory or fitment row for OEM <code>{oem}</code>.
          </p>
          <div className={styles.actions}>
            <Link href="/search" className={styles.add}>
              Search
            </Link>
            <Link href="/catalog" className={styles.wish}>
              Catalog
            </Link>
          </div>
        </div>
      </article>
    );
  }

  const p = status.product;
  const fitmentSummary =
    p.fitments.length === 0
      ? null
      : [
          ...new Set(
            p.fitments.map(fitmentLabel).filter((label) => label.length > 0),
          ),
        ].slice(0, 6);

  return (
    <article className={styles.wrap}>
      <div className={styles.gallery} aria-label="Product media">
        <CatalogCanvasStub diagram={p.diagram} oem={p.oem} />
      </div>
      <div className={styles.info}>
        <p className={styles.brand}>{p.brand}</p>
        <h1 className={styles.title}>{p.name}</h1>
        <p className={styles.oem}>
          OEM <code>{p.oem}</code>
        </p>
        <StockBadge state={p.stock} />
        <div className={styles.priceRow}>
          {p.usd != null ? (
            <PriceDual usd={p.usd} zig={p.zig} />
          ) : (
            <p className={styles.muted}>Price on request — ask counter.</p>
          )}
        </div>
        {p.coreCharge > 0 ? (
          <p className={styles.core}>
            Core / deposit:{" "}
            <strong>USD {p.coreCharge.toFixed(2)}</strong> (separate cart line)
          </p>
        ) : null}
        <div className={styles.fitment}>
          {fitmentSummary && fitmentSummary.length > 0 ? (
            <>
              <p>
                Fitment: <strong>{fitmentSummary[0]}</strong>
                {fitmentSummary.length > 1
                  ? ` (+${fitmentSummary.length - 1} more)`
                  : null}
              </p>
              {fitmentSummary.length > 1 ? (
                <ul>
                  {fitmentSummary.slice(1).map((label) => (
                    <li key={label}>{label}</li>
                  ))}
                </ul>
              ) : null}
            </>
          ) : (
            <p className={styles.muted}>
              No fitment vehicles listed for this OEM yet.
            </p>
          )}
        </div>
        <div className={styles.actions}>
          <AddToCartButton oem={p.oem} />
          <Link href="/account/wishlist" className={styles.wish}>
            Wishlist
          </Link>
          <WhatsAppCta oem={p.oem} />
        </div>
        <section className={styles.block}>
          <h2>Specs</h2>
          {p.specs.length ? (
            <ul>
              {p.specs.map((s) => (
                <li key={s}>{s}</li>
              ))}
            </ul>
          ) : (
            <p className={styles.muted}>No specs listed on this part row.</p>
          )}
        </section>
        <section className={styles.block}>
          <h2>OE cross-refs</h2>
          {p.replaces.length ? (
            <ul>
              {p.replaces.map((r) => (
                <li key={r}>
                  Also replaces <code>{r}</code>
                </li>
              ))}
            </ul>
          ) : (
            <p className={styles.muted}>No alternate OE numbers listed.</p>
          )}
        </section>
        <section className={styles.block}>
          <h2>Alternatives</h2>
          {p.alternatives.length ? (
            <ul className={styles.alts}>
              {p.alternatives.map((a) => (
                <li key={a.oem}>
                  <Link href={`/parts/${encodeURIComponent(a.oem)}`}>
                    {a.oem} — {a.name}
                  </Link>
                </li>
              ))}
            </ul>
          ) : (
            <p className={styles.muted}>No same-PNC alternatives found.</p>
          )}
        </section>
        <section className={styles.block}>
          <h2>Reviews</h2>
          <p className={styles.muted}>No reviews yet — write one from My Account.</p>
        </section>
        <section className={styles.block}>
          <h2>Fulfillment</h2>
          <p>
            Choose <strong>click &amp; collect</strong> or{" "}
            <strong>dispatch</strong> at checkout.
          </p>
        </section>
      </div>
    </article>
  );
}
