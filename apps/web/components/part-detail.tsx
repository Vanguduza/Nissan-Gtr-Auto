"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { AddToCartButton } from "@/components/add-to-cart-button";
import { PriceDual } from "@/components/price-dual";
import { StockBadge } from "@/components/stock-badge";
import { ChatEntryLink } from "@/components/chat-entry-link";
import { WhatsAppCta } from "@/components/whatsapp-cta";
import { Heart, Images, Star, iconSizeMd, iconStroke } from "@/components/icons";
import {
  loadCatalogProduct,
  partHref,
  type CatalogFitmentLine,
  type CatalogProduct,
} from "@/lib/catalog-product";
import {
  garageLabel,
  listGarageVehicles,
  type GarageVehicleRow,
} from "@/lib/customer-storefront";
import {
  addOemToCompareTray,
  removeOemFromCompareTray,
} from "@/lib/customer-compare";
import { isOemInCompare } from "@/lib/compare-selection";
import {
  getProductReviewStats,
  listApprovedReviewPhotoUrlsForOem,
  listApprovedReviewsForOem,
  submitProductReview,
  uploadReviewPhoto,
  type ProductReviewRow,
  type ProductReviewStats,
} from "@/lib/customer-reviews";
import {
  addWishlistItem,
  isOemOnWishlist,
  removeWishlistItem,
} from "@/lib/customer-wishlist";
import { createWebClient, friendlyError } from "@/lib/supabase";
import styles from "@/app/(storefront)/parts/[oem]/pdp.module.css";

type Status =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "missing" }
  | { kind: "ready"; product: CatalogProduct };

type VehicleFit = { label: string; fits: boolean | null } | null;

const norm = (v: string | null | undefined) =>
  (v ?? "").replace(/[\s-]/g, "").toUpperCase();

/**
 * Catalogue fitment stays in the background (owner decision 2026-10-10):
 * customers only see whether the part fits their primary garage vehicle.
 */
function primaryVehicleFit(
  vehicles: GarageVehicleRow[],
  fitments: CatalogFitmentLine[],
): VehicleFit {
  const v = vehicles.find((row) => row.is_primary) ?? vehicles[0];
  if (!v) return null;
  const label = garageLabel(v);
  const chassis = norm(v.generation);
  if (!chassis || fitments.length === 0) return { label, fits: null };
  const engine = norm(v.engine);
  const fits = fitments.some(
    (f) =>
      norm(f.chassis_code) === chassis &&
      (!engine || !f.engine_code || norm(f.engine_code) === engine),
  );
  return { label, fits };
}

