"use client";

import { CircleAlert, FlaskConical, WifiOff, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useMemo, useState, type ReactNode } from "react";
import type { PosGateway } from "@/lib/pos/gateway";
import { usePos, type PosDestination } from "@/lib/pos/use-pos";
import { CurrentSale } from "./CurrentSale";
import { PosHeader } from "./PosHeader";
import { PosHome, PosSearchResults } from "./PosHome";
import { PosRail } from "./PosRail";
import styles from "./pos.module.css";

/** Screens not yet rebuilt on web state exactly what is pending — never a fake success. */
const PENDING: Partial<Record<PosDestination, { title: string; body: string }>> = {
  quickSale: { title: "Quick Sale", body: "Warehouse, currency and fulfilment setup for the sale is being rebuilt to the approved design." },
  customer: { title: "Customer", body: "Customer search, create/edit and garage vehicles are being rebuilt to the approved design." },
  orders: { title: "Orders", body: "Quotations and parked sales are being rebuilt to the approved design." },
  returns: { title: "Returns", body: "Manager-approved returns through the finance refund pipeline are being rebuilt to the approved design." },
  epc: { title: "EPC Browse", body: "Maker → model → variant → section → diagram browsing is being rebuilt to the approved design." },
};

function Modal({ title, children, onClose }: { title: string; children: ReactNode; onClose: () => void }) {
  return (
    <div className={styles.modalScrim} role="presentation" onClick={onClose}>
      <div className={styles.modal} role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <h2 className={styles.panelTitle}>{title}</h2>
        {children}
      </div>
    </div>
  );
}

function StaffPortalDialog({ gateway, onClose }: { gateway: PosGateway; onClose: () => void }) {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  return (
    <Modal title="Staff portal" onClose={onClose}>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          const res = await gateway.reauthenticate(password);
          setBusy(false);
          if (!res.ok) {
            setError(res.error);
            return;
          }
          router.push("/staff");
        }}
      >
        <p className={styles.muted}>Confirm your password to open management features.</p>
        <input
          className={`${styles.cascadeSelect} ${styles.focusable}`}
          style={{ width: "100%", maxWidth: "none", marginTop: 12 }}
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-label="Password"
          autoFocus
        />
        {error ? <p className={styles.statusError} role="alert">{error}</p> : null}
        <div className={styles.modalActions}>
          <button type="submit" className={styles.pay} style={{ width: "auto", padding: "0 24px", height: 44 }} disabled={busy || !password}>
            Continue
          </button>
        </div>
      </form>
    </Modal>
  );
}

export function PosApp({ gateway }: { gateway: PosGateway }) {
  const pos = usePos(gateway);
  const [portal, setPortal] = useState(false);
  const [notice, setNotice] = useState<{ title: string; body: string } | null>(null);

  const content = useMemo(() => {
    if (pos.destination === "home") return <PosHome pos={pos} />;
    if (pos.destination === "search") return <PosSearchResults pos={pos} />;
    if (pos.destination === "settings") {
      return (
        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>Settings</h2>
          <p className={styles.muted}>Management features open through the Staff portal after you confirm your password.</p>
          <button type="button" className={styles.softButton} style={{ marginTop: 16, padding: "0 18px" }} onClick={() => setPortal(true)}>
            Staff portal
          </button>
        </section>
      );
    }
    const pending = PENDING[pos.destination];
    return pending ? (
      <section className={styles.panel}>
        <h2 className={styles.panelTitle}>{pending.title}</h2>
        <p className={styles.muted}>{pending.body}</p>
      </section>
    ) : null;
  }, [pos]);

  return (
    <div className={styles.app}>
      <PosRail active={pos.destination} onSelect={pos.setDestination} />
      <div className={styles.main}>
        <PosHeader pos={pos} />
        <main className={styles.canvas}>
          {pos.isPreview ? (
            <div className={`${styles.statusBanner} ${styles.statusPreview}`} role="status">
              <FlaskConical size={16} aria-hidden /> Preview data — development only, not connected to the shop database.
            </div>
          ) : null}
          {!pos.online ? (
            <div className={`${styles.statusBanner} ${styles.statusOffline}`} role="status">
              <WifiOff size={16} aria-hidden /> Offline — browsing only. Sales resume when the connection returns.
            </div>
          ) : null}
          {pos.error ? (
            <div className={`${styles.statusBanner} ${styles.statusError}`} role="alert">
              <CircleAlert size={16} aria-hidden /> {pos.error}
              <button type="button" className={`${styles.iconButton} ${styles.statusDismiss}`} aria-label="Dismiss" onClick={pos.dismissError}>
                <X size={14} aria-hidden />
              </button>
            </div>
          ) : null}
          {content}
        </main>
        <CurrentSale
          pos={pos}
          onAddCustomer={() => setNotice(PENDING.customer ?? null)}
          onPay={() =>
            setNotice({
              title: "Payment",
              body: "Split tender, EcoCash and receipt delivery are being rebuilt to the approved design. No payment has been taken.",
            })
          }
        />
      </div>
      {portal ? <StaffPortalDialog gateway={gateway} onClose={() => setPortal(false)} /> : null}
      {notice ? (
        <Modal title={notice.title} onClose={() => setNotice(null)}>
          <p className={styles.muted}>{notice.body}</p>
          <div className={styles.modalActions}>
            <button type="button" className={styles.softButton} style={{ padding: "0 18px" }} onClick={() => setNotice(null)}>
              Close
            </button>
          </div>
        </Modal>
      ) : null}
    </div>
  );
}
