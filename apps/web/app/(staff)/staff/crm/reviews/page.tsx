import { StaffNav } from "@/components/staff-nav";
import { StaffReviewModerationPanel } from "@/components/staff-review-moderation-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Review moderation" };

export default function StaffReviewModerationPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/crm/reviews" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Review moderation</h1>
        <StaffReviewModerationPanel />
      </div>
    </div>
  );
}
