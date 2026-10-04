import { StaffNav } from "@/components/staff-nav";
import { StaffDeliveryBalancesPanel } from "@/components/staff-delivery-balances-panel";
import { Banknote, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Balances on account" };

export default function StaffLogisticsBalancesPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/logistics/balances" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Banknote size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Balances on account
          </h1>
          <p className={styles.pageSubtitle}>
            Cash/card-on-delivery customers who paid part: leave the rest on their account or refuse. A driver may be waiting at the door.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffDeliveryBalancesPanel />
        </div>
      </div>
    </div>
  );
}
