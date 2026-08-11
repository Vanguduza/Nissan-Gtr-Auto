import { MasterStockPanel } from "@/components/master-stock-panel";
import styles from "@/components/account.module.css";

export const metadata = { title: "Master stock" };

export default function MasterStockPage() {
  return (
    <div className={styles.shell}>
      <MasterStockPanel />
    </div>
  );
}
