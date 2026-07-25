import { StaffGate } from "@/components/staff-gate";

/** Staff procurement / RFQs — management chrome only (no storefront). */
export default function ProcurementLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <StaffGate>{children}</StaffGate>;
}
