"use client";

import { Eraser, FileSignature, Printer, Upload } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { formatMoney } from "@/lib/pos/money";
import type { BusinessProfile, LetterSourceKind, MySignature, PaymentLetter, PaymentLetterSummary } from "@/lib/pos/types";
import type { PosStore } from "@/lib/pos/use-pos";
import { Modal } from "./PosDialogs";
import styles from "./pos.module.css";

/*
 * Payment resolution letters (Blueprint §10.7, §10.11, phase 8). When a payment cannot be proven
 * either way (no answer, charged but not posted, a refund in progress), a manager issues a signed
 * letter that records what was observed: provider, references, card details, status. The letter is a
 * frozen copy on the server, signed with the issuing manager's own stored signature.
 */

const when = (iso: string | null) => (iso ? new Date(iso).toLocaleString(undefined, { dateStyle: "long", timeStyle: "short" }) : "");

const PROVIDER: Record<string, string> = {
  card_terminal: "Card machine",
  ecocash: "EcoCash",
  paynow: "Paynow",
  contipay: "ContiPay",
  cash: "Cash",
  bank: "Card / bank transfer",
  store_credit: "Store credit",
};
const provider = (p: string | null) => (p ? PROVIDER[p] ?? p.replace(/_/g, " ") : "—");
const status = (s: string | null) => (s ? s.replace(/_/g, " ") : "—");

/** Letters already issued for one payment, and issuing a new one (signed-in manager, finance or admin). */
export function PaymentLetters({ pos, kind, sourceId }: { pos: PosStore; kind: LetterSourceKind; sourceId: string }) {
  const [rows, setRows] = useState<PaymentLetterSummary[] | null>(null);
  const [notes, setNotes] = useState("");
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [viewing, setViewing] = useState<string | null>(null);
  const load = useCallback(async () => {
    const res = await pos.gateway.listLetters(kind, sourceId, "");
    // A cashier may not list letters; the block then only offers issuing to a manager.
    setRows(res.ok ? res.data : []);
  }, [pos.gateway, kind, sourceId]);
  useEffect(() => {
    void load();
  }, [load]);

  const issue = async () => {
    setBusy(true);
    const res = await pos.gateway.issueLetter(kind, sourceId, notes.trim() || null);
    setBusy(false);
    if (!res.ok) return pos.showError(res.error);
    setOpen(false);
    setNotes("");
    pos.showNotice("Payment letter issued.");
    void load();
    setViewing(res.data);
  };

  return (
    <div className={styles.panelInset} style={{ marginTop: 10 }}>
      <div className={styles.row} style={{ justifyContent: "space-between", flexWrap: "wrap", gap: 8 }}>
        <span>
          <strong>
            <FileSignature size={14} aria-hidden /> Payment letter
          </strong>
          <div className={styles.muted}>A signed record of this payment&apos;s status for the customer or the bank. Issued by a manager under their own signature.</div>
        </span>
        {!open ? (
          <button type="button" className={`${styles.softButton} ${styles.inlineButton}`} disabled={!pos.online} onClick={() => setOpen(true)}>
            Issue letter
          </button>
        ) : null}
      </div>
      {open ? (
        <>
          <label className={styles.field} style={{ marginTop: 10 }}>
            <span className={styles.fieldLabel}>Note on the letter (optional)</span>
            <input className={styles.input} value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="e.g. Customer asked for proof for their bank" />
          </label>
          {!pos.selfApprover ? <p className={styles.muted}>Only a signed-in manager, finance or admin can issue a letter.</p> : null}
          <div className={styles.rowEnd}>
            <button type="button" className={styles.softButton} onClick={() => setOpen(false)}>
              Cancel
            </button>
            <button type="button" className={styles.primaryButton} disabled={busy || !pos.online} onClick={() => void issue()}>
              {busy ? "Issuing…" : "Issue and open"}
            </button>
          </div>
        </>
      ) : null}
      {rows && rows.length > 0 ? (
        <div className={styles.list}>
          {rows.map((l) => (
            <div key={l.id} className={styles.listRow}>
              <span>
                <div className={styles.listTitle}>{l.documentNumber ?? l.id}</div>
                <div className={styles.muted}>{[status(l.observedStatus), l.managerName, when(l.issuedAt)].filter(Boolean).join(" · ")}</div>
              </span>
              <button type="button" className={styles.linkButton} onClick={() => setViewing(l.id)}>
                Open
              </button>
            </div>
          ))}
        </div>
      ) : null}
      {viewing ? <LetterDialog pos={pos} letterId={viewing} onClose={() => setViewing(null)} /> : null}
    </div>
  );
}

