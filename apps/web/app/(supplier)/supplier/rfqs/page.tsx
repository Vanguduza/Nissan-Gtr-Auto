import { SupplierNav } from "@/components/supplier-nav";
import { SupplierRfqList } from "@/components/supplier-rfq-list";
import styles from "@/components/account.module.css";

export const metadata = { title: "Invited RFQs" };

export default function SupplierRfqsPage() {
  return (
    <div className={styles.shell}>
      <SupplierNav current="/supplier/rfqs" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Invited RFQs</h1>
        <p className={styles.lede}>
          Only RFQs that include your supplier profile appear here.
        </p>
        <SupplierRfqList />
      </div>
    </div>
  );
}
