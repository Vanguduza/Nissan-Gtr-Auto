import Link from "next/link";
import { CatalogCanvasStub } from "@/components/catalog-canvas-stub";
import { StorefrontHero } from "@/components/hero";
import { SearchFourWay } from "@/components/search-four-way";
import styles from "./page.module.css";

const categoryTiles = [
  { href: "/catalog?cat=brakes", label: "Brakes", blurb: "Pads, discs, hoses" },
  { href: "/catalog?cat=filters", label: "Filters", blurb: "Oil, air, cabin, fuel" },
  { href: "/catalog?cat=engine", label: "Engine", blurb: "Belts, sensors, gaskets" },
  { href: "/catalog?cat=suspension", label: "Suspension", blurb: "Arms, bushes, shocks" },
  { href: "/catalog?cat=electrical", label: "Electrical", blurb: "Batteries, lighting" },
  { href: "/catalog?cat=cooling", label: "Cooling", blurb: "Radiators, pumps" },
];

const productStubs = [
  {
    sku: "15208-65F0C",
    name: "Oil filter — QR25 / YD25",
    usd: "12.40",
    zig: "—",
  },
  {
    sku: "40206-EA00A",
    name: "Front disc rotor",
    usd: "84.00",
    zig: "—",
  },
  {
    sku: "16546-00Q0A",
    name: "Air filter element",
    usd: "18.75",
    zig: "—",
  },
  {
    sku: "21410-JF00A",
    name: "Water pump assembly",
    usd: "142.00",
    zig: "—",
  },
];

export default function HomePage() {
  return (
    <>
      <StorefrontHero />

      <section className={styles.section} aria-labelledby="cats-heading">
        <div className={styles.band}>
          <div className={styles.sectionHead}>
            <h2 id="cats-heading" className={styles.sectionTitle}>
              Popular categories
            </h2>
            <p className={styles.sectionLede}>
              Browse like a parts counter — pick a group, then refine by vehicle.
            </p>
          </div>
          <ul className={styles.catGrid}>
            {categoryTiles.map((c) => (
              <li key={c.href}>
                <Link href={c.href} className={styles.catTile}>
                  <span className={styles.catLabel}>{c.label}</span>
                  <span className={styles.catBlurb}>{c.blurb}</span>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className={styles.sectionAlt} aria-labelledby="list-heading">
        <div className={styles.band}>
          <div className={styles.sectionHead}>
            <h2 id="list-heading" className={styles.sectionTitle}>
              Parts list preview
            </h2>
            <p className={styles.sectionLede}>
              Product-list mental model — live stock & price lists bind in later
              phases.
            </p>
          </div>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">OEM / SKU</th>
                  <th scope="col">Description</th>
                  <th scope="col">USD</th>
                  <th scope="col">ZiG</th>
                  <th scope="col">
                    <span className={styles.srOnly}>Action</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {productStubs.map((p) => (
                  <tr key={p.sku}>
                    <td>
                      <code className={styles.sku}>{p.sku}</code>
                    </td>
                    <td>{p.name}</td>
                    <td className={styles.moneyUsd}>{p.usd}</td>
                    <td className={styles.moneyZig}>{p.zig}</td>
                    <td>
                      <Link
                        href={`/parts/${encodeURIComponent(p.sku)}`}
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
        </div>
      </section>

      <section className={styles.section} aria-labelledby="search-heading">
        <div className={styles.band}>
          <div className={styles.sectionHead}>
            <h2 id="search-heading" className={styles.sectionTitle}>
              Four-way parts search
            </h2>
            <p className={styles.sectionLede}>
              Part number, VIN, model, or PNC — same contract as the header search.
            </p>
          </div>
          <SearchFourWay />
        </div>
      </section>

      <section className={styles.sectionAlt} aria-labelledby="catalog-heading">
        <div className={styles.band}>
          <div className={styles.sectionHead}>
            <h2 id="catalog-heading" className={styles.sectionTitle}>
              Visual catalog
            </h2>
            <p className={styles.sectionLede}>
              FAST diagrams from catalog-diagrams when uploaded; otherwise the
              pipeline gap is shown until part_fitment.diagram_path is seeded.
            </p>
          </div>
          <CatalogCanvasStub sample />
        </div>
      </section>
    </>
  );
}
