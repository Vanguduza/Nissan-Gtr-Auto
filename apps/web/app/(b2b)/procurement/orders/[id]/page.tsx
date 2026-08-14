import { ProcurementNav } from "@/components/procurement-nav";
import { PurchaseOrderDetailPanel } from "@/components/purchase-order-detail-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Purchase order" };

export default async function PurchaseOrderDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <div className={styles.shell}>
      <ProcurementNav current={`/procurement/orders/${id}`} />
      <div className={styles.panel}>
        <h1 className={styles.title}>Purchase order</h1>
        <p className={styles.lede}>
          Live progress from draft through closed — status, fund release, and
          GRN receive qty (not a demo tracker).
        </p>
        <PurchaseOrderDetailPanel purchaseOrderId={id} />
      </div>
    </div>
  );
}
