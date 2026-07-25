"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import {
  loadOwnCustomer,
  loadOwnProfile,
  requireSession,
  updateOwnCustomerContact,
  updateOwnFullName,
} from "@/lib/customer-storefront";
import { createWebClient } from "@/lib/supabase";
import styles from "@/components/account.module.css";

type ProfileState = {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  company: string;
  preferredContact: "sms" | "email" | "whatsapp";
  customerId: string | null;
};

function splitName(full: string | null | undefined): {
  firstName: string;
  lastName: string;
} {
  const parts = (full ?? "").trim().split(/\s+/).filter(Boolean);
  if (!parts.length) return { firstName: "", lastName: "" };
  if (parts.length === 1) return { firstName: parts[0], lastName: "" };
  return { firstName: parts[0], lastName: parts.slice(1).join(" ") };
}

function preferredFromFlags(c: {
  sms_receipts: boolean;
  email_receipts: boolean;
  whatsapp_receipts: boolean;
}): ProfileState["preferredContact"] {
  if (c.whatsapp_receipts) return "whatsapp";
  if (c.sms_receipts) return "sms";
  if (c.email_receipts) return "email";
  return "whatsapp";
}

export function ProfileForm() {
  const [form, setForm] = useState<ProfileState | null>(null);
  const [auth, setAuth] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [status, setStatus] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [userId, setUserId] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setLoadError("Supabase is not configured on this environment.");
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setAuth(false);
      return;
    }
    setAuth(true);
    setUserId(session.data.userId);

    const [profile, customer] = await Promise.all([
      loadOwnProfile(client, session.data.userId),
      loadOwnCustomer(client),
    ]);
    if (!profile.ok) {
      setLoadError(profile.error);
      return;
    }
    if (!customer.ok) {
      setLoadError(customer.error);
      return;
    }

    const names = splitName(profile.data?.full_name);
    const c = customer.data;
    setForm({
      firstName: names.firstName,
      lastName: names.lastName,
      email: c?.email ?? "",
      phone: c?.phone_e164 ?? c?.whatsapp_e164 ?? "",
      company: c?.display_name ?? "",
      preferredContact: c
        ? preferredFromFlags(c)
        : "whatsapp",
      customerId: c?.id ?? null,
    });
    setLoadError(null);
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  function set<K extends keyof ProfileState>(key: K, value: ProfileState[K]) {
    setStatus(null);
    setForm((f) => (f ? { ...f, [key]: value } : f));
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!form || !userId) return;
    setBusy(true);
    setStatus(null);
    const client = createWebClient();
    if (!client) {
      setStatus("Supabase is not configured.");
      setBusy(false);
      return;
    }

    const fullName = [form.firstName, form.lastName]
      .map((s) => s.trim())
      .filter(Boolean)
      .join(" ");

    const nameResult = await updateOwnFullName(client, userId, fullName);
    if (!nameResult.ok) {
      setStatus(nameResult.error);
      setBusy(false);
      return;
    }

    const notes: string[] = ["Name saved to profiles."];
    if (form.customerId) {
      const pref = form.preferredContact;
      const contact = await updateOwnCustomerContact(client, form.customerId, {
        display_name: form.company.trim() || fullName || "Customer",
        email: form.email.trim() || null,
        phone_e164: form.phone.trim() || null,
        whatsapp_e164: form.phone.trim() || null,
        sms_receipts: pref === "sms",
        email_receipts: pref === "email",
        whatsapp_receipts: pref === "whatsapp",
      });
      if (!contact.ok) {
        notes.push(`Contact / receipt prefs not saved: ${contact.error}`);
      } else {
        notes.push("Customer contact + receipt prefs updated.");
      }
    } else {
      notes.push(
        "No linked customers row — contact fields are display-only until a customer profile is linked.",
      );
    }

    setBusy(false);
    setStatus(notes.join(" "));
  }

  if (!auth) {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> to edit personal details.
      </p>
    );
  }

  if (loadError) {
    return (
      <p className={styles.lede} role="alert">
        {loadError}
      </p>
    );
  }

  if (!form) {
    return <p className={styles.muted}>Loading profile…</p>;
  }

  return (
    <form className={styles.form} onSubmit={(e) => void onSubmit(e)} noValidate>
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
              disabled={busy}
            />
          </label>
          <label className={styles.field}>
            Last name
            <input
              value={form.lastName}
              onChange={(e) => set("lastName", e.target.value)}
              autoComplete="family-name"
              disabled={busy}
            />
          </label>
          <label className={styles.field}>
            Company / display name
            <input
              value={form.company}
              onChange={(e) => set("company", e.target.value)}
              autoComplete="organization"
              disabled={busy}
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
              disabled={busy}
            />
          </label>
          <label className={styles.field}>
            Mobile / WhatsApp
            <input
              type="tel"
              value={form.phone}
              onChange={(e) => set("phone", e.target.value)}
              autoComplete="tel"
              disabled={busy}
            />
          </label>
          <label className={styles.field}>
            Preferred receipt channel
            <select
              value={form.preferredContact}
              onChange={(e) =>
                set(
                  "preferredContact",
                  e.target.value as ProfileState["preferredContact"],
                )
              }
              disabled={busy}
            >
              <option value="whatsapp">WhatsApp</option>
              <option value="sms">SMS</option>
              <option value="email">Email</option>
            </select>
          </label>
        </div>
      </fieldset>

      <div className={styles.formActions}>
        <button type="submit" className={styles.btn} disabled={busy}>
          {busy ? "Saving…" : "Save details"}
        </button>
        {status ? (
          <p className={styles.formStatus} role="status">
            {status}
          </p>
        ) : null}
      </div>
    </form>
  );
}
