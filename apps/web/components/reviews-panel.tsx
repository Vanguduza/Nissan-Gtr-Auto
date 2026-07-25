"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  listOwnReviews,
  reviewStatusLabel,
  submitProductReview,
  uploadReviewPhoto,
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
  const [oem, setOem] = useState("");
  const [rating, setRating] = useState("5");
  const [body, setBody] = useState("");
  const [photo, setPhoto] = useState<File | null>(null);
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

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const n = Number(rating);
    if (!Number.isInteger(n) || n < 1 || n > 5) {
      setMessage("Rating must be 1–5.");
      setBusy(false);
      return;
    }
    if (!oem.trim()) {
      setMessage("OEM part number required.");
      setBusy(false);
      return;
    }
    const result = await submitProductReview(client, {
      rating: n,
      body: body.trim(),
      oem: oem.trim(),
    });
    setBusy(false);
    if (!result.ok) {
      setMessage(result.error);
      return;
    }
    setMessage("Review submitted for moderation.");
    setBody("");
    await refresh();
  }

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
      <form className={styles.form} onSubmit={(e) => void onSubmit(e)}>
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Write a review</legend>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              OEM
              <input
                value={oem}
                onChange={(e) => setOem(e.target.value)}
                placeholder="e.g. 15208-65F0C"
                disabled={busy}
                required
              />
            </label>
            <label className={styles.field}>
              Rating
              <select
                value={rating}
                onChange={(e) => setRating(e.target.value)}
                disabled={busy}
              >
                {[5, 4, 3, 2, 1].map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
              Comments
              <input
                value={body}
                onChange={(e) => setBody(e.target.value)}
                placeholder="Optional"
                disabled={busy}
              />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy}>
              Submit review
            </button>
          </div>
        </fieldset>
      </form>

      {status.reviews.length === 0 ? (
        <p className={styles.muted}>You have not reviewed any parts yet.</p>
      ) : (
        <ul className={styles.list}>
          {status.reviews.map((r) => {
            const oemNum =
              r.stock_items?.oem_part_number ?? r.stock_item_id.slice(0, 8);
            const name = r.stock_items?.description?.trim() || oemNum;
            return (
              <li key={r.id}>
                <strong>{name}</strong> · {r.rating}/5 ·{" "}
                {reviewStatusLabel(r.status)}
                <br />
                <span className={styles.muted}>
                  OEM <code>{oemNum}</code>
                  {r.body ? ` — ${r.body}` : ""}
                </span>
                <br />
                <Link
                  href={`/parts/${encodeURIComponent(oemNum)}`}
                  className={styles.btn}
                >
                  Open PDP
                </Link>
              </li>
            );
          })}
        </ul>
      )}
      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
