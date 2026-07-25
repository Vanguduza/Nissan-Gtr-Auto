import { SupplierNav } from "@/components/supplier-nav";
import { SupplierBlanketPanel } from "@/components/supplier-blanket-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Supplier · Blankets" };

export default function SupplierBlanketsPage() {
  return (
    <div className={styles.shell}>
      <SupplierNav current="/supplier/blankets" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Blanket contracts</h1>
        <p className={styles.lede}>
          Read-only view of blanket purchase orders visible to your linked
          supplier (RLS). Expiry and remaining-value alerts surface here —
          call-offs stay with staff procurement.
        </p>
        <SupplierBlanketPanel />
      </div>
    </div>
  );
}
