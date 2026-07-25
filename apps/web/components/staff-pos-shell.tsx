"use client";

import { useEffect, useState } from "react";
import { StaffModuleTabs, type StaffModuleTab } from "@/components/staff-module-tabs";
import { StaffOnlinePrepPanel } from "@/components/staff-online-prep-panel";
import { StaffPosPanel } from "@/components/staff-pos-panel";
import styles from "@/components/account.module.css";

const POS_TABS: StaffModuleTab[] = [
  { id: "cart", label: "Cart" },
  { id: "prep", label: "Online prep" },
];

export function StaffPosShell() {
  const [tab, setTab] = useState("cart");

  useEffect(() => {
    if (typeof window === "undefined") return;
    const t = new URLSearchParams(window.location.search).get("tab");
    if (t && POS_TABS.some((x) => x.id === t)) setTab(t);
  }, []);

  function selectTab(id: string) {
    setTab(id);
    if (typeof window === "undefined") return;
    const url = new URL(window.location.href);
    url.searchParams.set("tab", id);
    window.history.replaceState({}, "", url);
  }

  return (
    <div>
      <StaffModuleTabs
        tabs={POS_TABS}
        active={tab}
        onChange={selectTab}
        ariaLabel="POS sections"
      />
      {tab === "cart" ? <StaffPosPanel /> : null}
      {tab === "prep" ? <StaffOnlinePrepPanel /> : null}
    </div>
  );
}
