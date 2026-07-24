import { ProcurementNav } from "@/components/procurement-nav";
import styles from "@/components/account.module.css";
import Link from "next/link";

export const metadata = { title: "Procurement" };

export default function ProcurementPage() {
  return (
    <div className={styles.shell}>
      <ProcurementNav current="/procurement" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Procurement</h1>
        <p className={styles.lede}>
          Staff RFQ portal (Phase 8b): create and submit requests for quotation,
          then award a winning supplier quote to a purchase order.
        </p>
        <div className={styles.cardGrid}>
          <Link href="/procurement/rfqs" className={styles.card}>
            <span className={styles.cardLabel}>RFQs</span>
            <span className={styles.cardBlurb}>List &amp; award</span>
          </Link>
          <Link href="/procurement/rfqs/new" className={styles.card}>
            <span className={styles.cardLabel}>New RFQ</span>
            <span className={styles.cardBlurb}>Draft + invite</span>
          </Link>
          <Link href="/supplier/rfqs" className={styles.card}>
            <span className={styles.cardLabel}>Supplier view</span>
            <span className={styles.cardBlurb}>Invited quotes</span>
          </Link>
        </div>
      </div>
    </div>
  );
}
