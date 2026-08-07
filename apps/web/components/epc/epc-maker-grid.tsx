import Link from "next/link";
import { catalogPath, type CatalogMaker } from "@/lib/catalog-hierarchy";
import styles from "./epc-grid.module.css";

export function EpcMakerGrid({ makers }: { makers: CatalogMaker[] }) {
  return (
    <>
      <h2 className={styles.heading}>Makers</h2>
      <ul className={styles.grid}>
        {makers.map((m) => (
          <li key={m.slug}>
            <Link
              href={catalogPath({ maker: m.slug })}
              className={styles.card}
            >
              <span className={styles.cardName}>{m.name}</span>
              <span className={styles.cardMeta}>
                {m.model_count != null
                  ? `${m.model_count} model${m.model_count === 1 ? "" : "s"}`
                  : "Browse models"}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
