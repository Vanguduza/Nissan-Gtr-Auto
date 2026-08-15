"use client";

import {
  useCallback,
  useEffect,
  useMemo,
  useState,
  type FormEvent,
} from "react";
import account from "@/components/account.module.css";
import styles from "@/components/master-stock-report.module.css";
import { createWebClient } from "@/lib/supabase";
import {
  exportMasterStockCsv,
  listMasterStock,
  listMasterStockChassisOptions,
  masterStockCategoryOptions,
  masterStockSubcategoryOptions,
  sumMasterStockQty,
  MASTER_STOCK_EXPORT_LIMIT,
  MASTER_STOCK_PAGE_LIMIT,
  type MasterStockChassisOption,
  type MasterStockFilters,
  type MasterStockRow,
} from "@/lib/staff-warehouse";

function formatQty(n: number): string {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(
    Number(n) || 0,
  );
}

export function MasterStockPanel() {
  const [rows, setRows] = useState<MasterStockRow[]>([]);
  const [chassisOptions, setChassisOptions] = useState<
    MasterStockChassisOption[]
  >([]);
  const [query, setQuery] = useState("");
  const [chassisCode, setChassisCode] = useState("");
  const [categorySlug, setCategorySlug] = useState("");
  const [subcategorySlug, setSubcategorySlug] = useState("");
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const categoryOptions = useMemo(() => masterStockCategoryOptions(), []);
  const subcategoryOptions = useMemo(
    () => masterStockSubcategoryOptions(categorySlug),
    [categorySlug],
  );

  const filters = useMemo<MasterStockFilters>(
    () => ({
      query,
      chassisCode: chassisCode || undefined,
      categorySlug: categorySlug || undefined,
      subcategorySlug: subcategorySlug || undefined,
    }),
    [query, chassisCode, categorySlug, subcategorySlug],
  );

  const hasActiveFilters = Boolean(
    query.trim() || chassisCode || categorySlug || subcategorySlug,
  );

  const sums = useMemo(() => sumMasterStockQty(rows), [rows]);

  const load = useCallback(async (next: MasterStockFilters) => {
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setMessage(null);
    const result = await listMasterStock(client, next, {
      limit: MASTER_STOCK_PAGE_LIMIT,
    });
    setLoading(false);
    if (!result.ok) {
      setMessage(result.error);
      setRows([]);
      return;
    }
    setRows(result.data);
  }, []);

  useEffect(() => {
    void load({});
    const client = createWebClient();
    if (!client) return;
    void listMasterStockChassisOptions(client).then((res) => {
      if (res.ok) setChassisOptions(res.data);
    });
  }, [load]);

  const onApply = (e: FormEvent) => {
    e.preventDefault();
    void load(filters);
  };

  const onClear = () => {
    setQuery("");
    setChassisCode("");
    setCategorySlug("");
    setSubcategorySlug("");
    void load({});
  };

  const onExport = async (scope: "filtered" | "all") => {
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      return;
    }
    setExporting(true);
    setMessage(null);
    const exportFilters = scope === "all" ? {} : filters;
    const result = await listMasterStock(client, exportFilters, {
      limit: MASTER_STOCK_EXPORT_LIMIT,
    });
    setExporting(false);
    if (!result.ok) {
      setMessage(result.error);
      return;
    }
    if (!result.data.length) {
      setMessage(
        scope === "all"
          ? "No stock rows to export."
          : "No rows match the current filters.",
      );
      return;
    }
    exportMasterStockCsv(result.data, scope);
    setMessage(
      `Exported ${result.data.length} row${result.data.length === 1 ? "" : "s"} (${scope === "all" ? "whole stock" : "current filters"}).`,
    );
  };

  return (
    <div className={styles.report}>
      <form className={styles.toolbar} onSubmit={onApply}>
        <div className={styles.filters}>
          <label className={styles.field}>
            Model (chassis)
            <select
              value={chassisCode}
              onChange={(e) => setChassisCode(e.target.value)}
            >
              <option value="">All models</option>
              {chassisOptions.map((o) => (
                <option key={o.chassisCode} value={o.chassisCode}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            Category
            <select
              value={categorySlug}
              onChange={(e) => {
                setCategorySlug(e.target.value);
                setSubcategorySlug("");
              }}
            >
              <option value="">All categories</option>
              {categoryOptions.map((o) => (
                <option key={o.slug} value={o.slug}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            Subcategory
            <select
              value={subcategorySlug}
              onChange={(e) => setSubcategorySlug(e.target.value)}
              disabled={!categorySlug}
            >
              <option value="">All subcategories</option>
              {subcategoryOptions.map((o) => (
                <option key={o.slug} value={o.slug}>
                  {o.label}
                </option>
              ))}
            </select>
          </label>
          <label className={styles.field}>
            OEM / description
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search part number or name"
              autoComplete="off"
            />
          </label>
        </div>
        <div className={styles.actions}>
          <button type="submit" className={account.btn} disabled={loading}>
            Apply filters
          </button>
          <button
            type="button"
            className={account.btnGhost}
            onClick={onClear}
            disabled={loading || !hasActiveFilters}
          >
            Clear
          </button>
          <button
            type="button"
            className={account.btnGhost}
            onClick={() => void onExport("filtered")}
            disabled={exporting || loading}
          >
            Export filtered CSV
          </button>
          <button
            type="button"
            className={account.btnGhost}
            onClick={() => void onExport("all")}
            disabled={exporting || loading}
          >
            Export all CSV
          </button>
        </div>
      </form>

      {message ? <p className={account.formStatus}>{message}</p> : null}

      <div className={styles.statusBar} aria-live="polite">
        <p>
          {loading ? (
            "Loading…"
          ) : (
            <>
              <strong>{rows.length}</strong>
              {rows.length >= MASTER_STOCK_PAGE_LIMIT
                ? ` rows (capped at ${MASTER_STOCK_PAGE_LIMIT} — narrow filters or export)`
                : ` row${rows.length === 1 ? "" : "s"}`}
              {hasActiveFilters ? " · filtered" : " · all stock"}
            </>
          )}
        </p>
        {!loading && rows.length > 0 ? (
          <div className={styles.totals}>
            <span>
              Σ Total <strong>{formatQty(sums.total)}</strong>
            </span>
            <span>
              Σ WH1 <strong>{formatQty(sums.wh1)}</strong>
            </span>
            <span>
              Σ WH2 <strong>{formatQty(sums.wh2)}</strong>
            </span>
          </div>
        ) : null}
      </div>

      <div className={styles.tableShell}>
        {loading ? (
          <p className={styles.loading}>Loading master stock…</p>
        ) : rows.length === 0 ? (
          <p className={styles.empty}>
            {hasActiveFilters
              ? "No stock matches these filters."
              : "No master stock rows yet."}
          </p>
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                <th className={styles.stickyOem} scope="col">
                  OEM
                </th>
                <th scope="col">Description</th>
                <th scope="col">Model</th>
                <th scope="col">Category</th>
                <th scope="col">Subcategory</th>
                <th className={styles.num} scope="col">
                  Total
                </th>
                <th className={styles.num} scope="col">
                  WH1
                </th>
                <th className={styles.num} scope="col">
                  WH2
                </th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.stock_item_id}>
                  <td className={styles.stickyOem}>{r.oem_part_number}</td>
                  <td>{r.description ?? <span className={styles.muted}>—</span>}</td>
                  <td>
                    {r.chassis_codes ?? (
                      <span className={styles.muted}>—</span>
                    )}
                  </td>
                  <td>
                    {r.category_name ?? (
                      <span className={styles.muted}>—</span>
                    )}
                  </td>
                  <td>
                    {r.subcategory_name ?? (
                      <span className={styles.muted}>—</span>
                    )}
                  </td>
                  <td className={styles.num}>{formatQty(r.qty_total)}</td>
                  <td className={styles.num}>{formatQty(r.qty_wh1)}</td>
                  <td className={styles.num}>{formatQty(r.qty_wh2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <p className={styles.hint}>
        WH1 = receiving · WH2 = storefloor. CSV export re-fetches up to{" "}
        {MASTER_STOCK_EXPORT_LIMIT.toLocaleString()} rows (filtered or whole
        stock). Move WH1 → WH2 via approved stock transfers.
      </p>
    </div>
  );
}
