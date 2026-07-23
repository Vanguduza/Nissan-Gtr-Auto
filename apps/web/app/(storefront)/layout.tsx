import { SiteHeader } from "@/components/site-header";
import styles from "./storefront.module.css";

export default function StorefrontLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className={styles.shell}>
      <SiteHeader />
      <main>{children}</main>
    </div>
  );
}
