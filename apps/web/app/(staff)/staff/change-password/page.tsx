"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import styles from "@/components/account.module.css";
import { createWebClient } from "@/lib/supabase";

/**
 * Forced password change gate (Batch 1 §2.5).
 * Clears profiles.must_change_password after successful update.
 */
export default function StaffChangePasswordPage() {
  const router = useRouter();
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (password.length < 12) {
      setMessage("Password must be at least 12 characters.");
      return;
    }
    if (password !== confirm) {
      setMessage("Passwords do not match.");
      return;
    }
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const { error } = await client.auth.updateUser({ password });
    if (error) {
      setBusy(false);
      setMessage(error.message);
      return;
    }
    const { error: clearErr } = await client.rpc("clear_must_change_password");
    setBusy(false);
    if (clearErr) {
      setMessage(clearErr.message);
      return;
    }
    setMessage("Password updated.");
    router.replace("/staff");
  }

  return (
    <div className={styles.form}>
      <h1 className={styles.title}>Change password</h1>
      <p className={styles.muted}>
        Your account requires a new password before continuing.
      </p>
      <form onSubmit={(e) => void onSubmit(e)}>
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>New password</legend>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Password
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                disabled={busy}
                autoComplete="new-password"
                minLength={12}
                required
              />
            </label>
            <label className={styles.field}>
              Confirm
              <input
                type="password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                disabled={busy}
                autoComplete="new-password"
                minLength={12}
                required
              />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy}>
              {busy ? "Saving…" : "Save password"}
            </button>
          </div>
        </fieldset>
      </form>
      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