export function LetterDialog({ pos, letterId, onClose }: { pos: PosStore; letterId: string; onClose: () => void }) {
  const [letter, setLetter] = useState<PaymentLetter | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [slot, setSlot] = useState<HTMLElement | null>(null);
  useEffect(() => {
    setSlot(document.getElementById("pos-print-slot"));
    void pos.gateway.getLetter(letterId).then((res) => (res.ok ? setLetter(res.data) : setError(res.error)));
  }, [pos.gateway, letterId]);
  return (
    <Modal title="Payment letter" onClose={onClose} wide>
      {error ? <div className={styles.emptyCard}>{error}</div> : null}
      {!letter && !error ? <div className={styles.emptyCard}>Loading…</div> : null}
      {letter ? (
        <>
          <div style={{ border: "1px solid var(--gtr-color-neutral-border, #e5e7eb)", borderRadius: 8, padding: 4, background: "#fff" }}>
            <LetterDocument letter={letter} />
          </div>
          <div className={styles.rowEnd}>
            <button type="button" className={styles.softButton} onClick={onClose}>
              Close
            </button>
            <button
              type="button"
              className={styles.primaryButton}
              onClick={() => {
                const style = document.createElement("style");
                style.textContent = "@page { size: A4; margin: 18mm; }";
                document.head.appendChild(style);
                window.print();
                style.remove();
              }}
            >
              <Printer size={16} aria-hidden /> Print
            </button>
          </div>
          {slot ? createPortal(<LetterDocument letter={letter} />, slot) : null}
        </>
      ) : null}
    </Modal>
  );
}

