import { AccountNav } from "@/components/account-nav";
import { OrdersList } from "@/components/orders-list";
import styles from "@/components/account.module.css";

export const metadata = { title: "Orders" };

export default function OrdersPage() {
  return (
    <div className={styles.shell}>
      <AccountNav current="/account/orders" />
      <div className={styles.panel}>
        <h1 className={styles.title}>Orders & tracking</h1>
        <p className={styles.lede}>
          Click &amp; collect (Harare counter) or nationwide dispatch. Open an
          order for live last-point tracking while it is out for delivery.
        </p>
        <OrdersList />
      </div>
    </div>
  );
}
