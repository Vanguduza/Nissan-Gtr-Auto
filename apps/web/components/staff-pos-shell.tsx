"use client";

import { useSearchParams } from "next/navigation";
import { StaffModuleTabs, type StaffModuleTab } from "@/components/staff-module-tabs";
import { StaffOnlinePrepPanel } from "@/components/staff-online-prep-panel";
import { StaffPosPanel } from "@/components/staff-pos-panel";

const POS_TABS: StaffModuleTab[] = [
  { id: "cart", label: "Cart", href: "/staff/pos?tab=cart" },
  { id: "prep", label: "Online prep", href: "/staff/pos?tab=prep" },
];

export function StaffPosShell() {
  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab");
  const tab =
    tabParam && POS_TABS.some((x) => x.id === tabParam) ? tabParam : "cart";

  return (
    <div>
      <StaffModuleTabs
        tabs={POS_TABS}
        active={tab}
        ariaLabel="POS sections"
      />
      {tab === "cart" ? <StaffPosPanel /> : null}
      {tab === "prep" ? <StaffOnlinePrepPanel /> : null}
    </div>
  );
}
