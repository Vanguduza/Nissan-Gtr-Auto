import Link from "next/link";
import { CatalogStorageImage } from "@/components/catalog-storage-image";
import { catalogPath, type CatalogSection } from "@/lib/catalog-hierarchy";
import styles from "./epc-grid.module.css";

export function EpcSectionGrid({
  maker,
  model,
  variant,
  sections,
}: {
  maker: string;
  model: string;
  variant: string;
  sections: CatalogSection[];
}) {
  const sorted = [...sections].sort(
    (a, b) => (a.sort_order ?? 0) - (b.sort_order ?? 0),
  );
  return (
    <>
      <h2 className={styles.heading}>Sections</h2>
      <ul className={styles.sectionGrid}>
        {sorted.map((s) => (
          <li key={s.slug}>
            <Link
              href={catalogPath({
                maker,
                model,
                variant,
                section: s.slug,
              })}
              className={styles.sectionCard}
            >
              {s.thumbnail_url ? (
                <CatalogStorageImage
                  className={styles.sectionThumb}
                  src={s.thumbnail_url}
                  alt=""
                  variant="thumb"
                  width={160}
                  height={160}
                  sizes="5rem"
                />
              ) : (
                <span className={styles.sectionPlaceholder}>EPC</span>
              )}
              <span className={styles.sectionName}>{s.name}</span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
