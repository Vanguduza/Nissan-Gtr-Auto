import { ProcurementNav } from "@/components/procurement-nav";
import { StaffRfqCreate } from "@/components/staff-rfq-create";
import styles from "@/components/account.module.css";

export const metadata = { title: "New RFQ" };

export default function NewRfqPage() {
  return (
    <div className={styles.shell}>
      <ProcurementNav current="/procurement/rfqs/new" />
      <div className={styles.panel}>
        <h1 className={styles.title}>New RFQ (optional)</h1>
        <p className={styles.lede}>
          Spot-buy only — creates a draft via <code>create_rfq</code>. This does
          not replace the preferred supplier roster. Submit from the detail page
          when ready to invite pricing.
        </p>
        <StaffRfqCreate />
      </div>
    </div>
  );
}
