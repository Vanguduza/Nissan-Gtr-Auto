import { SearchFourWay } from "@/components/search-four-way";
import styles from "../page.module.css";

export const metadata = { title: "Search" };

export default async function SearchPage({
  searchParams,
}: {
  searchParams: Promise<{ mode?: string; q?: string }>;
}) {
  const sp = await searchParams;
  const mode = sp.mode ?? "part";
  const q = sp.q ?? "";

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Search</h1>
      <p className={styles.lede}>
        Four-way lookup. Results list will bind to the search index in Phase 7.
      </p>
      <SearchFourWay />
      {q ? (
        <div className={styles.resultStub}>
          <p>
            Query <strong>{q}</strong> via <strong>{mode}</strong> — no index
            yet. When live, this lists stocked OEM lines with price-list totals.
          </p>
        </div>
      ) : null}
    </div>
  );
}
