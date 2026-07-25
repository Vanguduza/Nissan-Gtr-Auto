import { AccountNav } from "@/components/account-nav";
import { GaragePanel } from "@/components/garage-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "My Garage" };

export default function GarageAccountPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/garage" />
      <div className={styles.panel}>
        <h1 className={styles.title}>My Garage</h1>
        <p className={styles.lede}>
          Saved vehicles drive fitment filters across the shop. Service
          reminders are not available yet — only garage vehicles are stored
          today.
        </p>
        <GaragePanel />
      </div>
    </div>
  );
}
