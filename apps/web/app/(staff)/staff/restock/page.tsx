import { StaffNav } from "@/components/staff-nav";
import { StaffRestockPanel } from "@/components/staff-restock-panel";
import { PackagePlus, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Restock" };

export default function StaffRestockPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/restock" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <PackagePlus size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Restock
          </h1>
          <p className={styles.pageSubtitle}>
            What to move between branches and what to buy, from how fast each part sells. Tick what you agree with, then create the transfers and draft purchase orders.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffRestockPanel />
        </div>
      </div>
    </div>
  );
}
