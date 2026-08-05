import { CatalogBrowse } from "@/components/catalog-browse";

export const metadata = { title: "Catalog" };

export default async function CatalogPage({
  searchParams,
}: {
  searchParams: Promise<{
    cat?: string;
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
      sort={sp.sort}
      minUsd={sp.min}
      maxUsd={sp.max}
    />
  );
}
