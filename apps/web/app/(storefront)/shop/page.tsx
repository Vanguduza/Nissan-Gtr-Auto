import { CatalogBrowse } from "@/components/catalog-browse";

export const metadata = { title: "Shop stock" };

/**
 * Relocated stock PLP — formerly `/catalog`.
 * EPC hierarchy browse now owns `/catalog`.
 */
export default async function ShopPage({
  searchParams,
}: {
  searchParams: Promise<{
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
      category={sp.cat}
      subcategory={sp.sub}
      sort={sp.sort}
      minUsd={sp.min}
      maxUsd={sp.max}
    />
  );
}
