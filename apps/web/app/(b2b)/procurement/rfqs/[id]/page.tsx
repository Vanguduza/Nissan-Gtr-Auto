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
        <h1 className={styles.title}>RFQ</h1>
        <p className={styles.lede}>
          Submit the draft, review quotations, and award a winner to a purchase
          order.
        </p>
        <StaffRfqDetail rfqId={id} />
      </div>
    </div>
  );
}
