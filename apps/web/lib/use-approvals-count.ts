"use client";

import { useEffect, useState } from "react";
import { listMyApprovals } from "@/lib/staff-approvals";
import { createWebClient } from "@/lib/supabase";

/** Waiting items for the nav badge: refreshed every minute while a staff page is open. */
export function useApprovalsCount(enabled: boolean): { total: number; urgent: number } {
  const [count, setCount] = useState({ total: 0, urgent: 0 });
  useEffect(() => {
    if (!enabled) return;
    const client = createWebClient();
    if (!client) return;
    let alive = true;
    const load = async () => {
      const r = await listMyApprovals(client);
      if (alive && r.ok) setCount({ total: r.data.items.length, urgent: r.data.items.filter((i) => i.urgent).length });
    };
    void load();
    const t = window.setInterval(() => void load(), 60_000);
    return () => {
      alive = false;
      window.clearInterval(t);
    };
  }, [enabled]);
  return count;
}
