import { StaffFinanceTransactionDetail } from "@/components/staff-finance-transaction-detail";
import { StaffNav } from "@/components/staff-nav";
import { Banknote, iconSizeMd, iconStroke } from "@/components/icons";
import styles from "@/components/account.module.css";

export const metadata = { title: "Staff · Finance transaction" };

type PageProps = {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ tab?: string }>;
};

export default async function StaffFinanceTransactionPage({
  params,
  searchParams,
}: PageProps) {
  const { id } = await params;
  const { tab } = await searchParams;

  return (
    <div className={styles.shell}>
      <StaffNav current="/staff/finance" />
      <div className={styles.panel}>
        <header className={styles.pageHeader}>
          <h1 className={styles.title}>
            <span className={styles.titleIcon} aria-hidden>
              <Banknote size={iconSizeMd} strokeWidth={iconStroke} />
            </span>
            Transaction detail
          </h1>
          <p className={styles.pageSubtitle}>
            Receipt and ledger lines for this clearing transaction.
          </p>
        </header>
        <div className={styles.pageBody}>
          <StaffFinanceTransactionDetail
            journalEntryId={id}
            returnTab={tab ?? null}
          />
        </div>
      </div>
    </div>
  );
}
