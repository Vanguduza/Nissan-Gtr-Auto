import Link from "next/link";
import { notFound } from "next/navigation";
import { PriceDual } from "@/components/price-dual";
import { StockBadge } from "@/components/stock-badge";
import { WhatsAppCta } from "@/components/whatsapp-cta";
import { DEMO_PRODUCTS, findProduct } from "@/lib/shop-demo";
import styles from "./pdp.module.css";

export function generateStaticParams() {
  return DEMO_PRODUCTS.map((p) => ({ oem: p.oem }));
}

export async function generateMetadata({
  params,
}: {
  params: Promise<{ oem: string }>;
}) {
  const { oem } = await params;
  const p = findProduct(oem);
  return { title: p ? `${p.oem} · ${p.name}` : "Part" };
}

export default async function PartPage({
  params,
}: {
  params: Promise<{ oem: string }>;
}) {
  const { oem } = await params;
  const p = findProduct(oem);
  if (!p) notFound();

  const alts = DEMO_PRODUCTS.filter((x) => x.oem !== p.oem).slice(0, 3);

  return (
    <article className={styles.wrap}>
      <div className={styles.gallery} aria-label="Product photos">
        <div className={styles.photo}>
          <span>Photo stub</span>
        </div>
      </div>
      <div className={styles.info}>
        <p className={styles.brand}>{p.brand}</p>
        <h1 className={styles.title}>{p.name}</h1>
        <p className={styles.oem}>
          OEM <code>{p.oem}</code>
        </p>
        <StockBadge state={p.stock} />
        <div className={styles.priceRow}>
          <PriceDual usd={p.usd} zig={p.zig} />
        </div>
        {p.coreCharge > 0 ? (
          <p className={styles.core}>
            Core / deposit:{" "}
            <strong>USD {p.coreCharge.toFixed(2)}</strong> (separate cart line)
          </p>
        ) : null}
        <p className={styles.fitment}>
          Fitment vs garage vehicle: <strong>Compatible (demo)</strong>
        </p>
        <div className={styles.actions}>
          <Link href="/cart" className={styles.add}>
            Add to cart
          </Link>
          <Link href="/account/wishlist" className={styles.wish}>
            Wishlist
          </Link>
          <WhatsAppCta oem={p.oem} />
        </div>
        <section className={styles.block}>
          <h2>Specs</h2>
          <ul>
            {p.specs.map((s) => (
              <li key={s}>{s}</li>
            ))}
          </ul>
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
          <ul className={styles.alts}>
            {alts.map((a) => (
              <li key={a.oem}>
                <Link href={`/parts/${encodeURIComponent(a.oem)}`}>
                  {a.oem} — {a.name}
                </Link>
              </li>
            ))}
          </ul>
        </section>
        <section className={styles.block}>
          <h2>Reviews</h2>
          <p className={styles.muted}>No reviews yet — write one from My Account.</p>
        </section>
        <section className={styles.block}>
          <h2>Fulfillment</h2>
          <p>
            Choose <strong>click &amp; collect</strong> or{" "}
            <strong>dispatch</strong> at checkout (Phase 10).
          </p>
        </section>
      </div>
    </article>
  );
}
