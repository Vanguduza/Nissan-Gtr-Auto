import { EpcSectionHub } from "@/components/epc/epc-section-hub";

export const metadata = { title: "Sections · EPC" };

export default async function CatalogVariantPage({
  params,
}: {
  params: Promise<{ maker: string; model: string; variant: string }>;
}) {
  const { maker, model, variant } = await params;
  return (
    <EpcSectionHub
      maker={decodeURIComponent(maker)}
      model={decodeURIComponent(model)}
      variant={decodeURIComponent(variant)}
    />
  );
}
