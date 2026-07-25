import { AccountNav } from "@/components/account-nav";
import { WishlistPanel } from "@/components/wishlist-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Wishlist" };

export default function WishlistPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/wishlist" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Wishlist</h1>
        <p className={styles.lede}>
          Saved parts from the catalog. Add or remove from any product page.
        </p>
        <WishlistPanel />
      </div>
    </div>
  );
}
