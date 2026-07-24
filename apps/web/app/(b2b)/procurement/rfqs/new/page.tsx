import { ProcurementNav } from "@/components/procurement-nav";
import { StaffRfqCreate } from "@/components/staff-rfq-create";
import styles from "@/components/account.module.css";

export const metadata = { title: "New RFQ" };

export default function NewRfqPage() {
  return (
    <div className={styles.shell}>
      <ProcurementNav current="/procurement/rfqs/new" />
      <div className={styles.panel}>
        <h1 className={styles.title}>New RFQ</h1>
        <p className={styles.lede}>
          Creates a draft via <code>create_rfq</code>. Submit from the detail
          page when ready to invite pricing.
        </p>
        <StaffRfqCreate />
      </div>
    </div>
  );
}
