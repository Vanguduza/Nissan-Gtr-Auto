"use client";

import { StaffModuleTabs, type StaffModuleTab } from "@/components/staff-module-tabs";

const WAREHOUSE_TABS: StaffModuleTab[] = [
  { id: "hub", label: "Overview", href: "/staff/warehouse" },
  { id: "receive", label: "Receive", href: "/staff/warehouse/receive" },
  { id: "transfers", label: "Transfers", href: "/staff/warehouse/transfers" },
  {
    id: "cycle-count",
    label: "Cycle count",
    href: "/staff/warehouse/cycle-count",
  },
  { id: "bins", label: "Bins", href: "/staff/warehouse/bins" },
  {
    id: "consignment",
    label: "Consignment",
    href: "/staff/warehouse/consignment",
  },
];

export function StaffWarehouseTabs({ active }: { active: string }) {
  return (
    <StaffModuleTabs
      tabs={WAREHOUSE_TABS}
      active={active}
      ariaLabel="Warehouse sections"
    />
  );
}
