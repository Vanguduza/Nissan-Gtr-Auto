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
        <p className={styles.lede}>
          Approve or reject pending product reviews via{" "}
          <code>moderate_customer_product_review</code>. Sales/admin roles
          enforced by RPC. Approved reviews may enqueue a fail-closed notify
          event.
        </p>
        <StaffReviewModerationPanel />
      </div>
    </div>
  );
}
