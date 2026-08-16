"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import styles from "@/components/account.module.css";
import { useStaffAuth } from "@/components/staff-auth-context";
import { clearStaffIdleLockStorage } from "@/lib/staff-idle-lock-state";
import {
  downloadMyBusinessCard,
  downloadMyIdCard,
  downloadMyPayslip,
  getMyStaffPhotoPreviewUrl,
  getMyStaffProfile,
  listMyPayslipHistory,
  updateMyStaffProfile,
  uploadMyStaffPhoto,
  type StaffMyProfile,
  type StaffPayslipHistoryRow,
} from "@/lib/staff-account";
import { requireSession } from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";

function money(n: number, currency: string) {
  return `${Number(n).toFixed(2)} ${currency}`;
}

export function StaffAccountPanel() {
  const ctx = useStaffAuth();
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [profile, setProfile] = useState<StaffMyProfile | null>(null);
  const [history, setHistory] = useState<StaffPayslipHistoryRow[]>([]);
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [address, setAddress] = useState("");
  const [authEmail, setAuthEmail] = useState<string | null>(null);
  const [photoPreviewUrl, setPhotoPreviewUrl] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setMessage("Sign in required.");
      return;
    }
    const auth = await client.auth.getSession();
    const sessionEmail = auth.data.session?.user.email ?? null;
    setAuthEmail(sessionEmail);
    const [prof, hist] = await Promise.all([
      getMyStaffProfile(client),
      listMyPayslipHistory(client),
    ]);
    if (!prof.ok) {
      setMessage(prof.error);
      return;
    }
    setProfile(prof.data);
    setEmail(prof.data.email ?? sessionEmail ?? "");
    setPhone(prof.data.phone_e164 ?? "");
    setAddress(prof.data.address ?? "");
    const preview = await getMyStaffPhotoPreviewUrl(
      client,
      prof.data.photo_storage_path,
    );
    setPhotoPreviewUrl(preview);
    if (hist.ok) setHistory(hist.data);
    else setMessage(hist.error);
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function onSaveIdentity(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !profile?.has_employee) return;
    setBusy(true);
    setMessage(null);
    const emailChanged =
      email.trim().toLowerCase() !==
      (profile.email ?? authEmail ?? "").trim().toLowerCase();
    const res = await updateMyStaffProfile(client, {
      phoneE164: phone,
      address,
      email,
      syncAuthEmail: emailChanged,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setProfile(res.data);
    setMessage(
      emailChanged
        ? "Profile saved. If email changed, confirm via the link Supabase sends."
        : "Profile saved.",
    );
    await refresh();
  }

  async function onPhotoSelected(file: File | null) {
    if (!file || !profile?.employee_id) return;
    if (!file.type.startsWith("image/")) {
      setMessage("Choose a JPEG, PNG, or WebP image.");
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setMessage("Photo must be 5 MB or smaller.");
      return;
    }
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await uploadMyStaffPhoto(client, {
      employeeId: profile.employee_id,
      file,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setProfile(res.data);
    setMessage("Photo uploaded.");
    await refresh();
  }

  async function onDownloadPayslip(row: StaffPayslipHistoryRow) {
    const client = createWebClient();
    if (!client || !profile) return;
    const session = await client.auth.getSession();
    const token = session.data.session?.access_token;
    if (!token) {
      setMessage("Sign in required for payslip PDF.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await downloadMyPayslip(client, token, row, profile);
    setBusy(false);
    setMessage(
      res.ok
        ? `Downloaded payslip ${row.period_start} → ${row.period_end} (${row.currency}, gross − manual only).`
        : res.error,
    );
    if (res.ok) await refresh();
  }

  async function onDownloadIdCard() {
    if (!profile?.has_employee) return;
    const client = createWebClient();
    if (!client) return;
    const session = await client.auth.getSession();
    const token = session.data.session?.access_token;
    if (!token) {
      setMessage("Sign in required for ID card PDF.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await downloadMyIdCard(token, profile);
    setBusy(false);
    setMessage(res.ok ? "ID card PDF downloaded (CR80)." : res.error);
  }

  async function onDownloadBusinessCard() {
    if (!profile?.has_employee) return;
    const client = createWebClient();
    if (!client) return;
    const session = await client.auth.getSession();
    const token = session.data.session?.access_token;
    if (!token) {
      setMessage("Sign in required for business card PDF.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const res = await downloadMyBusinessCard(token, profile);
    setBusy(false);
    setMessage(
      res.ok ? "Business card PDF downloaded (90×50 mm)." : res.error,
    );
  }

  async function onSignOut() {
    setBusy(true);
    clearStaffIdleLockStorage();
    const client = createWebClient();
    if (client) await client.auth.signOut();
    setBusy(false);
    router.replace("/login");
  }

  const moduleChips =
    (profile?.module_access?.length
      ? profile.module_access
      : ctx?.moduleAccess) ?? [];

  return (
    <div className={styles.pageBody}>
      <section className={styles.section} aria-labelledby="staff-identity">
        <h2 id="staff-identity" className={styles.sectionTitle}>
          Identity
        </h2>
        {!profile?.has_employee ? (
          <p className={styles.muted}>
            No employee record is linked to this login. Contact HR to complete
            onboarding. You can still change password and sign out below.
          </p>
        ) : (
          <>
            <dl className={styles.metaList}>
              <div>
                <dt>Employee #</dt>
                <dd>{profile.employee_code}</dd>
              </div>
              <div>
                <dt>Name</dt>
                <dd>{profile.full_name}</dd>
              </div>
              <div>
                <dt>Role / grade</dt>
                <dd>
                  {[profile.role_title, profile.grade_code, profile.grade_title]
                    .filter(Boolean)
                    .join(" · ") || "—"}
                </dd>
              </div>
              <div>
                <dt>Photo</dt>
                <dd>
                  <div className={styles.photoUpload}>
                    {photoPreviewUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        src={photoPreviewUrl}
                        alt="Your staff photo"
                        className={styles.photoPreview}
                      />
                    ) : (
                      <span className={styles.photoPlaceholder}>
                        No photo yet
                      </span>
                    )}
                    <label className={styles.btnSecondary}>
                      {busy ? "Uploading…" : "Upload photo"}
                      <input
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        disabled={busy}
                        className={styles.fileInputHidden}
                        onChange={(e) => {
                          const f = e.target.files?.[0] ?? null;
                          e.target.value = "";
                          void onPhotoSelected(f);
                        }}
                      />
                    </label>
                    <p className={styles.muted}>
                      JPEG / PNG / WebP · max 5 MB · file picker only (no
                      browser camera/QR).
                    </p>
                  </div>
                </dd>
              </div>
            </dl>
            <form onSubmit={(e) => void onSaveIdentity(e)}>
              <fieldset className={styles.fieldset} disabled={busy}>
                <legend className={styles.legend}>Update contact details</legend>
                <div className={styles.formGrid}>
                  <label className={styles.field}>
                    Email
                    <input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      autoComplete="email"
                    />
                  </label>
                  <label className={styles.field}>
                    Phone (E.164)
                    <input
                      type="tel"
                      value={phone}
                      onChange={(e) => setPhone(e.target.value)}
                      placeholder="+263…"
                      autoComplete="tel"
                    />
                  </label>
                  <label className={styles.field}>
                    Address
                    <textarea
                      value={address}
                      onChange={(e) => setAddress(e.target.value)}
                      rows={3}
                    />
                  </label>
                </div>
                <div className={styles.formActions}>
                  <button type="submit" className={styles.btn} disabled={busy}>
                    {busy ? "Saving…" : "Save details"}
                  </button>
                  <button
                    type="button"
                    className={styles.btnSecondary}
                    disabled={busy}
                    onClick={() => void onDownloadIdCard()}
                  >
                    Download ID card
                  </button>
                  <button
                    type="button"
                    className={styles.btnSecondary}
                    disabled={busy}
                    onClick={() => void onDownloadBusinessCard()}
                  >
                    Download business card
                  </button>
                </div>
              </fieldset>
            </form>
          </>
        )}
      </section>

      <section className={styles.section} aria-labelledby="staff-access">
        <h2 id="staff-access" className={styles.sectionTitle}>
          Module access
        </h2>
        <p className={styles.muted}>
          Read-only organogram modules for your role (not editable here).
        </p>
        <div className={styles.chipRow}>
          {moduleChips.length === 0 ? (
            <span className={styles.chipMuted}>
              None listed · role nav applies
            </span>
          ) : (
            moduleChips.map((m) => (
              <span key={m} className={styles.chip}>
                {m}
              </span>
            ))
          )}
        </div>
        {ctx?.roles?.length ? (
          <p className={styles.muted}>Staff roles: {ctx.roles.join(", ")}</p>
        ) : null}
      </section>

      <section className={styles.section} aria-labelledby="staff-payslips">
        <h2 id="staff-payslips" className={styles.sectionTitle}>
          Payslip history
        </h2>
        <p className={styles.muted}>
          All of your submitted payroll periods (gross − manual deductions only;
          USD | ZIG). Not limited to the latest or funded-only lines.
        </p>
        {history.length === 0 ? (
          <p className={styles.muted}>No payslips yet.</p>
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Period</th>
                  <th>Run</th>
                  <th>Gross</th>
                  <th>Net</th>
                  <th>Status</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {history.map((row) => (
                  <tr key={row.payroll_line_id}>
                    <td>
                      {row.period_start} → {row.period_end}
                    </td>
                    <td>
                      {row.document_number ?? row.payroll_run_id.slice(0, 8)}
                    </td>
                    <td>{money(row.gross_amount, row.currency)}</td>
                    <td>{money(row.net_amount, row.currency)}</td>
                    <td>
                      {row.funded ? "Funded" : "Unfunded"} · {row.run_status}
                    </td>
                    <td>
                      <button
                        type="button"
                        className={styles.btnSecondary}
                        disabled={busy}
                        onClick={() => void onDownloadPayslip(row)}
                      >
                        Download
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      <section className={styles.section} aria-labelledby="staff-security">
        <h2 id="staff-security" className={styles.sectionTitle}>
          Security
        </h2>
        <div className={styles.formActions}>
          <Link href="/staff/change-password" className={styles.btnSecondary}>
            Change password
          </Link>
          <button
            type="button"
            className={styles.btn}
            disabled={busy}
            onClick={() => void onSignOut()}
          >
            {busy ? "Signing out…" : "Sign out"}
          </button>
        </div>
      </section>

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
