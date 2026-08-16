"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import imgStyles from "@/components/staff-product-pages-panel.module.css";
import {
  deleteStaffProductImage,
  listStaffProductImages,
  listStaffProductPages,
  saveStaffProductPage,
  setStaffProductPrimaryImage,
  uploadStaffProductImage,
  type StaffProductImage,
  type StaffProductPageRow,
} from "@/lib/staff-product-pages";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ready"; rows: StaffProductPageRow[] };

export function StaffProductPagesPanel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [query, setQuery] = useState("");
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [unitPrice, setUnitPrice] = useState("");
  const [discountKind, setDiscountKind] = useState<"none" | "percent" | "amount">(
    "none",
  );
  const [discountValue, setDiscountValue] = useState("0");
  const [discountDescription, setDiscountDescription] = useState("");
  const [pinFeatured, setPinFeatured] = useState(false);
  const [pinMovers, setPinMovers] = useState(false);
  const [pinNewest, setPinNewest] = useState(false);
  const [pinSort, setPinSort] = useState("0");
  const [images, setImages] = useState<StaffProductImage[]>([]);

  const selected =
    status.kind === "ready"
      ? status.rows.find((r) => r.stock_item_id === selectedId) ?? null
      : null;

  const refresh = useCallback(async (q: string) => {
    const client = createWebClient();
    if (!client) {
      setStatus({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const res = await listStaffProductPages(client, q);
    if (!res.ok) {
      setStatus({ kind: "error", message: res.error });
      return;
    }
    setStatus({ kind: "ready", rows: res.data });
  }, []);

  const reloadImages = useCallback(async (stockItemId: string) => {
    const client = createWebClient();
    if (!client) return;
    const imgs = await listStaffProductImages(client, stockItemId);
    if (imgs.ok) setImages(imgs.data);
  }, []);

  useEffect(() => {
    void refresh("");
  }, [refresh]);

  useEffect(() => {
    if (!selected) {
      setImages([]);
      return;
    }
    setUnitPrice(
      selected.unit_price != null ? String(selected.unit_price) : "",
    );
    setDiscountKind(selected.discount_kind);
    setDiscountValue(String(selected.discount_value ?? 0));
    setDiscountDescription(selected.discount_description ?? "");
    setPinFeatured(selected.pin_featured);
    setPinMovers(selected.pin_movers);
    setPinNewest(selected.pin_newest);
    setPinSort(String(selected.pin_sort ?? 0));

    let cancelled = false;
    (async () => {
      const client = createWebClient();
      if (!client) return;
      const imgs = await listStaffProductImages(client, selected.stock_item_id);
      if (cancelled) return;
      if (imgs.ok) setImages(imgs.data);
    })();
    return () => {
      cancelled = true;
    };
  }, [selected]);

  async function onSave(e: FormEvent) {
    e.preventDefault();
    if (!selected) return;
    const price = Number(unitPrice);
    if (!Number.isFinite(price) || price < 0) {
      setMessage("Enter a valid product price (≥ 0).");
      return;
    }
    if (discountKind !== "none" && !discountDescription.trim()) {
      setMessage("Discount description is required when a discount is set.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const res = await saveStaffProductPage(client, {
      stockItemId: selected.stock_item_id,
      unitPrice: price,
      discountKind,
      discountValue: Number(discountValue) || 0,
      discountDescription:
        discountKind === "none" ? null : discountDescription.trim(),
      pinFeatured,
      pinMovers,
      pinNewest,
      pinSort: Number(pinSort) || 0,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(
      "Saved price, discount, and home-rail pins. Shop still requires in-stock + priced.",
    );
    await refresh(query);
  }

  async function onUploadFiles(fileList: FileList | null) {
    if (!selected || !fileList?.length) return;
    const files = Array.from(fileList).filter((f) =>
      /^image\/(jpeg|png|webp)$/i.test(f.type),
    );
    if (!files.length) {
      setMessage("Use JPEG, PNG, or WebP images only.");
      return;
    }

    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }

    let uploaded = 0;
    let lastError: string | null = null;
    const hadImages = images.length > 0;

    for (let i = 0; i < files.length; i += 1) {
      const file = files[i];
      const asPrimary = !hadImages && i === 0;
      const res = await uploadStaffProductImage(client, {
        stockItemId: selected.stock_item_id,
        file,
        asPrimary,
      });
      if (!res.ok) {
        lastError = res.error;
        break;
      }
      uploaded += 1;
    }

    setBusy(false);
    await reloadImages(selected.stock_item_id);
    await refresh(query);

    if (lastError) {
      setMessage(
        uploaded > 0
          ? `Uploaded ${uploaded} image(s), then failed: ${lastError}`
          : lastError,
      );
      return;
    }
    setMessage(
      uploaded === 1
        ? "Image uploaded. Select which photo is the main product image below."
        : `${uploaded} images uploaded. Select the main product image below.`,
    );
  }

  async function onSelectMain(imageId: string) {
    if (!selected) return;
    const current = images.find((i) => i.id === imageId);
    if (current?.is_primary) return;

    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const res = await setStaffProductPrimaryImage(client, imageId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    await reloadImages(selected.stock_item_id);
    setMessage("Main product image updated.");
    await refresh(query);
  }

  async function onRemoveImage(img: StaffProductImage) {
    if (!selected) return;
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const res = await deleteStaffProductImage(client, img);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    await reloadImages(selected.stock_item_id);
    setMessage("Image removed.");
    await refresh(query);
  }

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading product pages…</p>;
  }
  if (status.kind === "error") {
    return <p className={styles.formStatus}>{status.message}</p>;
  }

  return (
    <div className={styles.pageBody}>
      <p className={styles.lede}>
        Edit shop PDP merchandising: price, discount, product images, and
        optional <strong>home-rail pins</strong> (Featured / Fast movers /
        Newest). Pins sit in front of the automatic ranking; unpinned slots still
        fill algorithmically. Title, fitment, OEM, and EPC diagram stay
        catalog-owned.
      </p>
      {message ? <p className={styles.formStatus}>{message}</p> : null}

      <form
        className={styles.form}
        onSubmit={(e) => {
          e.preventDefault();
          void refresh(query);
        }}
      >
        <label className={styles.field}>
          Search OEM / catalog title
          <input value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
        <div className={styles.formActions}>
          <button type="submit" className={styles.btn}>
            Search
          </button>
        </div>
      </form>

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>OEM</th>
              <th>Catalog title</th>
              <th>Price</th>
              <th>Qty</th>
              <th>Discount</th>
              <th>Home pins</th>
              <th>Images</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {status.rows.map((r) => (
              <tr key={r.stock_item_id}>
                <td>
                  <code>{r.oem_part_number}</code>
                </td>
                <td>{r.catalog_title}</td>
                <td>
                  {r.unit_price != null
                    ? `${r.currency} ${Number(r.unit_price).toFixed(2)}`
                    : "—"}
                </td>
                <td>{Number(r.qty_saleable)}</td>
                <td>
                  {r.discount_kind === "none"
                    ? "—"
                    : `${r.discount_kind} ${r.discount_value}`}
                </td>
                <td>
                  {[
                    r.pin_featured ? "Featured" : null,
                    r.pin_movers ? "Movers" : null,
                    r.pin_newest ? "Newest" : null,
                  ]
                    .filter(Boolean)
                    .join(" · ") || "—"}
                </td>
                <td>{r.image_count}</td>
                <td>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={() => setSelectedId(r.stock_item_id)}
                  >
                    Edit
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {selected ? (
        <form className={styles.form} onSubmit={onSave}>
          <h2 className={styles.title}>Edit · {selected.oem_part_number}</h2>
          <p className={styles.muted}>
            Catalog title: <strong>{selected.catalog_title}</strong>
            {" · "}
            <Link
              href={`/parts/${encodeURIComponent(selected.oem_part_number)}`}
            >
              View PDP
            </Link>
          </p>
          <p className={styles.muted}>
            Fitment, part number, and EPC diagram are managed by the catalog
            pipeline — not editable here.
          </p>

          <label className={styles.field}>
            Product price (USD retail list)
            <input
              type="number"
              min={0}
              step="0.01"
              value={unitPrice}
              onChange={(e) => setUnitPrice(e.target.value)}
              required
            />
          </label>

          <label className={styles.field}>
            Discount type
            <select
              value={discountKind}
              onChange={(e) =>
                setDiscountKind(e.target.value as "none" | "percent" | "amount")
              }
            >
              <option value="none">None</option>
              <option value="percent">Percent off</option>
              <option value="amount">Amount off (USD)</option>
            </select>
          </label>

          {discountKind !== "none" ? (
            <>
              <label className={styles.field}>
                Discount value
                <input
                  type="number"
                  min={0}
                  step="0.01"
                  value={discountValue}
                  onChange={(e) => setDiscountValue(e.target.value)}
                />
              </label>
              <label className={styles.field}>
                Discount description
                <input
                  value={discountDescription}
                  onChange={(e) => setDiscountDescription(e.target.value)}
                  placeholder="e.g. Weekend brake promo"
                  required
                />
              </label>
            </>
          ) : null}

          <fieldset className={styles.field}>
            <legend>Home page rails (manual pins)</legend>
            <p className={styles.muted}>
              Optional backup control. Pinned items appear first on that rail;
              remaining slots still use the automatic rules (discount/qty /
              created date). Item must stay in stock and priced to show.
            </p>
            <label className={imgStyles.radioRow}>
              <input
                type="checkbox"
                checked={pinFeatured}
                onChange={(e) => setPinFeatured(e.target.checked)}
              />
              Pin to Featured products
            </label>
            <label className={imgStyles.radioRow}>
              <input
                type="checkbox"
                checked={pinMovers}
                onChange={(e) => setPinMovers(e.target.checked)}
              />
              Pin to Fast movers
            </label>
            <label className={imgStyles.radioRow}>
              <input
                type="checkbox"
                checked={pinNewest}
                onChange={(e) => setPinNewest(e.target.checked)}
              />
              Pin to Newest additions
            </label>
            <label className={styles.field}>
              Pin sort (lower = earlier among pins)
              <input
                type="number"
                min={0}
                max={999}
                step={1}
                value={pinSort}
                onChange={(e) => setPinSort(e.target.value)}
              />
            </label>
          </fieldset>

          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy}>
              Save merchandising
            </button>
          </div>

          <section
            className={imgStyles.section}
            aria-labelledby="product-images-heading"
          >
            <h3 id="product-images-heading" className={imgStyles.sectionTitle}>
              Product images
            </h3>
            <p className={imgStyles.hint}>
              Upload one or more photos, then select which one is the{" "}
              <strong>main product image</strong> shown first on the PDP.
            </p>

            <div className={imgStyles.uploadRow}>
              <label className={imgStyles.uploadBtn}>
                {busy ? "Uploading…" : "Upload product images"}
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  multiple
                  disabled={busy}
                  onChange={(e) => {
                    const list = e.target.files;
                    e.target.value = "";
                    void onUploadFiles(list);
                  }}
                />
              </label>
              {images.length > 0 ? (
                <span className={styles.muted}>
                  {images.length} image{images.length === 1 ? "" : "s"} · pick
                  main below
                </span>
              ) : null}
            </div>

            {images.length ? (
              <ul className={imgStyles.grid}>
                {images.map((img) => (
                  <li
                    key={img.id}
                    className={
                      img.is_primary
                        ? `${imgStyles.card} ${imgStyles.cardMain}`
                        : imgStyles.card
                    }
                  >
                    <div className={imgStyles.thumbWrap}>
                      {/* eslint-disable-next-line @next/next/no-img-element */}
                      <img
                        className={imgStyles.thumb}
                        src={img.public_url}
                        alt={
                          img.is_primary
                            ? `Main product image for ${selected.oem_part_number}`
                            : `Product image for ${selected.oem_part_number}`
                        }
                      />
                      {img.is_primary ? (
                        <span className={imgStyles.badge}>Main</span>
                      ) : null}
                    </div>
                    <div className={imgStyles.actions}>
                      <label className={imgStyles.radioRow}>
                        <input
                          type="radio"
                          name={`main-image-${selected.stock_item_id}`}
                          checked={img.is_primary}
                          disabled={busy}
                          onChange={() => void onSelectMain(img.id)}
                        />
                        Use as main image
                      </label>
                      <button
                        type="button"
                        className={imgStyles.removeBtn}
                        disabled={busy}
                        onClick={() => void onRemoveImage(img)}
                      >
                        Remove
                      </button>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p className={imgStyles.empty}>
                No product images yet — upload at least one, then mark it as
                main.
              </p>
            )}
          </section>
        </form>
      ) : null}
    </div>
  );
}
