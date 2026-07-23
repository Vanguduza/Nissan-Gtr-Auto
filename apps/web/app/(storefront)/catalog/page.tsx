import Link from "next/link";
import { CatalogCanvasStub } from "@/components/catalog-canvas-stub";
import { PriceDual } from "@/components/price-dual";
import { StockBadge } from "@/components/stock-badge";
import { DEMO_PRODUCTS } from "@/lib/shop-demo";
import styles from "../page.module.css";

export const metadata = { title: "Catalog" };

export default async function CatalogPage({
  searchParams,
}: {
  searchParams: Promise<{ cat?: string; brand?: string }>;
}) {
  const sp = await searchParams;
  const cat = sp.cat?.toLowerCase();
  const items = cat
    ? DEMO_PRODUCTS.filter((p) => p.category === cat)
    : DEMO_PRODUCTS;

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Catalog</h1>
      <p className={styles.lede}>
        Faceted parts list + visual canvas. Live index in Phase 7.
      </p>
      <div className={styles.plp}>
        <aside className={styles.facets} aria-label="Filters">
          <h2>Filters</h2>
          <div className={styles.facetGroup}>
            <p>Category</p>
            {["brakes", "filters", "cooling", "engine"].map((c) => (
              <label key={c}>
                <input type="checkbox" readOnly checked={cat === c} /> {c}
              </label>
            ))}
          </div>
          <div className={styles.facetGroup}>
            <p>Brand</p>
            <label>
              <input type="checkbox" readOnly checked defaultChecked /> Nissan
              OE
            </label>
          </div>
          <div className={styles.facetGroup}>
            <p>Availability</p>
            <label>
              <input type="checkbox" readOnly /> In stock
            </label>
            <label>
              <input type="checkbox" readOnly /> Counter only
            </label>
          </div>
          <Link href="/catalog" className={styles.rowCta}>
            Clear
          </Link>
        </aside>
        <div>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>OEM</th>
                  <th>Description</th>
                  <th>Stock</th>
                  <th>Price</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {items.map((p) => (
                  <tr key={p.oem}>
                    <td>
                      <code className={styles.sku}>{p.oem}</code>
                    </td>
                    <td>{p.name}</td>
                    <td>
                      <StockBadge state={p.stock} />
                    </td>
                    <td>
                      <PriceDual usd={p.usd} zig={p.zig} />
                    </td>
                    <td>
                      <Link
                        href={`/parts/${encodeURIComponent(p.oem)}`}
                        className={styles.rowCta}
                      >
                        View
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ marginTop: "1.5rem" }}>
            <CatalogCanvasStub />
          </div>
        </div>
      </div>
    </div>
  );
}
