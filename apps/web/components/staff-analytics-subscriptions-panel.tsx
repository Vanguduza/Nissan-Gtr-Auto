"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import { useStaffAuth } from "@/components/staff-auth-context";
import {
  activateAiReportSubscription,
  createAiReportSubscription,
  deactivateAiReportSubscription,
  listAiReportSubscriptions,
  requireSession,
  splitRecipientField,
  updateAiReportSubscription,
  type AiDeliveryChannel,
  type AiReportCadence,
  type AiReportSubscriptionRow,
} from "@/lib/staff-analytics";
import { rolesAllow } from "@/lib/staff-auth";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; rows: AiReportSubscriptionRow[] };

const CADENCES: AiReportCadence[] = ["daily", "weekly", "monthly"];

function emptyForm() {
  return {
    cadence: "daily" as AiReportCadence,
    channelEmail: true,
    channelWhatsapp: false,
    emails: "",
    whatsapp: "",
    includeNarrative: true,
    timezone: "Africa/Harare",
    active: true,
  };
}

const SUB_MUTATE_ROLES = ["admin", "finance"] as const;

export function StaffAnalyticsSubscriptionsPanel() {
  const staff = useStaffAuth();
  const canMutate = rolesAllow(staff?.roles ?? [], [...SUB_MUTATE_ROLES]);
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState(emptyForm);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }
    const list = await listAiReportSubscriptions(client);
    if (!list.ok) {
      setBoot({ kind: "error", message: list.error });
      return;
    }
    setBoot({ kind: "ready", rows: list.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  function loadIntoForm(row: AiReportSubscriptionRow) {
    setEditingId(row.id);
    setForm({
      cadence: row.cadence,
      channelEmail: row.channels.includes("email"),
      channelWhatsapp: row.channels.includes("whatsapp"),
      emails: row.recipient_emails.join(", "),
      whatsapp: row.recipient_whatsapp_e164.join(", "),
      includeNarrative: row.include_narrative,
      timezone: row.timezone,
      active: row.active,
    });
    setMessage(`Editing ${row.id.slice(0, 8)}…`);
  }

  function resetForm() {
    setEditingId(null);
    setForm(emptyForm());
    setMessage(null);
  }

  function channelsFromForm(): AiDeliveryChannel[] {
    const ch: AiDeliveryChannel[] = [];
    if (form.channelEmail) ch.push("email");
    if (form.channelWhatsapp) ch.push("whatsapp");
    return ch;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!canMutate) return;
    const client = createWebClient();
    if (!client) return;

    const input = {
      cadence: form.cadence,
      channels: channelsFromForm(),
      recipientEmails: splitRecipientField(form.emails),
      recipientWhatsappE164: splitRecipientField(form.whatsapp),
      includeNarrative: form.includeNarrative,
      timezone: form.timezone,
      active: form.active,
    };

    setBusy(true);
    setMessage(null);
    const res = editingId
      ? await updateAiReportSubscription(client, editingId, input)
      : await createAiReportSubscription(client, input);
    setBusy(false);

    if (!res.ok) {
      setMessage(res.error);
      return;
    }

    setMessage(
      editingId
        ? `Updated subscription ${editingId.slice(0, 8)}…`
        : `Created subscription ${String(res.data).slice(0, 8)}…`,
    );
    resetForm();
    await refresh();
  }

  async function onDeactivate(id: string) {
    if (!canMutate) return;
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await deactivateAiReportSubscription(client, id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Deactivated ${id.slice(0, 8)}…`);
    if (editingId === id) resetForm();
    await refresh();
  }

  async function onActivate(id: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await activateAiReportSubscription(client, id);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Activated ${id.slice(0, 8)}…`);
    await refresh();
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading subscriptions…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with admin, finance, or sales staff.
      </p>
    );
  }
  if (boot.kind === "error") {
    return <p className={styles.lede}>{boot.message}</p>;
  }

  return (
    <div className={styles.form}>
      <p className={styles.muted}>
        Worker cron delivers due subs via email / WhatsApp. Interactive KPIs:{" "}
        <Link href="/staff/analytics">analytics</Link>.
      </p>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>
          {editingId ? "Update subscription" : "New subscription"}
        </legend>
        <form onSubmit={onSubmit}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Cadence
              <select
                value={form.cadence}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    cadence: e.target.value as AiReportCadence,
                  }))
                }
                disabled={busy}
              >
                {CADENCES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              Timezone
              <input
                value={form.timezone}
                onChange={(e) =>
                  setForm((f) => ({ ...f, timezone: e.target.value }))
                }
                disabled={busy}
                placeholder="Africa/Harare"
              />
            </label>
            <label className={styles.checkField}>
              <input
                type="checkbox"
                checked={form.channelEmail}
                onChange={(e) =>
                  setForm((f) => ({ ...f, channelEmail: e.target.checked }))
                }
                disabled={busy}
              />
              Channel · email
            </label>
            <label className={styles.checkField}>
              <input
                type="checkbox"
                checked={form.channelWhatsapp}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    channelWhatsapp: e.target.checked,
                  }))
                }
                disabled={busy}
              />
              Channel · WhatsApp
            </label>
            <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
              Recipient emails (comma-separated)
              <input
                value={form.emails}
                onChange={(e) =>
                  setForm((f) => ({ ...f, emails: e.target.value }))
                }
                disabled={busy}
                placeholder="ops@example.com, finance@example.com"
              />
            </label>
            <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
              WhatsApp E.164 (comma-separated)
              <input
                value={form.whatsapp}
                onChange={(e) =>
                  setForm((f) => ({ ...f, whatsapp: e.target.value }))
                }
                disabled={busy}
                placeholder="+263771234567"
              />
            </label>
            <label className={styles.checkField}>
              <input
                type="checkbox"
                checked={form.includeNarrative}
                onChange={(e) =>
                  setForm((f) => ({
                    ...f,
                    includeNarrative: e.target.checked,
                  }))
                }
                disabled={busy}
              />
              Include AI narrative (numeric-only if Gemini unavailable)
            </label>
            <label className={styles.checkField}>
              <input
                type="checkbox"
                checked={form.active}
                onChange={(e) =>
                  setForm((f) => ({ ...f, active: e.target.checked }))
                }
                disabled={busy}
              />
              Active
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy}>
              {editingId ? "Save changes" : "Create subscription"}
            </button>
            {editingId ? (
              <button
                type="button"
                className={styles.btnGhost}
                disabled={busy}
                onClick={resetForm}
              >
                Cancel edit
              </button>
            ) : null}
            {message ? <p className={styles.formStatus}>{message}</p> : null}
          </div>
        </form>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Subscriptions</legend>
        {boot.rows.length === 0 ? (
          <p className={styles.muted}>No subscriptions yet.</p>
        ) : (
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Cadence</th>
                <th>Channels</th>
                <th>Recipients</th>
                <th>Narrative</th>
                <th>Status</th>
                <th>Last run</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {boot.rows.map((row) => (
                <tr key={row.id}>
                  <td>{row.cadence}</td>
                  <td>{row.channels.join(", ")}</td>
                  <td>
                    {[
                      ...row.recipient_emails,
                      ...row.recipient_whatsapp_e164,
                    ].join("; ") || "—"}
                  </td>
                  <td>{row.include_narrative ? "yes" : "no"}</td>
                  <td>{row.active ? "active" : "inactive"}</td>
                  <td>
                    {row.last_run_at
                      ? new Date(row.last_run_at).toLocaleString()
                      : "—"}
                  </td>
                  <td>
                    <div className={styles.addrActions}>
                      <button
                        type="button"
                        className={styles.btnGhost}
                        disabled={busy}
                        onClick={() => loadIntoForm(row)}
                      >
                        Edit
                      </button>
                      {row.active ? (
                        <button
                          type="button"
                          className={styles.btnGhost}
                          disabled={busy}
                          onClick={() => void onDeactivate(row.id)}
                        >
                          Deactivate
                        </button>
                      ) : (
                        <button
                          type="button"
                          className={styles.btnGhost}
                          disabled={busy}
                          onClick={() => void onActivate(row.id)}
                        >
                          Activate
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </fieldset>
    </div>
  );
}
