import styles from "./price-dual.module.css";

/** Storefront prices are USD-only. ZiG settlement appears at checkout. */
export function PriceDual({
  usd,
  zig: _zig,
}: {
  usd: number;
  /** @deprecated Ignored — catalog no longer dual-prices. */
  zig?: number | null;
}) {
  return (
    <span className={styles.wrap}>
      <span className={styles.usd}>USD {usd.toFixed(2)}</span>
    </span>
  );
}
