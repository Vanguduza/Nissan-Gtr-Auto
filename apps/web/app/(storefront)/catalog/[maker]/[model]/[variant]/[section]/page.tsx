import { EpcDiagramHub } from "@/components/epc/epc-diagram-hub";

export const metadata = { title: "Diagram · EPC" };

export default async function CatalogSectionPage({
  params,
}: {
  params: Promise<{
    maker: string;
    model: string;
    variant: string;
    section: string;
  }>;
}) {
  const { maker, model, variant, section } = await params;
  return (
    <EpcDiagramHub
      maker={decodeURIComponent(maker)}
      model={decodeURIComponent(model)}
      variant={decodeURIComponent(variant)}
      section={decodeURIComponent(section)}
    />
  );
}
