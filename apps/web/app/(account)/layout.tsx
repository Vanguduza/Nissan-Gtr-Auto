import { ShopChrome } from "@/components/shop-chrome";

export default function AccountLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <ShopChrome>{children}</ShopChrome>;
}
