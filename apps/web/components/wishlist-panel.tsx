"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  listWishlistItems,
  removeWishlistItem,
  type WishlistItemRow,
} from "@/lib/customer-wishlist";
import { requireSession } from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; items: WishlistItemRow[] };

export function WishlistPanel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

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
    const items = await listWishlistItems(client);
    if (!items.ok) {
      setStatus({ kind: "error", message: items.error });
      return;
    }
    setStatus({ kind: "ready", items: items.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onRemove(item: WishlistItemRow) {
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const result = await removeWishlistItem(client, { wishlistId: item.id });
    setBusy(false);
    if (!result.ok) {
      setMessage(result.error);
      return;
    }
    setMessage("Removed from wishlist.");
    await refresh();
  }

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading wishlist…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login?next=/account/wishlist">Sign in</Link> to save parts
        to your wishlist.
      </p>
    );
  }
  if (status.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {status.message}{" "}
        <button type="button" className={styles.btnGhost} onClick={() => void refresh()}>
          Retry
        </button>
      </p>
    );
  }

  if (status.items.length === 0) {
    return (
      <p className={styles.muted}>
        No saved SKUs yet. Open a part page and use Wishlist, or browse the{" "}
        <Link href="/catalog" className={styles.btnGhost}>
          catalog
        </Link>
        .
      </p>
    );
  }

  return (
    <div>
      <ul className={styles.list}>
        {status.items.map((item) => {
          const oem =
            item.stock_items?.oem_part_number ?? item.stock_item_id.slice(0, 8);
          const name = item.stock_items?.description?.trim() || oem;
          return (
            <li key={item.id}>
              <strong>{name}</strong>
              <br />
              <span className={styles.muted}>
                OEM <code>{oem}</code> · saved{" "}
                {new Date(item.created_at).toLocaleDateString()}
              </span>
              <br />
              <Link
                href={`/parts/${encodeURIComponent(oem)}`}
                className={styles.btn}
              >
                Open
              </Link>{" "}
              <button
                type="button"
                className={styles.btnGhost}
                disabled={busy}
                onClick={() => void onRemove(item)}
              >
                Remove
              </button>
            </li>
          );
        })}
      </ul>
      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
