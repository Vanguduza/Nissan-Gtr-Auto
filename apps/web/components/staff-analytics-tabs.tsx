"use client";

import { StaffModuleTabs, type StaffModuleTab } from "@/components/staff-module-tabs";

const ANALYTICS_TABS: StaffModuleTab[] = [
  { id: "kpis", label: "KPIs", href: "/staff/analytics" },
  {
    id: "subscriptions",
    label: "Subscriptions",
    href: "/staff/analytics/subscriptions",
  },
];

export function StaffAnalyticsTabs({ active }: { active: string }) {
  return (
    <StaffModuleTabs
      tabs={ANALYTICS_TABS}
      active={active}
      ariaLabel="Analytics sections"
    />
  );
}
