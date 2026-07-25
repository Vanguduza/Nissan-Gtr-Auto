"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import {
  listActiveKits,
  requireSession,
  type KitListItem,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "@/app/(storefront)/page.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; kits: KitListItem[] };

export function KitsList() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setStatus({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setStatus({ kind: "auth" });
      return;
    }
    const kits = await listActiveKits(client);
    if (!kits.ok) {
      setStatus({ kind: "error", message: kits.error });
      return;
    }
    setStatus({ kind: "ready", kits: kits.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (status.kind === "loading") {
    return <p className={styles.sectionLede}>Loading kits…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.sectionLede}>
        <Link href="/login">Sign in</Link> to browse kit / BOM packs (Phase 16).
      </p>
    );
  }
  if (status.kind === "error") {
    return (
      <p className={styles.sectionLede} role="alert">
        {status.message}
      </p>
    );
  }
  if (status.kits.length === 0) {
    return (
      <p className={styles.sectionLede}>
        No active kits in the catalog yet. Staff can define BOMs on{" "}
        <code>item_kits</code>.
      </p>
    );
  }

  return (
    <ul className={styles.simpleList}>
      {status.kits.map((kit) => (
        <li key={kit.kitId}>
          <Link href={`/parts/${encodeURIComponent(kit.oem)}`}>
            {kit.name}
          </Link>
          {" — "}
          <span>
            {kit.oem} · {kit.sellMode}
          </span>
          {kit.components.length > 0 ? (
            <ul className={styles.simpleList}>
              {kit.components.map((c) => (
                <li key={`${kit.kitId}-${c.oem}`}>
                  {c.qty}× {c.name} ({c.oem})
                </li>
              ))}
            </ul>
          ) : null}
        </li>
      ))}
    </ul>
  );
}
