import { AccountNav } from "@/components/account-nav";
import styles from "../account.module.css";

export const metadata = { title: "Reviews" };

export default function ReviewsPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/reviews" />
      <div className={styles.panel}>
        <h1 className={styles.title}>My reviews</h1>
        <p className={styles.lede}>
          Ratings appear on PDPs once moderation volume justifies it.
        </p>
        <p className={styles.muted}>You have not reviewed any parts yet.</p>
      </div>
    </div>
  );
}
