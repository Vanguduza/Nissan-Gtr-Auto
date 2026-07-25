import { StaffNav } from "@/components/staff-nav";
import { StaffPosShell } from "@/components/staff-pos-shell";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · POS" };

export default function StaffPosPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/pos" />
      <div className={styles.panel}>
        <h1 className={styles.title}>POS</h1>
        <p className={styles.lede}>
          Sales till: search, catalog browse, add lines, checkout with receipt
          contacts. Optional pairing code for Android companion (web never
          scans). Online prep for storefront dispatch. Roles enforced by RPCs.
        </p>
        <StaffPosShell />
      </div>
    </div>
  );
}
