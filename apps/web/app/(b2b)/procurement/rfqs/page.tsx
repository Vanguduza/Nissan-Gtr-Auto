import Link from "next/link";
import { ProcurementNav } from "@/components/procurement-nav";
import { StaffRfqList } from "@/components/staff-rfq-list";
import styles from "@/components/account.module.css";

export const metadata = { title: "RFQs" };

export default function ProcurementRfqsPage() {
  return (
    <div className={styles.shell}>
      <ProcurementNav current="/procurement/rfqs" />
      <div className={styles.panel}>
        <h1 className={styles.title}>RFQs</h1>
        <p className={styles.lede}>
          Draft → submit → compare supplier quotations → award to PO. Requires
          staff role (admin, warehouse, or finance).
        </p>
        <Link href="/procurement/rfqs/new" className={styles.btn}>
          New RFQ
        </Link>
        <div style={{ marginTop: "1rem" }}>
          <StaffRfqList />
        </div>
      </div>
    </div>
  );
}
