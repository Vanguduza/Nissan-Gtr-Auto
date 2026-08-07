import { EpcMakerHub } from "@/components/epc/epc-maker-hub";

export const metadata = { title: "Parts catalog (EPC)" };

/** Megazip-style hierarchy maker hub. Stock PLP moved to /shop. */
export default function CatalogPage() {
  return <EpcMakerHub />;
}
