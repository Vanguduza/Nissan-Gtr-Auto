export default function StorefrontLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className="gtr-shop-shell">
      {/* Header/footer injected via ShopShell to avoid circular imports in RSC edge cases */}
      <ShopChrome>{children}</ShopChrome>
    </div>
  );
}

import { ShopChrome } from "@/components/shop-chrome";
