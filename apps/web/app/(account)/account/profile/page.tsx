import { AccountNav } from "@/components/account-nav";
import { ProfileForm } from "@/components/profile-form";
import styles from "@/components/account.module.css";

export const metadata = { title: "Personal details" };

export default function ProfilePage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/profile" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Personal details</h1>
        <p className={styles.lede}>
          Name and contact information used for orders, receipts, and counter
          pickup alerts.
        </p>
        <ProfileForm />
      </div>
    </div>
  );
}
