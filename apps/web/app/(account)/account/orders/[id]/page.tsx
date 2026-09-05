import { AccountNav } from "@/components/account-nav";
import { OrderDetail } from "@/components/order-detail";
import styles from "@/components/account.module.css";

export const metadata = { title: "Order status" };

export default async function OrderDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;

  return (
    <div className={styles.shell}>
      <AccountNav current="/account/orders" />
      <div className={styles.panel}>
        <OrderDetail orderRef={id} />
      </div>
    </div>
  );
}
