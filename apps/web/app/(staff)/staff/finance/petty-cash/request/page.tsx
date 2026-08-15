import { StaffNav } from "@/components/staff-nav";
import { StaffPettyCashRequestForm } from "@/components/staff-petty-cash-request-form";
import { Banknote, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Request petty cash funds" };

export default function StaffPettyCashRequestPage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/finance/petty-cash/request" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Banknote size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Request funds
          </h1>
          <p className={styles.pageSubtitle}>
            Ask for a float top-up — approval and automatic disbursement apply.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffPettyCashRequestForm />
        </div>
      </div>
    </div>
  );
}
