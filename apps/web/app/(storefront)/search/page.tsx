import { redirect } from "next/navigation";
import styles from "../page.module.css";

export const metadata = { title: "Search" };

/**
 * Customer search is plain product search over stock. Part numbers, VIN
 * decoding, PNC and EPC lookups run in the background only and are never
 * shown to customers (owner decision 2026-10-10); staff use /catalog.
 */
export default async function SearchPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const sp = await searchParams;
  const q = sp.q?.trim() ?? "";
  if (q) redirect(`/shop?q=${encodeURIComponent(q)}`);

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Search parts</h1>
      <p className={styles.lede}>
        Search by what you need, for example “brake pads” or “oil filter”.
      </p>
      <form action="/shop" method="get" role="search" className={styles.searchForm}>
        <label htmlFor="search-q" className={styles.srOnly}>
          Search parts
        </label>
        <input
          id="search-q"
          name="q"
          type="search"
          required
          placeholder="Search parts"
          autoComplete="off"
          className={styles.searchInput}
        />
        <button type="submit" className={styles.rowCta}>
          Search
        </button>
      </form>
    </div>
  );
}
