import { StaffNav } from "@/components/staff-nav";
import { StaffPosShell } from "@/components/staff-pos-shell";
import { iconSizeMd, iconStroke, Monitor } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · POS" };

export default function StaffPosPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/pos" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Monitor size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            POS
          </h1>
          <p className={styles.pageSubtitle}>
            In-store cart checkout and online order prep — pick a view from the
            sidebar.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffPosShell />
        </div>
      </div>
    </div>
  );
}
