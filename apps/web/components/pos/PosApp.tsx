"use client";

import { CircleAlert, CircleCheck, FlaskConical, WifiOff, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { haptic, hapticsEnabled, hapticsSupported, setHapticsEnabled } from "@/lib/pos/haptics";
import type { PosGateway } from "@/lib/pos/gateway";
import { usePos, type PosStore } from "@/lib/pos/use-pos";
import { CurrentSale } from "./CurrentSale";
import { GarageChooser, ManagerDialog, Modal, PaymentDialog, ReceiptView } from "./PosDialogs";
import { PosHeader } from "./PosHeader";
import { PosHome, PosSearchResults } from "./PosHome";
import { PosRail } from "./PosRail";
import { CustomerScreen, EpcScreen, OrdersScreen, QuickSaleScreen, ReturnsScreen } from "./PosScreens";
import styles from "./pos.module.css";

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
          className={styles.input}
          style={{ width: "100%", marginTop: 12 }}
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-label="Password"
          autoFocus
        />
        {error ? <p className={`${styles.statusBanner} ${styles.statusError}`} role="alert">{error}</p> : null}
        <div className={styles.rowEnd}>
          <button type="submit" className={styles.primaryButton} disabled={busy || !password}>
            Continue
          </button>
        </div>
      </form>
    </Modal>
  );
}

/** Device-local on/off for vibration feedback; shows plainly when the device cannot vibrate. */
function HapticsSetting() {
  const [supported, setSupported] = useState(false);
  const [on, setOn] = useState(true);
  useEffect(() => {
    setSupported(hapticsSupported());
    setOn(hapticsEnabled());
  }, []);
  return (
    <div className={styles.listRow}>
      <span>
        <div className={styles.listTitle}>Haptic feedback</div>
        <div className={styles.muted}>
          {supported
            ? "Short vibrations confirm adds, pins, long-presses, completed sales and errors."
            : "This device or browser cannot vibrate (iPhone, iPad and desktop browsers). Visual feedback still applies."}
        </div>
      </span>
      <div className={styles.segment} role="group" aria-label="Haptic feedback">
        {[true, false].map((v) => (
          <button
            key={String(v)}
            type="button"
            aria-pressed={on === v}
            disabled={!supported}
            className={`${styles.segmentItem} ${on === v ? styles.segmentActive : ""}`}
            onClick={() => {
              setHapticsEnabled(v);
              setOn(v);
              if (v) haptic("success");
            }}
          >
            {v ? "On" : "Off"}
          </button>
        ))}
      </div>
    </div>
  );
}

function QuoteDialog({ pos, onClose }: { pos: PosStore; onClose: () => void }) {
  const [validUntil, setValidUntil] = useState(() => new Date(Date.now() + 14 * 864e5).toISOString().slice(0, 10));
  const [notes, setNotes] = useState("");
  return (
    <Modal title="Create quotation" onClose={onClose}>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          if (await pos.quoteCurrent(validUntil || null, notes.trim() || null)) onClose();
        }}
      >
        <div className={styles.formGrid}>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Valid until</span>
            <input className={styles.input} type="date" value={validUntil} onChange={(e) => setValidUntil(e.target.value)} />
          </label>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Notes</span>
            <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} />
          </label>
        </div>
        <div className={styles.rowEnd}>
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={onClose}>
            Cancel
          </button>
          <button type="submit" className={styles.primaryButton}>
            Create quotation
          </button>
        </div>
      </form>
    </Modal>
  );
}

