import { AccountNav } from "@/components/account-nav";
import styles from "../account.module.css";

export const metadata = { title: "Wishlist" };

export default function WishlistPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/wishlist" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Wishlist</h1>
        <p className={styles.lede}>
          Save parts and opt into back-in-stock SMS (Phase 13). Empty for now.
        </p>
        <p className={styles.muted}>No saved SKUs.</p>
      </div>
    </div>
  );
}
