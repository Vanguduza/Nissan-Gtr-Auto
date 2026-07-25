"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  listPendingReviewsForStaff,
  listReviewPhotos,
  moderateProductReview,
  reviewStatusLabel,
  signedReviewPhotoUrl,
  type ProductReviewPhotoRow,
  type ProductReviewRow,
} from "@/lib/customer-reviews";
import { requireSession } from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; reviews: ProductReviewRow[] };

export function StaffReviewModerationPanel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [photoUrls, setPhotoUrls] = useState<Record<string, string[]>>({});

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
    const reviews = await listPendingReviewsForStaff(client);
    if (!reviews.ok) {
      setStatus({ kind: "error", message: reviews.error });
      return;
    }
    setStatus({ kind: "ready", reviews: reviews.data });

    const urls: Record<string, string[]> = {};
    for (const r of reviews.data) {
      const photos = await listReviewPhotos(client, r.id);
      if (!photos.ok) continue;
      const signed: string[] = [];
      for (const ph of photos.data as ProductReviewPhotoRow[]) {
        const u = await signedReviewPhotoUrl(client, ph.storage_path);
        if (u) signed.push(u);
      }
      if (signed.length) urls[r.id] = signed;
    }
    setPhotoUrls(urls);
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onModerate(
    review: ProductReviewRow,
    next: "approved" | "rejected",
  ) {
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const res = await moderateProductReview(client, {
      reviewId: review.id,
      status: next,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(
      next === "approved"
        ? "Review approved (notify event queued if catalog allows)."
        : "Review rejected.",
    );
    await refresh();
  }

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading pending reviews…</p>;
  }
  if (status.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login?next=/staff/crm/reviews">Sign in</Link> with
        sales/admin to moderate product reviews.
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

  if (status.reviews.length === 0) {
    return (
      <p className={styles.muted}>
        No pending reviews. Approved reviews appear on PDPs.
      </p>
    );
  }

  return (
    <div>
      <ul className={styles.list}>
        {status.reviews.map((r) => {
          const oem =
            r.stock_items?.oem_part_number ?? r.stock_item_id.slice(0, 8);
          const name = r.stock_items?.description?.trim() || oem;
          const customer = r.customers?.display_name ?? r.customer_id.slice(0, 8);
          return (
            <li key={r.id}>
              <strong>{name}</strong> · {r.rating}/5 ·{" "}
              {reviewStatusLabel(r.status)}
              <br />
              <span className={styles.muted}>
                OEM <code>{oem}</code> · customer {customer} ·{" "}
                {new Date(r.created_at).toLocaleString()}
                {r.body ? ` — ${r.body}` : ""}
              </span>
              {photoUrls[r.id]?.length ? (
                <div
                  style={{
                    display: "flex",
                    gap: "0.5rem",
                    flexWrap: "wrap",
                    marginTop: "0.5rem",
                  }}
                >
                  {photoUrls[r.id]!.map((url) => (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      key={url}
                      src={url}
                      alt="Review attachment"
                      style={{
                        width: 72,
                        height: 72,
                        objectFit: "cover",
                        borderRadius: 4,
                      }}
                    />
                  ))}
                </div>
              ) : null}
              <div className={styles.formActions} style={{ marginTop: "0.5rem" }}>
                <Link
                  href={`/parts/${encodeURIComponent(oem)}`}
                  className={styles.btnGhost}
                >
                  Open PDP
                </Link>
                <button
                  type="button"
                  className={styles.btn}
                  disabled={busy}
                  onClick={() => void onModerate(r, "approved")}
                >
                  Approve
                </button>
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void onModerate(r, "rejected")}
                >
                  Reject
                </button>
              </div>
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
