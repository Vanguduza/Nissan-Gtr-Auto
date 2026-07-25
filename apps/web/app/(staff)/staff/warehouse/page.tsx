import Link from "next/link";
import { StaffNav } from "@/components/staff-nav";
import { StaffWarehouseTabs } from "@/components/staff-warehouse-tabs";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Warehouse" };

export default function StaffWarehouseHubPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/warehouse" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Warehouse</h1>
        <p className={styles.lede}>
          Receive, dual-auth transfers, and cycle count. Warehouse staff only —
          roles enforced by RPCs. Typed OEM / manual entry here; QR scan and
          print use the management device bridge.
        </p>
        <StaffWarehouseTabs active="hub" />
        <div className={styles.cardGrid}>
          <Link href="/staff/warehouse/receive" className={styles.card}>
            <span className={styles.cardLabel}>Receive</span>
            <span className={styles.cardBlurb}>post_stock_receipt</span>
          </Link>
          <Link href="/staff/warehouse/transfers" className={styles.card}>
            <span className={styles.cardLabel}>Transfers</span>
            <span className={styles.cardBlurb}>Create · approve · reject</span>
          </Link>
          <Link href="/staff/warehouse/cycle-count" className={styles.card}>
            <span className={styles.cardLabel}>Cycle count</span>
            <span className={styles.cardBlurb}>Reconciliation draft → post</span>
          </Link>
          <Link href="/staff/warehouse/bins" className={styles.card}>
            <span className={styles.cardLabel}>Bins</span>
            <span className={styles.cardBlurb}>Create · preferred putaway</span>
          </Link>
          <Link href="/staff/warehouse/consignment" className={styles.card}>
            <span className={styles.cardLabel}>Consignment</span>
            <span className={styles.cardBlurb}>Draft → submit / cancel</span>
          </Link>
        </div>
      </div>
    </div>
  );
}
