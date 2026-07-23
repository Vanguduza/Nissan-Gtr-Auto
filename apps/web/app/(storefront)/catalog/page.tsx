import { CatalogCanvasStub } from "@/components/catalog-canvas-stub";
import styles from "../page.module.css";

export const metadata = { title: "Catalog" };

export default function CatalogPage() {
  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Catalog</h1>
      <p className={styles.lede}>
        Browse by diagram. Hotspot data is stubbed until the Phase 7 pipeline
        imports FAST plates.
      </p>
      <CatalogCanvasStub />
    </div>
  );
}
