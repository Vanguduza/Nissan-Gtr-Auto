"use client";

import Link from "next/link";
import { partHref } from "@/lib/catalog-product";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  listOwnReviews,
  reviewStatusLabel,
  type ProductReviewRow,
} from "@/lib/customer-reviews";
import { requireSession } from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; reviews: ProductReviewRow[] };

export function ReviewsPanel() {
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
    const reviews = await listOwnReviews(client);
    if (!reviews.ok) {
      setStatus({ kind: "error", message: reviews.error });
      return;
    }
    setStatus({ kind: "ready", reviews: reviews.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading reviews…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login?next=/account/reviews">Sign in</Link> to write and
        manage your product reviews.
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

  return (
    <div>
      <p className={styles.muted}>
        To review a part, open it from the shop or your orders and use the
        review form on the product page.
      </p>

      {status.reviews.length === 0 ? (
        <p className={styles.muted}>You have not reviewed any parts yet.</p>
      ) : (
        <ul className={styles.list}>
          {status.reviews.map((r) => {
            const oemNum =
              r.stock_items?.oem_part_number ?? r.stock_item_id.slice(0, 8);
            const name = r.stock_items?.description?.trim() || "Nissan part";
            return (
              <li key={r.id}>
                <strong>{name}</strong> · {r.rating}/5 ·{" "}
                {reviewStatusLabel(r.status)}
                <br />
                {r.body ? (
                  <span className={styles.muted}>{r.body}</span>
                ) : null}
                <br />
                <Link
                  href={partHref({ id: r.stock_item_id, oem: oemNum })}
                  className={styles.btn}
                >
                  View part
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
