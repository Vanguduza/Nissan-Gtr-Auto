import { StaffGate } from "@/components/staff-gate";

export default function StaffLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <StaffGate>{children}</StaffGate>;
}
