import { CatalogBrowse } from "@/components/catalog-browse";

export const metadata = { title: "Catalog" };

export default async function CatalogPage({
  searchParams,
}: {
  searchParams: Promise<{ cat?: string; brand?: string }>;
}) {
  const sp = await searchParams;
  return <CatalogBrowse category={sp.cat} />;
}
