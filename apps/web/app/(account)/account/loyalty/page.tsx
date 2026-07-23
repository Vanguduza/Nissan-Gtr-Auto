import { AccountNav } from "@/components/account-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Loyalty" };

export default function LoyaltyPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/loyalty" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Loyalty</h1>
        <p className={styles.lede}>
          Points after store credit (Phase 13) — Phase 16 engine. Balance stub:
          0 pts.
        </p>
      </div>
    </div>
  );
}
