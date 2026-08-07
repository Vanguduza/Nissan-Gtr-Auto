"use client";

import Link from "next/link";
import { AddToCartButton } from "@/components/add-to-cart-button";
import { PriceDual } from "@/components/price-dual";
import { StockBadge } from "@/components/stock-badge";
import { partHref } from "@/lib/catalog-search";
import type { CatalogDiagramPart } from "@/lib/catalog-hierarchy";
import styles from "./epc-diagram.module.css";

export type EpcPartRow = CatalogDiagramPart & {
  usd?: number | null;
  stock?: "in_stock" | "low" | "backorder" | "counter_only" | null;
};

export function EpcPartsTable({
  parts,
  activeOem,
  onHoverOem,
}: {
  parts: EpcPartRow[];
  activeOem: string | null;
  onHoverOem: (oem: string | null) => void;
}) {
  if (parts.length === 0) {
    return <p className={styles.caption}>No parts for this section.</p>;
  }

  return (
    <div className={styles.tableWrap}>
      <table className={styles.table}>
        <thead>
          <tr>
            <th>OEM</th>
            <th>PNC</th>
            <th>Category</th>
            <th>Stock</th>
            <th>Price</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {parts.map((p) => {
            const oem = p.oem_part_number;
            const stocked = Boolean(p.stock_item_id);
            return (
              <tr
                key={oem}
                className={activeOem === oem ? styles.rowActive : undefined}
                onMouseEnter={() => onHoverOem(oem)}
                onMouseLeave={() => onHoverOem(null)}
              >
                <td className={styles.oem}>
                  <Link
                    href={`${partHref(oem)}?from=epc`}
                    className={styles.oemLink}
                  >
                    {oem}
                  </Link>
                </td>
                <td>{p.pnc_code ?? "—"}</td>
                <td>
                  {[p.category_name, p.subcategory_name]
                    .filter(Boolean)
                    .join(" · ") || "—"}
                </td>
                <td>
                  {stocked ? (
                    <StockBadge state={p.stock ?? "in_stock"} />
                  ) : (
                    "—"
                  )}
                </td>
                <td>
                  {stocked && p.usd != null ? <PriceDual usd={p.usd} /> : "—"}
                </td>
                <td>
                  {stocked ? (
                    <div className={styles.actions}>
                      <AddToCartButton oem={oem} />
                    </div>
                  ) : null}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
