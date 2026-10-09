import { ProcurementNav } from "@/components/procurement-nav";
import { StaffRfqDetail } from "@/components/staff-rfq-detail";
import styles from "@/components/account.module.css";

export const metadata = { title: "RFQ detail" };

export default async function RfqDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <div className={styles.shell}>
      <ProcurementNav current={`/procurement/rfqs/${id}`} />
      <div className={styles.panel}>
        <h1 className={styles.title}>RFQ (optional spot-buy)</h1>
        <p className={styles.lede}>
          Optional quotation compare for a one-off spot buy. Awarding a quotation
          may create a PO for that buy, but preferred-roster POs remain the
          replenishment SoR — RFQ-win does not authorize the supplier list.
        </p>
        <StaffRfqDetail rfqId={id} />
      </div>
    </div>
  );
}
