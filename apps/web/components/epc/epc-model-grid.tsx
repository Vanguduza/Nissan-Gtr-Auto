import Link from "next/link";
import { catalogPath, type CatalogModel } from "@/lib/catalog-hierarchy";
import styles from "./epc-grid.module.css";

export function EpcModelGrid({
  maker,
  models,
}: {
  maker: string;
  models: CatalogModel[];
}) {
  const sorted = [...models].sort((a, b) =>
    a.sort_key.localeCompare(b.sort_key),
  );
  return (
    <>
      <h2 className={styles.heading}>Models</h2>
      <ul className={styles.grid}>
        {sorted.map((m) => {
          const years =
            m.year_start || m.year_end
              ? [m.year_start, m.year_end].filter(Boolean).join("–")
              : null;
          return (
            <li key={m.slug}>
              <Link
                href={catalogPath({ maker, model: m.slug })}
                className={styles.card}
              >
                <span className={styles.cardName}>{m.display_name}</span>
                <span className={styles.cardMeta}>
                  {[m.body_type, years].filter(Boolean).join(" · ") ||
                    "Select variant"}
                </span>
              </Link>
            </li>
          );
        })}
      </ul>
    </>
  );
}
