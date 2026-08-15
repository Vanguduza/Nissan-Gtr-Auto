import { StaffNav } from "@/components/staff-nav";
import { StaffPettyCashExpenseForm } from "@/components/staff-petty-cash-expense-form";
import { Banknote, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Add petty cash expense" };

export default function StaffPettyCashExpensePage() {
  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/finance/petty-cash/expense" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Banknote size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Add expense
          </h1>
          <p className={styles.pageSubtitle}>
            Record an operational spend from the petty cash float.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffPettyCashExpenseForm />
        </div>
      </div>
    </div>
  );
}
