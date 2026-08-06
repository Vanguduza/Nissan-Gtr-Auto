import { iconSizeMd, iconStroke, Zap } from "@/components/icons";
import styles from "./flash-sale-panel.module.css";

/**
 * KMP Flash Sale + countdown — UI shell only until a customer deals feed exists.
 * Do not invent countdown timers or discounted prices.
 */
export function FlashSalePanel() {
  return (
    <section className={styles.panel} aria-labelledby="flash-heading">
      <div className={styles.head}>
        <h2 id="flash-heading" className={styles.title}>
          <span className={styles.icon} aria-hidden>
            <Zap size={iconSizeMd} strokeWidth={iconStroke} />
          </span>
          Flash deals
        </h2>
        <div className={styles.timerStub} aria-hidden>
          <span>--</span>
          <span>:</span>
          <span>--</span>
          <span>:</span>
          <span>--</span>
        </div>
      </div>
      {/* TODO(backend): wire customer deals feed + expired_at countdown when RPC exists */}
    </section>
  );
}
