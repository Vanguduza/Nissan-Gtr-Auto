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
  return <PartDetail oem={decoded} />;
}
