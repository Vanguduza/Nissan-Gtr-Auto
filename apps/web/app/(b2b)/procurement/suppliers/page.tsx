import { PreferredSuppliersPanel } from "@/components/preferred-suppliers-panel";
import { ProcurementNav } from "@/components/procurement-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Preferred suppliers" };

export default function PreferredSuppliersPage() {
  return (
    <div className={styles.shell}>
      <ProcurementNav current="/procurement/suppliers" />
      <PreferredSuppliersPanel />
    </div>
  );
}
