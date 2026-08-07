import { EpcModelHub } from "@/components/epc/epc-model-hub";

export const metadata = { title: "Models · EPC" };

export default async function CatalogMakerPage({
  params,
}: {
  params: Promise<{ maker: string }>;
}) {
  const { maker } = await params;
  return <EpcModelHub maker={decodeURIComponent(maker)} />;
}
