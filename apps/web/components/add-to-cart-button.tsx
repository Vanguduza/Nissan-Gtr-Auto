"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  addCartLineByOem,
  requireSession,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "@/app/(storefront)/parts/[oem]/pdp.module.css";

export function AddToCartButton({ oem }: { oem: string }) {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function onAdd() {
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }

    const session = await requireSession(client);
    if (!session.ok) {
      setBusy(false);
      router.push("/login");
      return;
    }

    const result = await addCartLineByOem(client, oem, 1);
    setBusy(false);
    if (!result.ok) {
      setMessage(result.error);
      return;
    }
    router.push("/cart");
  }

  return (
    <div>
      <button
        type="button"
        className={styles.add}
        disabled={busy}
        onClick={() => void onAdd()}
      >
        {busy ? "Adding…" : "Add to cart"}
      </button>
      {message ? <p className={styles.muted}>{message}</p> : null}
    </div>
  );
}
