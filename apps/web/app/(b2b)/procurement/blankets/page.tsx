import { ProcurementNav } from "@/components/procurement-nav";
import { StaffBlanketPanel } from "@/components/staff-blanket-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Blanket POs" };

export default function ProcurementBlanketsPage() {
  return (
    <div className={styles.shell}>
      <ProcurementNav current="/procurement/blankets" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Blanket purchase orders</h1>
        <p className={styles.lede}>
          Create blanket contracts, submit them, get finance approval, then
          call-off releases against remaining qty and value.
        </p>
        <StaffBlanketPanel />
      </div>
    </div>
  );
}
