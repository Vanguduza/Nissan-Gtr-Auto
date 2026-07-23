import { SearchFourWay } from "@/components/search-four-way";
import { SearchResults } from "@/components/search-results";
import { normalizeSearchMode } from "@/lib/catalog-search";
import { hasSupabaseEnv } from "@/lib/supabase";
import styles from "../page.module.css";

export const metadata = { title: "Search" };

export default async function SearchPage({
  searchParams,
}: {
  searchParams: Promise<{ mode?: string; q?: string }>;
}) {
  const sp = await searchParams;
  const mode = normalizeSearchMode(sp.mode);
  const q = sp.q?.trim() ?? "";
  const supabaseReady = hasSupabaseEnv();

  return (
    <div className={styles.page}>
      <h1 className={styles.title}>Search</h1>
      <p className={styles.lede}>
        Four-way catalog lookup by part number, VIN, model, or PNC. Live
        results use the signed-in catalog index.
      </p>
      <SearchFourWay initialMode={mode} initialQuery={q} />
      {q ? (
        supabaseReady ? (
          <SearchResults mode={mode} query={q} />
        ) : (
          <div className={styles.resultStub}>
            <p className={styles.muted}>
              Query <strong>{q}</strong> via <strong>{mode}</strong> — catalog
              search is unavailable until{" "}
              <code>NEXT_PUBLIC_SUPABASE_URL</code> and{" "}
              <code>NEXT_PUBLIC_SUPABASE_ANON_KEY</code> are configured.
            </p>
          </div>
        )
      ) : null}
    </div>
  );
}
