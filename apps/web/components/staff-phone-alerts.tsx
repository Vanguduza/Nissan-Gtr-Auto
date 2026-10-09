"use client";

import { useEffect, useId, useState } from "react";
import styles from "@/components/account.module.css";
import { getMyAlertSettings, setMyAlertSettings, type AlertSettings } from "@/lib/staff-alert-settings";
import { createWebClient } from "@/lib/supabase";

/**
 * "Alerts on your phone": each person chooses for themselves whether urgent approvals (waiting 10
 * minutes for them) and the 07:00 summary of yesterday reach their phone, by SMS or WhatsApp.
 */
export function StaffPhoneAlerts() {
  const [s, setS] = useState<AlertSettings | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ text: string; error: boolean } | null>(null);
  const phoneId = useId();

  useEffect(() => {
    const client = createWebClient();
    if (!client) return;
    void getMyAlertSettings(client).then((res) => {
      if (res.ok) setS(res.data);
    });
  }, []);

  if (!s) return null;

  async function save() {
    const client = createWebClient();
    if (!client || !s) return;
    setBusy(true);
    setMessage(null);
    const res = await setMyAlertSettings(client, s);
    setBusy(false);
    if (!res.ok) {
      setMessage({ text: res.error, error: true });
      return;
    }
    setS(res.data);
    const on = [res.data.urgentApprovals ? "urgent approvals" : null, res.data.morningSummary ? "the 07:00 summary" : null].filter(Boolean);
    setMessage({
      text: on.length
        ? `Saved. ${on.join(" and ")} will reach ${res.data.phoneE164} by ${res.data.channel === "whatsapp" ? "WhatsApp (SMS if WhatsApp cannot deliver)" : "SMS"}.`
        : "Saved. No alerts will be sent to your phone.",
      error: false,
    });
  }

  return (
    <details className={styles.form}>
      <summary className={styles.sectionTitle} style={{ cursor: "pointer" }}>
        Alerts on your phone
      </summary>
      <form
        className={styles.form}
        onSubmit={(e) => {
          e.preventDefault();
          void save();
        }}
      >
        <div className={styles.formActions}>
          <label className={styles.field} htmlFor={phoneId} style={{ flex: "1 1 220px" }}>
            Phone (international)
            <input
              id={phoneId}
              type="tel"
              inputMode="tel"
              autoComplete="tel"
              placeholder="+263771234567"
              value={s.phoneE164 ?? ""}
              onChange={(e) => setS({ ...s, phoneE164: e.target.value })}
              disabled={busy}
            />
          </label>
          <fieldset className={styles.formActions} style={{ border: 0, padding: 0, margin: 0 }}>
            <legend className={styles.muted}>Send by</legend>
            <label className={styles.checkField}>
              <input type="radio" name="alert-channel" checked={s.channel === "sms"} onChange={() => setS({ ...s, channel: "sms" })} disabled={busy} />
              SMS
            </label>
            <label className={styles.checkField}>
              <input type="radio" name="alert-channel" checked={s.channel === "whatsapp"} onChange={() => setS({ ...s, channel: "whatsapp" })} disabled={busy} />
              WhatsApp
            </label>
          </fieldset>
        </div>
        <label className={styles.checkField}>
          <input type="checkbox" checked={s.urgentApprovals} onChange={(e) => setS({ ...s, urgentApprovals: e.target.checked })} disabled={busy} />
          Text me when an urgent item (a driver waiting, a card payment to reconcile, a transfer for a waiting customer) has waited 10 minutes
          for me
        </label>
        {s.canGetSummary ? (
          <label className={styles.checkField}>
            <input type="checkbox" checked={s.morningSummary} onChange={(e) => setS({ ...s, morningSummary: e.target.checked })} disabled={busy} />
            Send me yesterday&apos;s figures at 07:00 (sales, unpaid, till differences, driver cash, failed deliveries, reorder)
          </label>
        ) : null}
        <div className={styles.formActions}>
          <button type="submit" className={styles.btn} disabled={busy}>
            {busy ? "Saving…" : "Save phone alerts"}
          </button>
        </div>
        {message ? (
          <p className={styles.lede} role={message.error ? "alert" : "status"}>
            {message.text}
          </p>
        ) : null}
      </form>
    </details>
  );
}
