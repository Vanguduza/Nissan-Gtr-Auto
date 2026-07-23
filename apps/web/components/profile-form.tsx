"use client";

import { FormEvent, useState } from "react";
import styles from "@/components/account.module.css";

type ProfileState = {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  company: string;
  preferredContact: "sms" | "email" | "whatsapp";
};

const initial: ProfileState = {
  firstName: "Tendai",
  lastName: "Moyo",
  email: "tendai@example.co.zw",
  phone: "+263 77 000 0000",
  company: "",
  preferredContact: "whatsapp",
};

export function ProfileForm() {
  const [form, setForm] = useState(initial);
  const [saved, setSaved] = useState(false);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    setSaved(true);
  }

  function set<K extends keyof ProfileState>(key: K, value: ProfileState[K]) {
    setSaved(false);
    setForm((f) => ({ ...f, [key]: value }));
  }

  return (
    <form className={styles.form} onSubmit={onSubmit} noValidate>
      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Personal information</legend>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            First name
            <input
              value={form.firstName}
              onChange={(e) => set("firstName", e.target.value)}
              autoComplete="given-name"
              required
            />
          </label>
          <label className={styles.field}>
            Last name
            <input
              value={form.lastName}
              onChange={(e) => set("lastName", e.target.value)}
              autoComplete="family-name"
              required
            />
          </label>
          <label className={styles.field}>
            Company (optional)
            <input
              value={form.company}
              onChange={(e) => set("company", e.target.value)}
              autoComplete="organization"
            />
          </label>
        </div>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Contact details</legend>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Email
            <input
              type="email"
              value={form.email}
              onChange={(e) => set("email", e.target.value)}
              autoComplete="email"
              required
            />
          </label>
          <label className={styles.field}>
            Mobile / WhatsApp
            <input
              type="tel"
              value={form.phone}
              onChange={(e) => set("phone", e.target.value)}
              autoComplete="tel"
              required
            />
          </label>
          <label className={styles.field}>
            Preferred contact
            <select
              value={form.preferredContact}
              onChange={(e) =>
                set(
                  "preferredContact",
                  e.target.value as ProfileState["preferredContact"],
                )
              }
            >
              <option value="whatsapp">WhatsApp</option>
              <option value="sms">SMS</option>
              <option value="email">Email</option>
            </select>
          </label>
        </div>
      </fieldset>

      <div className={styles.formActions}>
        <button type="submit" className={styles.btn}>
          Save details
        </button>
        {saved ? (
          <p className={styles.formStatus} role="status">
            Saved locally (demo) — binds to profile API in Phase 6+ auth.
          </p>
        ) : null}
      </div>
    </form>
  );
}
