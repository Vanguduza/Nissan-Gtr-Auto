import type { ReactNode } from "react";
import { SiteFooter } from "@/components/site-footer";
import { SiteHeader } from "@/components/site-header";
import { StickyGarageBar } from "@/components/sticky-garage-bar";
import styles from "./shop-chrome.module.css";

export function ShopChrome({ children }: { children: ReactNode }) {
  return (
    <div className={styles.shell}>
      <SiteHeader />
      <StickyGarageBar />
      <main className={styles.main}>{children}</main>
      <SiteFooter />
    </div>
  );
}
