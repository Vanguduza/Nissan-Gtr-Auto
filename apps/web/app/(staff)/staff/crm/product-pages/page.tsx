import { StaffNav } from "@/components/staff-nav";
import { StaffProductPagesPanel } from "@/components/staff-product-pages-panel";
import { iconSizeMd, iconStroke, PackageSearch } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Product pages" };

export default function StaffProductPagesPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/crm/product-pages" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <PackageSearch size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Product pages
          </h1>
          <p className={styles.pageSubtitle}>
            Set price, discount, and product photos for the shop PDP. Catalog
            title, fitment, OEM, and EPC diagrams stay read-only.
          </p>
        </header>
        <StaffProductPagesPanel />
      </div>
    </div>
  );
}
