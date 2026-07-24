import { ShopChrome } from "@/components/shop-chrome";

export default function StaffLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <ShopChrome>{children}</ShopChrome>;
}
