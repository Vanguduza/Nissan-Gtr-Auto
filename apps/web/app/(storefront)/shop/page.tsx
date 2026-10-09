import { CatalogBrowse } from "@/components/catalog-browse";

export const metadata = { title: "Shop stock" };

/** Customer stock PLP. `q` is plain product search (customer `/search` lands here). */
export default async function ShopPage({
  searchParams,
}: {
  searchParams: Promise<{
    q?: string;
    cat?: string;
    sub?: string;
    brand?: string;
    sort?: string;
    min?: string;
    max?: string;
  }>;
}) {
  const sp = await searchParams;
  return (
    <CatalogBrowse
      query={sp.q?.trim() || undefined}
      category={sp.cat}
      subcategory={sp.sub}
      sort={sp.sort}
      minUsd={sp.min}
      maxUsd={sp.max}
    />
  );
}
