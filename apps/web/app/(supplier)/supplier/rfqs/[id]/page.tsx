import { SupplierNav } from "@/components/supplier-nav";
import { SupplierQuoteForm } from "@/components/supplier-quote-form";
import styles from "@/components/account.module.css";

export const metadata = { title: "Supplier quotation" };

export default async function SupplierRfqDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id } = await params;
  return (
    <div className={styles.shell}>
      <SupplierNav current={`/supplier/rfqs/${id}`} />
      <div className={styles.panel}>
        <h1 className={styles.title}>Quotation</h1>
        <p className={styles.lede}>
          Upsert a draft with <code>upsert_supplier_quotation</code>, then
          submit when prices are final.
        </p>
        <SupplierQuoteForm rfqId={id} />
      </div>
    </div>
  );
}
