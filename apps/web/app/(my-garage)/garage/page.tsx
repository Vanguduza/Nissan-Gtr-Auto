import { redirect } from "next/navigation";

/** My Garage moved under My Account. */
export default function GarageRedirect() {
  redirect("/account/garage");
}
