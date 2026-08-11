"use client";

import { Suspense } from "react";
import { useSearchParams } from "next/navigation";
import { StaffOnlinePrepPanel } from "@/components/staff-online-prep-panel";
import { StaffPosPanel } from "@/components/staff-pos-panel";
import account from "@/components/account.module.css";
import pos from "@/components/staff-pos-shell.module.css";

const POS_TAB_IDS = ["cart", "prep"] as const;

function StaffPosShellInner() {
  const searchParams = useSearchParams();
  const tabParam = searchParams.get("tab");
  const tab =
    tabParam && POS_TAB_IDS.includes(tabParam as (typeof POS_TAB_IDS)[number])
      ? tabParam
      : "cart";

  return (
    <div className={pos.posShell}>
      <header className={pos.posChrome}>
        <div>
          <p className={pos.posChromeTitle}>
            {tab === "cart" ? "Store POS" : "Online prep"}
          </p>
          <p className={pos.posChromeHint}>
            Dial UX pass — shared tokens for desktop and tablet. Scan OEM / QR,
            pick from WH2 storefloor stock, checkout with tender split.
          </p>
        </div>
      </header>
      <div className={pos.posBody}>
        {tab === "cart" ? <StaffPosPanel /> : null}
        {tab === "prep" ? <StaffOnlinePrepPanel /> : null}
      </div>
    </div>
  );
}

export function StaffPosShell() {
  return (
    <Suspense fallback={<p className={account.formStatus}>Loading POS…</p>}>
      <StaffPosShellInner />
    </Suspense>
  );
}
