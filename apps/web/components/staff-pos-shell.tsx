"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { StaffOnlinePrepPanel } from "@/components/staff-online-prep-panel";
import { StaffPosPanel } from "@/components/staff-pos-panel";
import styles from "@/components/account.module.css";

const POS_TAB_IDS = ["cart", "prep"] as const;

function StaffPosShellInner() {
  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab");
  const tab =
    tabParam && POS_TAB_IDS.includes(tabParam as (typeof POS_TAB_IDS)[number])
      ? tabParam
      : "cart";

  return (
    <div>
      {tab === "cart" ? <StaffPosPanel /> : null}
      {tab === "prep" ? <StaffOnlinePrepPanel /> : null}
    </div>
  );
}

export function StaffPosShell() {
  return (
    <Suspense fallback={<p className={styles.formStatus}>Loading POS…</p>}>
      <StaffPosShellInner />
    </Suspense>
  );
}
