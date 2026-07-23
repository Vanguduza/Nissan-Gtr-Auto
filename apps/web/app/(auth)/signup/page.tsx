"use client";

import Image from "next/image";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { createWebClient } from "@/lib/supabase";
import styles from "../login/auth.module.css";

export default function SignupPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Add NEXT_PUBLIC_SUPABASE_URL and ANON_KEY to .env.local");
      setBusy(false);
      return;
    }
    const { error } = await client.auth.signUp({
      email,
      password,
      options: { data: { full_name: fullName } },
    });
    setBusy(false);
    setMessage(
      error
        ? error.message
        : "Check your email if confirmations are on — profile row is created by trigger.",
    );
  }

  return (
    <div className={styles.shell}>
      <Link href="/" className={styles.brand}>
        Nissan GTR Auto
      </Link>
      <form className={styles.form} onSubmit={onSubmit}>
        <h1 className={styles.title}>Create account</h1>
        <label className={styles.label}>
          Full name
          <input
            className={styles.input}
            value={fullName}
            onChange={(e) => setFullName(e.target.value)}
            autoComplete="name"
          />
        </label>
        <label className={styles.label}>
          Email
          <input
            className={styles.input}
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </label>
        <label className={styles.label}>
          Password
          <input
            className={styles.input}
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
          />
        </label>
        <button className={styles.submit} type="submit" disabled={busy}>
          {busy ? "Creating…" : "Sign up"}
        </button>
        {message ? <p className={styles.message}>{message}</p> : null}
        <p className={styles.alt}>
          Already registered? <Link href="/login">Sign in</Link>
        </p>
      </form>
    </div>
  );
}
