import Link from "next/link";
import { CatalogCanvasStub } from "@/components/catalog-canvas-stub";
import { StorefrontHero } from "@/components/hero";
import {
  ArrowRight,
  ArrowUpDown,
  Car,
  CircleDot,
  Cog,
  Droplets,
  Filter,
  iconSizeLg,
  iconSizeMd,
  iconStroke,
  Images,
  LayoutGrid,
  ListOrdered,
  Package,
  Search,
  Zap,
  type LucideIcon,
} from "@/components/icons";
import { SearchFourWay } from "@/components/search-four-way";
import styles from "./page.module.css";

const categoryTiles: {
  href: string;
  label: string;
  blurb: string;
  Icon: LucideIcon;
}[] = [
  {
    href: "/catalog?cat=brakes",
    label: "Brakes",
    blurb: "Pads, discs, hoses",
    Icon: CircleDot,
  },
  {
    href: "/catalog?cat=filters",
    label: "Filters",
    blurb: "Oil, air, cabin, fuel",
    Icon: Filter,
  },
  {
    href: "/catalog?cat=engine",
    label: "Engine",
    blurb: "Belts, sensors, gaskets",
    Icon: Cog,
  },
  {
    href: "/catalog?cat=suspension",
    label: "Suspension",
    blurb: "Arms, bushes, shocks",
    Icon: ArrowUpDown,
  },
  {
    href: "/catalog?cat=electrical",
    label: "Electrical",
    blurb: "Batteries, lighting",
    Icon: Zap,
  },
  {
    href: "/catalog?cat=cooling",
    label: "Cooling",
    blurb: "Radiators, pumps",
    Icon: Droplets,
  },
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

function SectionHeading({
  id,
  Icon,
  title,
  lede,
}: {
  id: string;
  Icon: LucideIcon;
  title: string;
  lede: string;
}) {
  return (
    <div className={styles.sectionHead}>
      <h2 id={id} className={styles.sectionTitle}>
        <span className={styles.sectionIcon} aria-hidden>
          <Icon size={iconSizeLg} strokeWidth={iconStroke} />
        </span>
        {title}
      </h2>
      <p className={styles.sectionLede}>{lede}</p>
    </div>
  );
}

export default function HomePage() {
  return (
    <>
      <StorefrontHero />

      <section className={styles.section} aria-labelledby="cats-heading">
        <div className={styles.band}>
          <SectionHeading
            id="cats-heading"
            Icon={LayoutGrid}
            title="Popular categories"
            lede="Browse like a parts counter — pick a group, then refine by vehicle."
          />
          <ul className={styles.catGrid}>
            {categoryTiles.map((c) => (
              <li key={c.href}>
                <Link href={c.href} className={styles.catTile}>
                  <span className={styles.catIcon} aria-hidden>
                    <c.Icon size={iconSizeLg} strokeWidth={iconStroke} />
                  </span>
                  <span className={styles.catCopy}>
                    <span className={styles.catLabel}>{c.label}</span>
                    <span className={styles.catBlurb}>{c.blurb}</span>
                  </span>
                  <ArrowRight
                    className={styles.catArrow}
                    size={iconSizeMd}
                    strokeWidth={iconStroke}
                    aria-hidden
                  />
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <section className={styles.sectionAlt} aria-labelledby="list-heading">
        <div className={styles.band}>
          <SectionHeading
            id="list-heading"
            Icon={ListOrdered}
            title="Parts list preview"
            lede="Product-list mental model — live stock & price lists bind in later phases."
          />
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
                        <ArrowRight size={14} strokeWidth={iconStroke} aria-hidden />
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
          <SectionHeading
            id="search-heading"
            Icon={Search}
            title="Four-way parts search"
            lede="Part number, VIN, model, or PNC — same contract as the header search."
          />
          <SearchFourWay />
        </div>
      </section>

      <section className={styles.sectionAlt} aria-labelledby="catalog-heading">
        <div className={styles.band}>
          <SectionHeading
            id="catalog-heading"
            Icon={Images}
            title="Visual catalog"
            lede="FAST diagrams from catalog-diagrams when uploaded; otherwise the pipeline gap is shown until part_fitment.diagram_path is seeded."
          />
          <CatalogCanvasStub sample />
        </div>
      </section>

      <section className={styles.section} aria-labelledby="kits-heading">
        <div className={styles.band}>
          <SectionHeading
            id="kits-heading"
            Icon={Package}
            title="Service kits"
            lede="Bundled jobs — filters, belts, and wear items grouped for the counter."
          />
          <Link href="/kits" className={styles.button}>
            <Package size={iconSizeMd} strokeWidth={iconStroke} aria-hidden />
            Browse kits
            <ArrowRight size={iconSizeMd} strokeWidth={iconStroke} aria-hidden />
          </Link>
          <p className={styles.muted} style={{ marginTop: "0.75rem" }}>
            <Car size={14} strokeWidth={iconStroke} aria-hidden /> Fitment stays
            scoped from My Garage when a vehicle is selected.
          </p>
        </div>
      </section>
    </>
  );
}
