import { ShopChrome } from "@/components/shop-chrome";

/** Customer B2B trade surface — keep storefront chrome. */
export default function B2bTradeLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return <ShopChrome>{children}</ShopChrome>;
}
