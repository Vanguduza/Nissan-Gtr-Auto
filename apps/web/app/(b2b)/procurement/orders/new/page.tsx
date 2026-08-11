import { ManualPurchaseOrderPanel } from "@/components/manual-purchase-order-panel";
import { ProcurementNav } from "@/components/procurement-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "New purchase order" };

export default function NewPurchaseOrderPage() {
  return (
    <div className={styles.shell}>
      <ProcurementNav current="/procurement/orders/new" />
      <ManualPurchaseOrderPanel />
    </div>
  );
}
