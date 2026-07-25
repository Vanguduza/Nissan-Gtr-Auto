import { StaffHubCards } from "@/components/staff-hub-cards";
import { StaffNav } from "@/components/staff-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff" };

export default function StaffHubPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Staff</h1>
        <p className={styles.lede}>
          Management fallback for POS, warehouse, finance, attendance, and
          dispatch. Roles from <code>staff_roles</code>; RPCs remain the source
          of truth. QR / GPS use the management device bridge — not the browser.
        </p>
        <StaffHubCards />
      </div>
    </div>
  );
}
