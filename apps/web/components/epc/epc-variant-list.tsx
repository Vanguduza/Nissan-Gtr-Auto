import Link from "next/link";
import { catalogPath, type CatalogVariant } from "@/lib/catalog-hierarchy";
import styles from "./epc-grid.module.css";

export function EpcVariantList({
  maker,
  model,
  variants,
}: {
  maker: string;
  model: string;
  variants: CatalogVariant[];
}) {
  return (
    <>
      <h2 className={styles.heading}>Variants</h2>
      <table className={styles.variantTable}>
        <thead>
          <tr>
            <th>Chassis</th>
            <th>Grade</th>
            <th>Region</th>
            <th>Years</th>
            <th>Engine</th>
          </tr>
        </thead>
        <tbody>
          {variants.map((v) => (
            <tr key={v.slug} className={styles.variantRow}>
              <td>
                <Link
                  href={catalogPath({
                    maker,
                    model,
                    variant: v.slug,
                  })}
                  className={styles.chassis}
                >
                  {v.chassis_code}
                </Link>
              </td>
              <td>{v.grade ?? "—"}</td>
              <td>{v.sales_region ?? "—"}</td>
              <td>{v.year_label ?? "—"}</td>
              <td>{v.engine_code ?? "—"}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  );
}
