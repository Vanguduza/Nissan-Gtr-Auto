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
        <StaffWarehouseTabs active="hub" />
        <div className={styles.cardGrid}>
          <Link href="/staff/warehouse/receive" className={styles.card}>
            <span className={styles.cardLabel}>Receive</span>
          </Link>
          <Link href="/staff/warehouse/transfers" className={styles.card}>
            <span className={styles.cardLabel}>Transfers</span>
          </Link>
          <Link href="/staff/warehouse/cycle-count" className={styles.card}>
            <span className={styles.cardLabel}>Cycle count</span>
          </Link>
          <Link href="/staff/warehouse/bins" className={styles.card}>
            <span className={styles.cardLabel}>Bins</span>
          </Link>
          <Link href="/staff/warehouse/consignment" className={styles.card}>
            <span className={styles.cardLabel}>Consignment</span>
          </Link>
        </div>
      </div>
    </div>
  );
}
