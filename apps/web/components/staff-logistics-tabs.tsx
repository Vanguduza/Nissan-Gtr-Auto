"use client";

import { StaffModuleTabs, type StaffModuleTab } from "@/components/staff-module-tabs";

const LOGISTICS_TABS: StaffModuleTab[] = [
  { id: "jobs", label: "Jobs / pick", href: "/staff/logistics" },
  { id: "prep", label: "Sales prep", href: "/staff/logistics/prep" },
  { id: "tracking", label: "Tracking", href: "/staff/logistics/tracking" },
  { id: "panic", label: "Panic", href: "/staff/logistics/panic" },
];

export function StaffLogisticsTabs({ active }: { active: string }) {
  return (
    <StaffModuleTabs
      tabs={LOGISTICS_TABS}
      active={active}
      ariaLabel="Logistics sections"
    />
  );
}
