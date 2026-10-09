import Link from "next/link";
import { HomeMerch } from "@/components/home-merch";
import { StorefrontHero } from "@/components/hero";
import {
  ArrowRight,
  Car,
  iconSizeMd,
  iconStroke,
  Package,
  type LucideIcon,
} from "@/components/icons";
import styles from "./page.module.css";

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
          <Icon size={22} strokeWidth={iconStroke} />
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
      <HomeMerch />

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
