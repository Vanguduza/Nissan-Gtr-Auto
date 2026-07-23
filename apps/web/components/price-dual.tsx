import styles from "./price-dual.module.css";

export function PriceDual({
  usd,
  zig,
}: {
  usd: number;
  zig?: number | null;
}) {
  return (
    <span className={styles.wrap}>
      <span className={styles.usd}>
        USD {usd.toFixed(2)}
      </span>
      <span className={styles.zig}>
        ZiG {zig != null ? zig.toFixed(2) : "—"}
      </span>
    </span>
  );
}