/** The printable letter: business header, the payment as observed, the manager's signature. */
export function LetterDocument({ letter }: { letter: PaymentLetter }) {
  const b = letter.business;
  const facts: [string, string | null][] = [
    ["Customer", letter.customerName],
    ["Invoice", letter.invoiceNumber],
    ["Paid with", provider(letter.provider)],
    ["Amount", formatMoney(letter.amount, letter.currency)],
    ["Status when issued", status(letter.observedStatus)],
    ["Our reference", letter.externalReference],
    ["Provider reference", letter.providerReference],
    ["Card machine transaction", letter.terminalTransactionId],
    ["RRN", letter.rrn],
    ["Authorisation code", letter.authorizationCode],
    ["Card", letter.cardLast4 ? `${letter.cardScheme ?? "Card"} ending ${letter.cardLast4}` : null],
    ["Detail", letter.failureDetail],
  ];
  return (
    <article style={{ color: "#111", background: "#fff", padding: "28px 32px", fontSize: 13, lineHeight: 1.5, fontFamily: "inherit" }}>
      <header style={{ display: "flex", justifyContent: "space-between", gap: 16, borderBottom: "2px solid #c8102e", paddingBottom: 12 }}>
        <div>
          <div style={{ fontSize: 20, fontWeight: 800 }}>{b?.tradingName ?? "Nissan GTR Auto"}</div>
          {b && b.legalName !== b.tradingName ? <div>{b.legalName}</div> : null}
          <div style={{ color: "#555" }}>{[b?.addressLine1, b?.addressLine2, b?.city, b?.country].filter(Boolean).join(", ")}</div>
        </div>
        <div style={{ textAlign: "right", color: "#555" }}>
          {[b?.phone, b?.email, b?.domain].filter(Boolean).map((x) => (
            <div key={x}>{x}</div>
          ))}
          {b?.registrationNumber ? <div>Reg. {b.registrationNumber}</div> : null}
        </div>
      </header>
      <h1 style={{ fontSize: 18, margin: "18px 0 4px" }}>Payment status letter</h1>
      <div style={{ color: "#555" }}>
        {letter.documentNumber} · {when(letter.issuedAt)}
      </div>
      <p style={{ marginTop: 14 }}>
        This letter records the status of the payment below as seen in our records at the time it was issued. It is not a receipt; a posted sale
        has its own invoice.
      </p>
      <table style={{ borderCollapse: "collapse", width: "100%", marginTop: 8 }}>
        <tbody>
          {facts
            .filter(([, v]) => v)
            .map(([k, v]) => (
              <tr key={k}>
                <td style={{ padding: "4px 12px 4px 0", color: "#555", width: "38%", verticalAlign: "top" }}>{k}</td>
                <td style={{ padding: "4px 0", fontWeight: 600 }}>{v}</td>
              </tr>
            ))}
        </tbody>
      </table>
      {letter.issueNotes ? <p style={{ marginTop: 12 }}>{letter.issueNotes}</p> : null}
      <footer style={{ marginTop: 28 }}>
        {letter.signatureUrl ? (
          // eslint-disable-next-line @next/next/no-img-element
          <img src={letter.signatureUrl} alt="Signature" style={{ height: 64, maxWidth: 260, objectFit: "contain", display: "block" }} />
        ) : (
          <div style={{ height: 48, color: "#777", fontStyle: "italic" }}>Signature on file{letter.signatureSha256 ? ` (${letter.signatureSha256.slice(0, 12)}…)` : ""}</div>
        )}
        <div style={{ borderTop: "1px solid #999", width: 260, paddingTop: 4 }}>
          <strong>{letter.managerName}</strong>
          <div style={{ color: "#555" }}>{[letter.managerTitle, letter.managerEmployeeCode].filter(Boolean).join(" · ")}</div>
        </div>
      </footer>
    </article>
  );
}

/** Settings → My signature: draw it or upload an image; letters use the one on file when issued. */
export function SignatureSetting({ pos }: { pos: PosStore }) {
  const [sig, setSig] = useState<MySignature | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [drawn, setDrawn] = useState(false);
  const [busy, setBusy] = useState(false);
  const canvas = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  useEffect(() => {
    void pos.gateway.getMySignature().then((res) => (res.ok ? setSig(res.data) : setError(res.error)));
  }, [pos.gateway]);

  const point = (e: React.PointerEvent<HTMLCanvasElement>) => {
    const c = canvas.current!;
    const r = c.getBoundingClientRect();
    return { x: ((e.clientX - r.left) / r.width) * c.width, y: ((e.clientY - r.top) / r.height) * c.height };
  };
  const save = async (blob: Blob | null) => {
    if (!blob) return;
    setBusy(true);
    const res = await pos.gateway.saveMySignature(blob);
    setBusy(false);
    if (!res.ok) return pos.showError(res.error);
    setSig(res.data);
    setDrawn(false);
    canvas.current?.getContext("2d")?.clearRect(0, 0, canvas.current.width, canvas.current.height);
    pos.showNotice("Signature saved. New letters use it.");
  };

  return (
    <section className={styles.panel}>
      <h2 className={styles.panelTitle}>My signature</h2>
      {error ? (
        <p className={styles.muted}>Signatures are for managers, finance and admins with an employee profile. {error}</p>
      ) : (
        <>
          <p className={styles.muted}>Payment letters you issue carry this signature. Changing it does not change letters already issued.</p>
          {sig?.imageUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={sig.imageUrl} alt="Your signature on file" style={{ height: 72, maxWidth: 300, objectFit: "contain", background: "#fff", borderRadius: 8, padding: 6 }} />
          ) : sig ? (
            <p className={styles.muted}>No signature on file yet.</p>
          ) : null}
          <canvas
            ref={canvas}
            width={600}
            height={180}
            aria-label="Sign here"
            style={{ width: "100%", maxWidth: 480, height: 144, background: "#fff", borderRadius: 8, border: "1px dashed #aaa", touchAction: "none", marginTop: 10, display: "block" }}
            onPointerDown={(e) => {
              const ctx = canvas.current?.getContext("2d");
              if (!ctx) return;
              drawing.current = true;
              canvas.current?.setPointerCapture(e.pointerId);
              const p = point(e);
              ctx.lineWidth = 3;
              ctx.lineCap = "round";
              ctx.strokeStyle = "#0b1d4a";
              ctx.beginPath();
              ctx.moveTo(p.x, p.y);
            }}
            onPointerMove={(e) => {
              if (!drawing.current) return;
              const ctx = canvas.current?.getContext("2d");
              const p = point(e);
              ctx?.lineTo(p.x, p.y);
              ctx?.stroke();
              setDrawn(true);
            }}
            onPointerUp={() => (drawing.current = false)}
          />
          <div className={styles.rowEnd}>
            <label className={`${styles.softButton} ${styles.inlineButton}`} style={{ cursor: "pointer" }}>
              <Upload size={16} aria-hidden /> Upload image
              <input type="file" accept="image/png,image/jpeg" hidden onChange={(e) => void save(e.target.files?.[0] ?? null)} />
            </label>
            <button
              type="button"
              className={`${styles.softButton} ${styles.inlineButton}`}
              disabled={!drawn}
              onClick={() => {
                canvas.current?.getContext("2d")?.clearRect(0, 0, canvas.current.width, canvas.current.height);
                setDrawn(false);
              }}
            >
              <Eraser size={16} aria-hidden /> Clear
            </button>
            <button type="button" className={styles.primaryButton} disabled={!drawn || busy || !pos.online} onClick={() => canvas.current?.toBlob((b) => void save(b), "image/png")}>
              {busy ? "Saving…" : "Save drawn signature"}
            </button>
          </div>
        </>
      )}
    </section>
  );
}

