import { ProcurementNav } from "@/components/procurement-nav";
import { StaffProcurementApprovalsPanel } from "@/components/staff-procurement-approvals-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Procurement approvals" };

export default function ProcurementApprovalsPage() {
  return (
    <div className={styles.shell}>
      <ProcurementNav current="/procurement/approvals" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Approvals</h1>
        <p className={styles.lede}>
          Finance or admin: approve or reject submitted purchase orders and
          material requests. Each PO links to a detail surface with live progress
          through closed (fund release + GRN receive). Approved status is
          required before GRN, blanket call-off, or MR→PO conversion.
        </p>
        <StaffProcurementApprovalsPanel />
      </div>
    </div>
  );
}
