import { GoodsReceiptPanel } from "@/components/goods-receipt-panel";
import { ProcurementNav } from "@/components/procurement-nav";
import styles from "@/components/account.module.css";

export const metadata = { title: "Goods received" };

export default function ProcurementGrnPage() {
  return (
    <div className={styles.shell}>
      <ProcurementNav current="/procurement/grn" />
      <GoodsReceiptPanel />
    </div>
  );
}
