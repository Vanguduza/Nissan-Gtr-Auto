import { StaffNav } from "@/components/staff-nav";
import { StaffKitsPanel } from "@/components/staff-kits-panel";
import { iconSizeMd, iconStroke, Package } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Kits" };

export default function StaffKitsPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/crm/kits" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Package size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Kits
          </h1>
          <p className={styles.pageSubtitle}>
            Create and edit explode BOM kits for the storefront. Manual kit OEM,
            catalog components, optional chassis fitment.
          </p>
        </header>
        <StaffKitsPanel />
      </div>
    </div>
  );
}
