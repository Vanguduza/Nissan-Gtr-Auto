import { AccountNav } from "@/components/account-nav";
import { AddressesPanel } from "@/components/addresses-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Addresses" };

export default function AddressesPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/addresses" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Addresses</h1>
        <p className={styles.lede}>
          Delivery and billing addresses for nationwide dispatch. Click &amp;
          collect uses the Harare counter — no address required for pickup.
        </p>
        <AddressesPanel />
      </div>
    </div>
  );
}
