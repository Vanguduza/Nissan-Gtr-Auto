import { SiteHeader } from "@/components/site-header";
import garage from "./garage.module.css";

export default function GarageLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className={garage.shell}>
      <div className={garage.topBar}>
        <SiteHeader />
      </div>
      {children}
    </div>
  );
}
