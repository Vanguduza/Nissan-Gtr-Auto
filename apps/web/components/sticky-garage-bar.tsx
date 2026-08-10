"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  garageLabel,
  listGarageVehicles,
  requireSession,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "./sticky-garage-bar.module.css";

export function StickyGarageBar() {
  const [label, setLabel] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function run() {
      const client = createWebClient();
      if (!client) return;
      const session = await requireSession(client);
      if (!session.ok || cancelled) return;
      const vehicles = await listGarageVehicles(client);
      if (!vehicles.ok || cancelled) return;
      const primary =
        vehicles.data.find((v) => v.is_primary) ?? vehicles.data[0];
      if (primary) setLabel(garageLabel(primary));
    }
    void run();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className={styles.bar} role="status">
      <div className={styles.inner}>
        <span className={styles.label}>Shopping for</span>
        <strong className={styles.vehicle}>
          {label ?? "No vehicle selected"}
        </strong>
        <nav className={styles.links} aria-label="Vehicle fitment">
          <Link href="/vehicle" className={styles.link}>
            Select vehicle
          </Link>
          <Link href="/account/garage" className={styles.link}>
            Change in My Garage
          </Link>
        </nav>
      </div>
    </div>
  );
}
