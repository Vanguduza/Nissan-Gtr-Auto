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
          Create cart, add OEM lines, checkout. Online prep queue for storefront
          dispatch orders. Roles enforced by RPCs.
        </p>
        <StaffPosShell />
      </div>
    </div>
  );
}
