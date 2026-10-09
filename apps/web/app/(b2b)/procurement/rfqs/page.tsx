import Link from "next/link";
import { ProcurementNav } from "@/components/procurement-nav";
import { StaffRfqList } from "@/components/staff-rfq-list";
import styles from "@/components/account.module.css";

export const metadata = { title: "RFQs (optional)" };

export default function ProcurementRfqsPage() {
  return (
    <div className={styles.shell}>
      <ProcurementNav current="/procurement/rfqs" />
      <div className={styles.panel}>
        <h1 className={styles.title}>RFQs (optional spot-buy)</h1>
        <p className={styles.lede}>
          Optional legacy path for rare spot buys when a preferred supplier is
          not already on the roster. RFQ award does <strong>not</strong> authorize
          replenishment POs — the preferred supplier roster remains the system of
          record. Prefer{" "}
          <Link href="/procurement/orders/new">New PO from roster</Link> for
          day-to-day buying.
        </p>
        <Link href="/procurement/rfqs/new" className={styles.btn}>
          New optional RFQ
        </Link>
        <div style={{ marginTop: "1rem" }}>
          <StaffRfqList />
        </div>
      </div>
    </div>
  );
}
