import { StaffGate } from "@/components/staff-gate";
import layoutStyles from "@/components/staff-layout.module.css";

export default function StaffLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <div className={layoutStyles.staffCanvas}>
      <div className={layoutStyles.staffCanvasInner}>
        <StaffGate>{children}</StaffGate>
      </div>
    </div>
  );
}
