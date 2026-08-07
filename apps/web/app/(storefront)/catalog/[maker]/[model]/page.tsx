import { EpcVariantHub } from "@/components/epc/epc-variant-hub";

export const metadata = { title: "Variants · EPC" };

export default async function CatalogModelPage({
  params,
}: {
  params: Promise<{ maker: string; model: string }>;
}) {
  const { maker, model } = await params;
  return (
    <EpcVariantHub
      maker={decodeURIComponent(maker)}
      model={decodeURIComponent(model)}
    />
  );
}