/** `ref` is the stock item id (customer links) or a legacy part-number URL. */
export function PartDetail({ oem: ref }: { oem: string }) {
  const router = useRouter();
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [onWishlist, setOnWishlist] = useState(false);
  const [inCompare, setInCompare] = useState(false);
  const [reviews, setReviews] = useState<ProductReviewRow[]>([]);
  const [reviewStats, setReviewStats] = useState<ProductReviewStats | null>(
    null,
  );
  const [wishBusy, setWishBusy] = useState(false);
  const [compareBusy, setCompareBusy] = useState(false);
  const [reviewRating, setReviewRating] = useState("5");
  const [reviewBody, setReviewBody] = useState("");
  const [reviewBusy, setReviewBusy] = useState(false);
  const [reviewPhoto, setReviewPhoto] = useState<File | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  const [photoUrls, setPhotoUrls] = useState<string[]>([]);
  const [vehicleFit, setVehicleFit] = useState<VehicleFit>(null);

  useEffect(() => {
    let cancelled = false;

    async function run() {
      setStatus({ kind: "loading" });
      setActionMsg(null);
      setPhotoUrls([]);
      setVehicleFit(null);
      const client = createWebClient();
      if (!client) {
        if (!cancelled) {
          setStatus({
            kind: "error",
            message: "Supabase is not configured on this environment.",
          });
        }
        return;
      }

      const { data: sessionData } = await client.auth.getSession();
      if (!sessionData.session) {
        if (!cancelled) setStatus({ kind: "auth" });
        return;
      }

      const result = await loadCatalogProduct(client, ref);
      if (cancelled) return;

      if (!result.ok) {
        if (result.missing) {
          setStatus({ kind: "missing" });
          return;
        }
        setStatus({ kind: "error", message: friendlyError(result.error) });
        return;
      }

      setStatus({ kind: "ready", product: result.data });
      setInCompare(isOemInCompare(result.data.oem));
      // Legacy part-number URLs move to the opaque id so the number never shows.
      const canonical = partHref({ id: result.data.id, oem: result.data.oem });
      if (result.data.id && canonical !== partHref({ oem: ref })) {
        router.replace(canonical);
      }

      const [wish, approved, stats, photos, garage] = await Promise.all([
        isOemOnWishlist(client, result.data.oem),
        listApprovedReviewsForOem(client, result.data.oem),
        getProductReviewStats(client, { oem: result.data.oem }),
        listApprovedReviewPhotoUrlsForOem(client, result.data.oem),
        listGarageVehicles(client),
      ]);
      if (cancelled) return;
      if (wish.ok) setOnWishlist(wish.data);
      if (approved.ok) setReviews(approved.data);
      if (stats.ok) setReviewStats(stats.data);
      if (photos.ok) setPhotoUrls(photos.data);
      if (garage.ok) {
        setVehicleFit(primaryVehicleFit(garage.data, result.data.fitments));
      }
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, [ref, router]);

  const toggleWishlist = useCallback(async (productOem: string) => {
    setWishBusy(true);
    setActionMsg(null);
    const client = createWebClient();
    if (!client) {
      setActionMsg("Supabase is not configured.");
      setWishBusy(false);
      return;
    }
    if (onWishlist) {
      const res = await removeWishlistItem(client, { oem: productOem });
      setWishBusy(false);
      if (!res.ok) {
        setActionMsg(res.error);
        return;
      }
      setOnWishlist(false);
      setActionMsg("Removed from wishlist.");
      return;
    }
    const res = await addWishlistItem(client, { oem: productOem });
    setWishBusy(false);
    if (!res.ok) {
      setActionMsg(res.error);
      return;
    }
    setOnWishlist(true);
    setActionMsg("Saved to wishlist.");
  }, [onWishlist]);

  async function toggleCompare(productOem: string) {
    setCompareBusy(true);
    setActionMsg(null);
    const client = createWebClient();
    if (!client) {
      setActionMsg("Supabase is not configured.");
      setCompareBusy(false);
      return;
    }
    if (isOemInCompare(productOem)) {
      await removeOemFromCompareTray(client, productOem, true);
      setInCompare(false);
      setCompareBusy(false);
      setActionMsg("Removed from compare.");
      return;
    }
    const res = await addOemToCompareTray(client, productOem, true);
    setCompareBusy(false);
    if (!res.ok) {
      setActionMsg(res.error);
      return;
    }
    setInCompare(true);
    setActionMsg("Added to compare (synced to account).");
  }

  async function onSubmitReview(e: FormEvent, productOem: string) {
    e.preventDefault();
    setReviewBusy(true);
    setActionMsg(null);
    const client = createWebClient();
    if (!client) {
      setActionMsg("Supabase is not configured.");
      setReviewBusy(false);
      return;
    }
    const n = Number(reviewRating);
    const res = await submitProductReview(client, {
      rating: n,
      body: reviewBody.trim(),
      oem: productOem,
    });
    if (!res.ok) {
      setReviewBusy(false);
      setActionMsg(res.error);
      return;
    }
    if (reviewPhoto) {
      const photo = await uploadReviewPhoto(client, {
        reviewId: res.data,
        file: reviewPhoto,
      });
      if (!photo.ok) {
        setReviewBusy(false);
        setActionMsg(
          `Review submitted, but photo upload failed: ${photo.error}`,
        );
        setReviewBody("");
        setReviewPhoto(null);
        return;
      }
    }
    setReviewBusy(false);
    setReviewBody("");
    setReviewPhoto(null);
    setActionMsg("Review submitted — pending moderation.");
    const stats = await getProductReviewStats(client, { oem: productOem });
    if (stats.ok) setReviewStats(stats.data);
  }

  if (status.kind === "loading") {
    return (
      <article className={styles.wrap}>
        <div className={styles.gallery}>
          <div className={styles.photo}>
            <span>Loading…</span>
          </div>
        </div>
        <div className={styles.info}>
          <p className={styles.muted}>Loading part…</p>
        </div>
      </article>
    );
  }

  if (status.kind === "auth") {
    const next = `/parts/${encodeURIComponent(ref)}`;
    return (
      <article className={styles.wrap}>
        <div className={styles.info}>
          <h1 className={styles.title}>Sign in to view part</h1>
          <p className={styles.muted}>
            Pricing and stock details require a signed-in account.
          </p>
          <Link href={`/login?next=${encodeURIComponent(next)}`} className={styles.add}>
            Sign in
          </Link>
        </div>
      </article>
    );
  }

  if (status.kind === "error") {
    return (
      <article className={styles.wrap}>
        <div className={styles.info}>
          <h1 className={styles.title}>Part unavailable</h1>
          <p className={styles.muted} role="alert">
            {status.message}
          </p>
          <Link href="/shop" className={styles.wish}>
            Back to shop
          </Link>
        </div>
      </article>
    );
  }

  if (status.kind === "missing") {
    return (
      <article className={styles.wrap}>
        <div className={styles.info}>
          <h1 className={styles.title}>Part not found</h1>
          <p className={styles.muted}>
            This part is not in stock right now. Ask the counter or browse the
            shop.
          </p>
          <div className={styles.actions}>
            <Link href="/search" className={styles.add}>
              Search
            </Link>
            <Link href="/shop" className={styles.wish}>
              Shop stock
            </Link>
          </div>
        </div>
      </article>
    );
  }

  const p = status.product;
  const productPhotos = p.productImages ?? [];
  const hasProductPhotos = productPhotos.length > 0;
  const hasPhotos = hasProductPhotos || photoUrls.length > 0;
  const displayPhotos = hasProductPhotos ? productPhotos : photoUrls;
  const photoHint = hasProductPhotos
    ? "Product photos"
    : "Approved customer review photos";
  const ratingAvg =
    reviewStats && reviewStats.review_count > 0
      ? Number(reviewStats.avg_rating)
      : null;

  return (
    <article className={styles.wrap}>
      <div className={styles.galleryCol}>
        <div className={styles.gallery} aria-label="Product media">
          {hasPhotos ? (
            <div className={styles.photoGallery}>
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                className={styles.photoHero}
                src={displayPhotos[0]}
                alt={p.name}
              />
              {displayPhotos.length > 1 ? (
                <div className={styles.photoThumbs}>
                  {displayPhotos.slice(1, 4).map((url) => (
                    /* eslint-disable-next-line @next/next/no-img-element */
                    <img
                      key={url}
                      className={styles.photoThumb}
                      src={url}
                      alt=""
                    />
                  ))}
                </div>
              ) : null}
              <p className={styles.photoHint}>{photoHint}</p>
            </div>
          ) : (
            <div className={styles.photo}>
              <Images size={28} strokeWidth={iconStroke} aria-hidden />
              <span>Photo coming soon</span>
            </div>
          )}
        </div>
      </div>
      <div className={styles.info}>
        <div className={styles.titleRow}>
          <div>
            <p className={styles.brand}>{p.brand}</p>
            <h1 className={styles.title}>{p.name}</h1>
          </div>
          <button
            type="button"
            className={onWishlist ? styles.heartOn : styles.heart}
            disabled={wishBusy}
            aria-pressed={onWishlist}
            aria-label={onWishlist ? "Remove from wishlist" : "Add to wishlist"}
            onClick={() => void toggleWishlist(p.oem)}
          >
            <Heart
              size={22}
              strokeWidth={iconStroke}
              fill={onWishlist ? "currentColor" : "none"}
              aria-hidden
            />
          </button>
        </div>
        <div className={styles.ratingRow}>
          {ratingAvg != null ? (
            <>
              <span className={styles.stars} aria-hidden>
                {[1, 2, 3, 4, 5].map((n) => (
                  <Star
                    key={n}
                    size={iconSizeMd}
                    strokeWidth={iconStroke}
                    fill={n <= Math.round(ratingAvg) ? "currentColor" : "none"}
                  />
                ))}
              </span>
              <strong>{ratingAvg.toFixed(1)}</strong>
              <span className={styles.muted}>
                · {reviewStats!.review_count} review
                {reviewStats!.review_count === 1 ? "" : "s"}
              </span>
            </>
          ) : (
            <span className={styles.muted}>No rating yet</span>
          )}
        </div>
        <StockBadge state={p.stock} />
        <div className={styles.priceRow}>
          {p.usd != null ? (
            <>
              <PriceDual usd={p.usd} zig={p.zig} />
              {p.listUsd != null && p.discount && p.discount.kind !== "none" ? (
                <p className={styles.muted}>
                  Was USD {p.listUsd.toFixed(2)}
                  {p.discount.description
                    ? ` · ${p.discount.description}`
                    : ""}
                  {p.discount.kind === "percent"
                    ? ` (−${p.discount.value}%)`
                    : ` (−USD ${p.discount.value.toFixed(2)})`}
                </p>
              ) : null}
            </>
          ) : (
            <p className={styles.muted}>Price on request — ask counter.</p>
          )}
        </div>
        {p.coreCharge > 0 ? (
          <p className={styles.core}>
            Core / deposit:{" "}
            <strong>USD {p.coreCharge.toFixed(2)}</strong> (separate cart line)
          </p>
        ) : null}
        <div className={styles.fitment}>
          {vehicleFit == null ? (
            <p className={styles.muted}>
              <Link href="/account/garage">Add your vehicle</Link> to check
              fitment before you buy.
            </p>
          ) : vehicleFit.fits === true ? (
            <p>
              Fits your <strong>{vehicleFit.label}</strong>
            </p>
          ) : vehicleFit.fits === false ? (
            <p>
              Not listed for your <strong>{vehicleFit.label}</strong> — ask the
              counter to confirm.
            </p>
          ) : (
            <p className={styles.muted}>
              Ask the counter to confirm fitment for your{" "}
              <strong>{vehicleFit.label}</strong>.
            </p>
          )}
        </div>
        {p.category ? (
          <section className={styles.descBlock}>
            <h2 className={styles.descTitle}>Description</h2>
            <p>{p.category}</p>
          </section>
        ) : null}
        <div className={styles.stickyBar}>
          <AddToCartButton oem={p.oem} />
          <button
            type="button"
            className={styles.wish}
            disabled={wishBusy}
            onClick={() => void toggleWishlist(p.oem)}
          >
            {onWishlist ? "Remove wishlist" : "Wishlist"}
          </button>
          <button
            type="button"
            className={styles.wish}
            disabled={compareBusy}
            onClick={() => void toggleCompare(p.oem)}
          >
            {inCompare ? "Remove compare" : "Compare"}
          </button>
          <Link href="/account/compare" className={styles.wish}>
            View compare
          </Link>
          <WhatsAppCta product={p.name} />
          <ChatEntryLink product={p.name} />
        </div>
        {actionMsg ? (
          <p className={styles.muted} role="status">
            {actionMsg}
          </p>
        ) : null}
        <section className={styles.block}>
          <h2>Alternatives</h2>
          {p.alternatives.length ? (
            <ul className={styles.alts}>
              {p.alternatives.map((a) => (
                <li key={a.oem}>
                  <Link href={partHref(a)}>{a.name}</Link>
                </li>
              ))}
            </ul>
          ) : (
            <p className={styles.muted}>No alternatives in stock.</p>
          )}
        </section>
        <section className={styles.block}>
          <h2>Reviews</h2>
          {reviewStats && reviewStats.review_count > 0 ? (
            <p>
              <strong>{Number(reviewStats.avg_rating).toFixed(1)}</strong> / 5
              average · {reviewStats.review_count} approved review
              {reviewStats.review_count === 1 ? "" : "s"}
            </p>
          ) : (
            <p className={styles.muted}>No rating aggregate yet.</p>
          )}
          {reviews.length === 0 ? (
            <p className={styles.muted}>No approved reviews yet.</p>
          ) : (
            <ul className={styles.reviewList}>
              {reviews.map((r) => (
                <li key={r.id}>
                  <strong>{r.rating}/5</strong>
                  {r.body ? ` — ${r.body}` : ""}
                  <span className={styles.muted}>
                    {" "}
                    · {new Date(r.created_at).toLocaleDateString()}
                  </span>
                </li>
              ))}
            </ul>
          )}
          <form
            onSubmit={(e) => void onSubmitReview(e, p.oem)}
            className={styles.reviewForm}
          >
            <p className={styles.muted}>Write a review (pending moderation)</p>
            <div className={styles.actions}>
              <select
                value={reviewRating}
                onChange={(e) => setReviewRating(e.target.value)}
                disabled={reviewBusy}
                aria-label="Rating"
              >
                {[5, 4, 3, 2, 1].map((n) => (
                  <option key={n} value={n}>
                    {n} stars
                  </option>
                ))}
              </select>
              <input
                value={reviewBody}
                onChange={(e) => setReviewBody(e.target.value)}
                placeholder="Optional comments"
                disabled={reviewBusy}
                style={{ flex: 1, minWidth: "8rem" }}
              />
              <button type="submit" className={styles.wish} disabled={reviewBusy}>
                Submit
              </button>
            </div>
            <label className={styles.muted} style={{ display: "block", marginTop: "0.5rem" }}>
              Photo (optional, jpeg/png/webp · file upload — not camera capture)
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                disabled={reviewBusy}
                onChange={(e) =>
                  setReviewPhoto(e.target.files?.[0] ?? null)
                }
                style={{ display: "block", marginTop: "0.25rem" }}
              />
            </label>
          </form>
          <p className={styles.muted}>
            Or manage all reviews from{" "}
            <Link href="/account/reviews">My Account</Link>.
          </p>
        </section>
        <section className={styles.block}>
          <h2>Fulfillment</h2>
          <p>
            Choose <strong>click &amp; collect</strong> or{" "}
            <strong>dispatch</strong> at checkout.
          </p>
        </section>
      </div>
    </article>
  );
}