const PROFILE_FIELDS: [keyof BusinessProfile, string, boolean][] = [
  ["tradingName", "Trading name", true],
  ["legalName", "Legal name", true],
  ["domain", "Web domain", true],
  ["registrationNumber", "Registration number", false],
  ["addressLine1", "Address", false],
  ["addressLine2", "Address (line 2)", false],
  ["city", "City", false],
  ["country", "Country", false],
  ["phone", "Phone (+263…)", false],
  ["email", "Email", false],
];

/** Settings → Business details on documents (admin): the header of letters and other staff documents. */
export function BusinessProfileSetting({ pos }: { pos: PosStore }) {
  const [p, setP] = useState<BusinessProfile | null>(null);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    void pos.gateway.getBusinessProfile().then((res) => res.ok && setP(res.data));
  }, [pos.gateway]);
  if (!p) return null;
  const save = async () => {
    setBusy(true);
    const res = await pos.gateway.setBusinessProfile(p);
    setBusy(false);
    if (!res.ok) return pos.showError(/admin/i.test(res.error) ? "Only an admin can change the business details." : res.error);
    setP(res.data);
    pos.showNotice("Business details saved.");
  };
  return (
    <section className={styles.panel}>
      <h2 className={styles.panelTitle}>Business details on documents</h2>
      <p className={styles.muted}>Printed at the top of payment letters. Admins only.</p>
      <div className={styles.formGrid}>
        {PROFILE_FIELDS.map(([k, label, required]) => (
          <label key={k} className={styles.field}>
            <span className={styles.fieldLabel}>
              {label}
              {required ? " *" : ""}
            </span>
            <input className={styles.input} value={p[k] ?? ""} onChange={(e) => setP({ ...p, [k]: required ? e.target.value : e.target.value || null })} />
          </label>
        ))}
      </div>
      <div className={styles.rowEnd}>
        <button type="button" className={styles.primaryButton} disabled={busy || !pos.online || !p.tradingName.trim() || !p.legalName.trim() || !p.domain.trim()} onClick={() => void save()}>
          {busy ? "Saving…" : "Save details"}
        </button>
      </div>
    </section>
  );
}
