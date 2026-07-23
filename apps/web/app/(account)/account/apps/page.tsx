import { AccountNav } from "@/components/account-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Mobile apps" };

export default function AppsPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/apps" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Customer apps</h1>
        <p className={styles.lede}>
          iOS and Android customer apps (Phase 11) will reuse My Account / My
          Garage APIs. Store links appear here when published.
        </p>
      </div>
    </div>
  );
}
