import type { StockState } from "@/lib/shop-demo";
import styles from "./stock-badge.module.css";

const labels: Record<StockState, string> = {
  in_stock: "In stock",
  low: "Low stock",
  backorder: "On backorder",
  counter_only: "Counter only",
};

export function StockBadge({ state }: { state: StockState }) {
  return (
    <span className={`${styles.badge} ${styles[state]}`}>{labels[state]}</span>
  );
}
