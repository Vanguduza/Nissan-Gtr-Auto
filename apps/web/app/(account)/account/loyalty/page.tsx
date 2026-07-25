import { AccountNav } from "@/components/account-nav";
import { LoyaltyPanel } from "@/components/loyalty-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Loyalty" };

export default function LoyaltyPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/loyalty" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Loyalty</h1>
        <p className={styles.lede}>
          Points balance from Phase 16 (<code>get_loyalty_balance</code>) —
          after store credit, before redemption at counter.
        </p>
        <LoyaltyPanel />
      </div>
    </div>
  );
}
