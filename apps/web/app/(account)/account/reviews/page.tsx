import { AccountNav } from "@/components/account-nav";
import { ReviewsPanel } from "@/components/reviews-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Reviews" };

export default function ReviewsPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/reviews" />
      <div className={styles.panel}>
        <h1 className={styles.title}>My reviews</h1>
        <p className={styles.lede}>
          Submit ratings for parts you use. Approved reviews appear on the
          product page after moderation.
        </p>
        <ReviewsPanel />
      </div>
    </div>
  );
}
