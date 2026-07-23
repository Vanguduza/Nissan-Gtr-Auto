import { CatalogCanvasStub } from "@/components/catalog-canvas-stub";
import { StorefrontHero } from "@/components/hero";
import { SearchFourWay } from "@/components/search-four-way";
import styles from "./page.module.css";

export default function HomePage() {
  return (
    <>
      <StorefrontHero />
      <section className={styles.below} aria-labelledby="search-heading">
        <h2 id="search-heading" className={styles.sectionTitle}>
          Four-way parts search
        </h2>
        <p className={styles.sectionLede}>
          Part number, VIN, model, or PNC — one query path into stock.
        </p>
        <SearchFourWay />
      </section>
      <section className={styles.below} aria-labelledby="catalog-heading">
        <h2 id="catalog-heading" className={styles.sectionTitle}>
          Visual catalog
        </h2>
        <p className={styles.sectionLede}>
          Diagram hotspots will open fitment-accurate parts lists.
        </p>
        <CatalogCanvasStub />
      </section>
    </>
  );
}
