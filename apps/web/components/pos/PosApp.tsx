"use client";

import { CircleAlert, CircleCheck, FlaskConical, WifiOff, X } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { haptic, hapticsEnabled, hapticsSupported, setHapticsEnabled } from "@/lib/pos/haptics";
import type { PosGateway } from "@/lib/pos/gateway";
import type { OfflineCatalog } from "@/lib/pos/offline/catalog";
import { usePos, type PosStore } from "@/lib/pos/use-pos";
import { useWindowClass } from "@/lib/pos/window-class";
import { CurrentSale } from "./CurrentSale";
import { OfflineCatalogSetting } from "./OfflineCatalogSetting";
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

/** Pair the companion scanner phone to this sale: the phone scans, lines land here live. */
function CompanionDialog({ pos, onClose }: { pos: PosStore; onClose: () => void }) {
  const c = pos.companion;
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const t = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(t);
  }, []);
  const left = c ? Math.max(0, Math.floor((new Date(c.expiresAt).getTime() - now) / 1000)) : 0;
  const status = !c
    ? null
    : c.status === "claimed"
      ? "Phone connected — scans go straight into this sale."
      : c.status === "open" && left > 0
        ? `Waiting for the phone · code expires in ${Math.floor(left / 60)}:${String(left % 60).padStart(2, "0")}`
        : "This code has expired or was ended. Create a new one.";
  return (
    <Modal title="Companion phone" onClose={onClose}>
      <p className={styles.muted}>
        On a phone signed in with your staff account, open POS, then Settings, then Scan for a till, and enter this code. The phone
        uses its own camera; this browser never opens a camera.
      </p>
      {c ? (
        <>
          <div className={styles.pairingCode} aria-live="polite">{c.pairingCode}</div>
          <p className={styles.muted} role="status">{status}</p>
        </>
      ) : null}
      <div className={styles.rowEnd}>
        {c ? (
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} onClick={() => void pos.unpairCompanion()}>
            End pairing
          </button>
        ) : null}
        <button
          type="button"
          className={styles.primaryButton}
          disabled={!pos.online}
          onClick={() => void pos.pairCompanion()}
          autoFocus
        >
          {c ? "New code" : "Create pairing code"}
        </button>
      </div>
    </Modal>
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

function Destination({
  pos,
  offline,
  onPortal,
  onQuote,
  onCompanion,
}: {
  pos: PosStore;
  offline: OfflineCatalog | null;
  onPortal: () => void;
  onQuote: () => void;
  onCompanion: () => void;
}) {
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
            {offline ? <OfflineCatalogSetting catalog={offline} /> : null}
            <div className={styles.listRow}>
              <span>
                <div className={styles.listTitle}>Companion phone</div>
                <div className={styles.muted}>
                  {pos.companion?.status === "claimed" ? "Paired with this sale." : "Pair the scanner phone to add parts to this sale by scanning."}
                </div>
              </span>
              <button type="button" className={styles.primaryButton} onClick={onCompanion}>
                {pos.companion ? "Manage" : "Pair"}
              </button>
            </div>
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

export function PosApp({ gateway, offline = null }: { gateway: PosGateway; offline?: OfflineCatalog | null }) {
  const pos = usePos(gateway);
  const [portal, setPortal] = useState(false);
  const [paying, setPaying] = useState(false);
  const [quoting, setQuoting] = useState(false);
  const [companionOpen, setCompanionOpen] = useState(false);
  const [paper, setPaper] = useState<"80mm" | "A4">("80mm");

  const { windowClass, cartMode } = useWindowClass();

  return (
    <div className={styles.root} data-window={windowClass} data-cart={cartMode}>
    <div id="pos-shell" className={styles.app}>
      <PosRail active={pos.destination} onSelect={pos.setDestination} windowClass={windowClass} />
      <div className={styles.main}>
        <PosHeader pos={pos} windowClass={windowClass} onScan={() => setCompanionOpen(true)} />
        <main className={styles.canvas}>
          {pos.isPreview ? (
            <div className={`${styles.statusBanner} ${styles.statusPreview}`} role="status">
              <FlaskConical size={16} aria-hidden /> Preview data — development only, not connected to the shop database. Preview manager: “manager” /
              “preview”.
            </div>
          ) : null}
          {!pos.online ? (
            <div className={`${styles.statusBanner} ${styles.statusOffline}`} role="status">
              <WifiOff size={16} aria-hidden />{" "}
              {offline?.ready
                ? "Offline — searching and browsing the downloaded catalogue. Sales resume when the connection returns."
                : "Offline — browsing only. Sales resume when the connection returns."}
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
          <Destination pos={pos} offline={offline} onPortal={() => setPortal(true)} onQuote={() => setQuoting(true)} onCompanion={() => setCompanionOpen(true)} />
        </main>
        <CurrentSale
          pos={pos}
          mode={cartMode}
          onAddCustomer={() => pos.setDestination("customer")}
          onPay={() => {
            pos.dismissError();
            setPaying(true);
          }}
        />
      </div>
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
      {companionOpen ? <CompanionDialog pos={pos} onClose={() => setCompanionOpen(false)} /> : null}
      {portal ? <StaffPortalDialog gateway={gateway} onClose={() => setPortal(false)} /> : null}
      <div id="pos-layers" />
    </div>
  );
}
