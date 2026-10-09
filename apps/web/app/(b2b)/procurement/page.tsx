import { ProcurementNav } from "@/components/procurement-nav";
import { RecentPurchaseOrdersPanel } from "@/components/recent-purchase-orders-panel";
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
          Relationship-based buying: maintain preferred suppliers, build POs with
          quoted figures from AI restock suggestions (manual — never auto-PO),
          submit for approval, then receive into WH1. RFQs remain optional for
          rare spot buys — they do <strong>not</strong> authorize the supplier
          list.
        </p>
        <RecentPurchaseOrdersPanel />
        <div className={styles.cardGrid} style={{ marginTop: "1.25rem" }}>
          <Link href="/procurement/suppliers" className={styles.card}>
            <span className={styles.cardLabel}>Preferred suppliers</span>
            <span className={styles.cardBlurb}>Add / remove roster</span>
          </Link>
          <Link href="/procurement/orders/new" className={styles.card}>
            <span className={styles.cardLabel}>New PO</span>
            <span className={styles.cardBlurb}>Quoted lines · submit</span>
          </Link>
          <Link href="/procurement/grn" className={styles.card}>
            <span className={styles.cardLabel}>Goods received</span>
            <span className={styles.cardBlurb}>OEM + qty · invoice GRN</span>
          </Link>
          <Link href="/procurement/approvals" className={styles.card}>
            <span className={styles.cardLabel}>Approvals</span>
            <span className={styles.cardBlurb}>PO &amp; MR queue · fund release</span>
          </Link>
          <Link href="/staff/warehouse/receive" className={styles.card}>
            <span className={styles.cardLabel}>Ad-hoc receive</span>
            <span className={styles.cardBlurb}>Non-PO stock entry</span>
          </Link>
          <Link href="/staff/warehouse/master-stock" className={styles.card}>
            <span className={styles.cardLabel}>Master stock</span>
            <span className={styles.cardBlurb}>Total · WH1 · WH2</span>
          </Link>
          <Link href="/staff/warehouse/transfers" className={styles.card}>
            <span className={styles.cardLabel}>WH1 → WH2 transfers</span>
            <span className={styles.cardBlurb}>Approval-tracked moves</span>
          </Link>
          <Link href="/staff/warehouse/insights" className={styles.card}>
            <span className={styles.cardLabel}>AI restock insights</span>
            <span className={styles.cardBlurb}>Suggestions only</span>
          </Link>
          <Link href="/procurement/blankets" className={styles.card}>
            <span className={styles.cardLabel}>Blanket POs</span>
            <span className={styles.cardBlurb}>Call-offs</span>
          </Link>
          <Link href="/procurement/rfqs" className={styles.card}>
            <span className={styles.cardLabel}>RFQs (optional)</span>
            <span className={styles.cardBlurb}>Spot buy — not roster SoR</span>
          </Link>
        </div>
      </div>
    </div>
  );
}
