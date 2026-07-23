import { SiteHeader } from "@/components/site-header";
import b2b from "./b2b.module.css";

export default function B2bLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className={b2b.shell}>
      <div className={b2b.topBar}>
        <SiteHeader />
      </div>
      {children}
    </div>
  );
}
