import { StaffNav } from "@/components/staff-nav";
import { StaffReviewModerationPanel } from "@/components/staff-review-moderation-panel";
import { iconSizeMd, iconStroke, Star } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Review moderation" };

export default function StaffReviewModerationPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/crm/reviews" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Star size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Review moderation
          </h1>
          <p className={styles.pageSubtitle}>
            Approve or reject storefront product reviews before they publish.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffReviewModerationPanel />
        </div>
      </div>
    </div>
  );
}
