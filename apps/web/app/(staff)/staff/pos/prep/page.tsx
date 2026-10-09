import { StaffNav } from "@/components/staff-nav";
import { StaffOnlinePrepPanel } from "@/components/staff-online-prep-panel";
import { iconSizeMd, iconStroke, Monitor } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Online order prep" };

/**
 * Online order prep (fulfilment of web orders). Formerly the `?tab=prep` view of the
 * retired pre-benchmark POS shell; it is a staff hub surface, not part of the counter POS.
 */
export default function StaffOnlinePrepPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/pos/prep" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Monitor size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Online order prep
          </h1>
        </header>
        <div className={styles.pageBody}>
          <StaffOnlinePrepPanel />
        </div>
      </div>
    </div>
  );
}
