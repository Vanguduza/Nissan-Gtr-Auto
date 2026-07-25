import { StaffNav } from "@/components/staff-nav";
import { StaffOnlinePrepPanel } from "@/components/staff-online-prep-panel";
import { iconSizeMd, iconStroke, PackageCheck } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Sales prep" };

export default function StaffLogisticsPrepPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics/prep" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <PackageCheck size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Sales prep
          </h1>
          <p className={styles.pageSubtitle}>
            Pick and stage online orders before dispatch.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffOnlinePrepPanel />
        </div>
      </div>
    </div>
  );
}
