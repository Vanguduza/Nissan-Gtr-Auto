import { AccountNav } from "@/components/account-nav";
import { ComparePanel } from "@/components/compare-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Compare" };

export default function ComparePage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/compare" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Compare</h1>
        <p className={styles.lede}>
          Side-by-side up to 3 SKUs selected from the catalog (stored in this
          browser). Sign in to load live prices and stock.
        </p>
        <ComparePanel />
      </div>
    </div>
  );
}
