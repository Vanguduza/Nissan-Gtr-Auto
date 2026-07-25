import { StaffNav } from "@/components/staff-nav";
import { StaffPosPanel } from "@/components/staff-pos-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · POS" };

export default function StaffPosPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/pos" />
      <div className={styles.panel}>
        <h1 className={styles.title}>POS</h1>
        <p className={styles.lede}>
          Create cart, add OEM lines, checkout. Admin / sales / warehouse —
          roles enforced by RPCs. Typed OEM input only; QR add-to-cart uses
          the management device bridge.
        </p>
        <StaffPosPanel />
      </div>
    </div>
  );
}
