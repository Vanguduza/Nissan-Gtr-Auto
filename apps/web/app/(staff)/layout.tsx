import { ShopChrome } from "@/components/shop-chrome";
import { StaffGate } from "@/components/staff-gate";

export default function StaffLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <ShopChrome>
      <StaffGate>{children}</StaffGate>
    </ShopChrome>
  );
}
