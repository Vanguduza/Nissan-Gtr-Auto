import { PartDetail } from "@/components/part-detail";

// Part numbers stay off customer surfaces (owner decision 2026-10-10).
export const metadata = { title: "Part" };

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
  return (
    <PartDetail oem={decoded} />
  );
}
