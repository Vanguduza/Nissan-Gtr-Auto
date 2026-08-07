import { PartDetail } from "@/components/part-detail";

export async function generateMetadata({
  params,
}: {
  params: Promise<{ oem: string }>;
}) {
  const { oem } = await params;
  let decoded = oem;
  try {
    decoded = decodeURIComponent(oem);
  } catch {
    /* keep raw */
  }
  return { title: `${decoded} · Part` };
}

export default async function PartPage({
  params,
  searchParams,
}: {
  params: Promise<{ oem: string }>;
  searchParams: Promise<{ from?: string }>;
}) {
  const { oem } = await params;
  const sp = await searchParams;
  let decoded = oem;
  try {
    decoded = decodeURIComponent(oem);
  } catch {
    /* keep raw */
  }
  return (
    <PartDetail oem={decoded} fromEpc={sp.from === "epc"} />
  );
}
