import { ShopChrome } from "@/components/shop-chrome";

export default function SupplierLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <ShopChrome>{children}</ShopChrome>;
}
