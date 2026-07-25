import Link from "next/link";
import { CustomerDeliveryTrackPanel } from "@/components/customer-delivery-track-panel";
import styles from "./track.module.css";

export const metadata = { title: "Track delivery" };
export const dynamic = "force-dynamic";

type Props = { params: Promise<{ token: string }> };

export default async function CustomerTrackPage({ params }: Props) {
  const { token: raw } = await params;
  const token = decodeURIComponent(raw ?? "").trim();

  return (
    <div className={styles.shell}>
      <p className={styles.brand}>
        <Link href="/">Nissan GTR Auto</Link>
      </p>
      <div className={styles.card}>
        <h1 className={styles.title}>Live delivery</h1>
        <p className={styles.lede}>
          Last known location and ETA while your order is out for delivery.
          Historical GPS trail is never shown.
        </p>
        {!token || token.length < 8 || /[^a-zA-Z0-9_-]/.test(token) ? (
          <p className={styles.alert} role="alert">
            Invalid tracking link.
          </p>
        ) : (
          <CustomerDeliveryTrackPanel token={token} />
        )}
      </div>
    </div>
  );
}
