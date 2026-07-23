import { ShopChrome } from "@/components/shop-chrome";

export default function GarageLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <ShopChrome>{children}</ShopChrome>;
}
