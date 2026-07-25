"use client";

import Link from "next/link";
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
  { id: "consignment", label: "Consignment", href: "/staff/warehouse/consignment" },
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

/** Deep-link helper kept for overview cards. */
export function warehouseTabHref(id: string): string {
  const tab = WAREHOUSE_TABS.find((t) => t.id === id);
  return tab?.href ?? "/staff/warehouse";
}

export function WarehouseOverviewLinks() {
  return (
    <>
      {WAREHOUSE_TABS.filter((t) => t.id !== "hub").map((t) => (
        <Link key={t.id} href={t.href!} className="sr-only">
          {t.label}
        </Link>
      ))}
    </>
  );
}