function Destination({ pos, onPortal, onQuote }: { pos: PosStore; onPortal: () => void; onQuote: () => void }) {
  switch (pos.destination) {
    case "home":
      return <PosHome pos={pos} />;
    case "search":
      return <PosSearchResults pos={pos} />;
    case "quickSale":
      return <QuickSaleScreen pos={pos} onQuote={onQuote} />;
    case "customer":
      return <CustomerScreen pos={pos} />;
    case "orders":
      return <OrdersScreen pos={pos} />;
    case "returns":
      return <ReturnsScreen pos={pos} />;
    case "epc":
      return <EpcScreen pos={pos} />;
    case "settings":
      return (
        <section className={styles.panel}>
          <h2 className={styles.panelTitle}>Settings</h2>
          <div className={styles.list}>
            <div className={styles.listRow}>
              <span>
                <div className={styles.listTitle}>Staff portal</div>
                <div className={styles.muted}>Management features open after you confirm your password.</div>
              </span>
              <button type="button" className={styles.primaryButton} onClick={onPortal}>
                Open
              </button>
            </div>
            <HapticsSetting />
            <div className={styles.listRow}>
              <span>
                <div className={styles.listTitle}>Scanner</div>
                <div className={styles.muted}>
                  USB and Bluetooth barcode scanners type straight into the search field. Camera scanning runs on the counter tablet and the
                  companion phone, not in the browser.
                </div>
              </span>
            </div>
            <div className={styles.listRow}>
              <span>
                <div className={styles.listTitle}>Receipts</div>
                <div className={styles.muted}>
                  Printed from the browser at 80 mm or A4 after each sale. ESC/POS printers and the cash drawer are driven by the counter
                  tablet.
                </div>
              </span>
            </div>
          </div>
        </section>
      );
  }
}

export function PosApp({ gateway }: { gateway: PosGateway }) {
  const pos = usePos(gateway);
  const [portal, setPortal] = useState(false);
  const [paying, setPaying] = useState(false);
  const [quoting, setQuoting] = useState(false);
  const [paper, setPaper] = useState<"80mm" | "A4">("80mm");

  return (
    <div className={styles.app}>
      <PosRail active={pos.destination} onSelect={pos.setDestination} />
      <div className={styles.main}>
        <PosHeader pos={pos} />
        <main className={styles.canvas}>
          {pos.isPreview ? (
            <div className={`${styles.statusBanner} ${styles.statusPreview}`} role="status">
              <FlaskConical size={16} aria-hidden /> Preview data — development only, not connected to the shop database. Preview manager: “manager” /
              “preview”.
            </div>
          ) : null}
          {!pos.online ? (
            <div className={`${styles.statusBanner} ${styles.statusOffline}`} role="status">
              <WifiOff size={16} aria-hidden /> Offline — browsing only. Sales resume when the connection returns.
            </div>
          ) : null}
          {pos.error && !pos.managerPrompt && !paying ? (
            <div className={`${styles.statusBanner} ${styles.statusError}`} role="alert">
              <CircleAlert size={16} aria-hidden /> {pos.error}
              <button type="button" className={`${styles.iconButton} ${styles.statusDismiss}`} aria-label="Dismiss" onClick={pos.dismissError}>
                <X size={14} aria-hidden />
              </button>
            </div>
          ) : null}
          {pos.notice ? (
            <div className={`${styles.statusBanner} ${styles.statusNotice}`} role="status">
              <CircleCheck size={16} aria-hidden /> {pos.notice}
              <button type="button" className={`${styles.iconButton} ${styles.statusDismiss}`} aria-label="Dismiss" onClick={pos.dismissNotice}>
                <X size={14} aria-hidden />
              </button>
            </div>
          ) : null}
          <Destination pos={pos} onPortal={() => setPortal(true)} onQuote={() => setQuoting(true)} />
        </main>
        <CurrentSale
          pos={pos}
          onAddCustomer={() => pos.setDestination("customer")}
          onPay={() => {
            pos.dismissError();
            setPaying(true);
          }}
        />
      </div>

      {paying || pos.lastReceipt ? <PaymentDialog pos={pos} onClose={() => setPaying(false)} paper={paper} setPaper={setPaper} /> : null}
      {pos.lastReceipt ? (
        <div className={styles.printOnly} aria-hidden>
          <ReceiptView receipt={pos.lastReceipt} paper={paper} />
        </div>
      ) : null}
      <ManagerDialog pos={pos} />
      <GarageChooser pos={pos} />
      {quoting ? <QuoteDialog pos={pos} onClose={() => setQuoting(false)} /> : null}
      {portal ? <StaffPortalDialog gateway={gateway} onClose={() => setPortal(false)} /> : null}
    </div>
  );
}
