import { AccountNav } from "@/components/account-nav";
import { ReturnsPanel } from "@/components/returns-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Returns" };

export default function ReturnsPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/returns" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Returns</h1>
        <p className={styles.lede}>
          Request a return against an invoice. Stock always routes to{" "}
          <strong>Quarantine</strong> — never a direct exchange to saleable.
        </p>
        <ReturnsPanel />
      </div>
    </div>
  );
}
