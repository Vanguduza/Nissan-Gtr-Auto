"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import { createWebClient } from "@/lib/supabase";

type SupplierRow = {
  id: string;
  code: string;
  name: string;
  email: string | null;
  phone_e164: string | null;
  default_currency: string;
  is_preferred: boolean;
  is_active: boolean;
  relationship_notes: string | null;
  payment_terms: string | null;
  product_categories: string[] | null;
};

export function PreferredSuppliersPanel() {
  const [rows, setRows] = useState<SupplierRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [notes, setNotes] = useState("");
  const [terms, setTerms] = useState("");
  const [categories, setCategories] = useState("");

  const load = useCallback(async () => {
    const client = createWebClient();
    const { data, error } = await client
      .from("suppliers")
      .select(
        "id,code,name,email,phone_e164,default_currency,is_preferred,is_active,relationship_notes,payment_terms,product_categories",
      )
      .eq("is_preferred", true)
      .order("name");
    if (error) {
      setMessage(error.message);
      return;
    }
    setRows((data ?? []) as SupplierRow[]);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    const cats = categories
      .split(",")
      .map((s) => s.trim())
      .filter(Boolean);
    const { error } = await client.rpc("upsert_preferred_supplier", {
      p_code: code,
      p_name: name,
      p_email: email || null,
      p_phone_e164: phone || null,
      p_currency: "USD",
      p_notes: notes || null,
      p_address: null,
      p_tax_id: null,
      p_payment_terms: terms || null,
      p_categories: cats,
    });
    setBusy(false);
    if (error) {
      setMessage(error.message);
      return;
    }
    setCode("");
    setName("");
    setEmail("");
    setPhone("");
    setNotes("");
    setTerms("");
    setCategories("");
    setMessage("Supplier saved on preferred roster.");
    await load();
  }

  async function remove(id: string) {
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    const { error } = await client.rpc("deactivate_preferred_supplier", {
      p_supplier_id: id,
    });
    setBusy(false);
    if (error) {
      setMessage(error.message);
      return;
    }
    setMessage("Supplier removed from preferred roster.");
    await load();
  }

  return (
    <div className={styles.panel}>
      <h2 className={styles.title}>Preferred suppliers</h2>
      <p className={styles.lede}>
        Long-standing relationships for replenishment. POs are built manually
        against this roster — not from winning RFQ quotations. AI restock
        suggestions never auto-create orders.
      </p>
      {message ? <p className={styles.formStatus}>{message}</p> : null}

      <form onSubmit={onSubmit} className={styles.form}>
        <div className={styles.formGrid}>
          <label className={styles.field}>
            Code
            <input required value={code} onChange={(e) => setCode(e.target.value)} />
          </label>
          <label className={styles.field}>
            Name
            <input required value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          <label className={styles.field}>
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          </label>
          <label className={styles.field}>
            Phone (E.164)
            <input value={phone} onChange={(e) => setPhone(e.target.value)} />
          </label>
          <label className={styles.field}>
            Payment terms
            <input value={terms} onChange={(e) => setTerms(e.target.value)} />
          </label>
          <label className={styles.field}>
            Product categories (comma-separated)
            <input
              value={categories}
              onChange={(e) => setCategories(e.target.value)}
            />
          </label>
          <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
            Relationship notes
            <textarea
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={2}
            />
          </label>
        </div>
        <div className={styles.formActions}>
          <button type="submit" disabled={busy} className={styles.btn}>
            {busy ? "Saving…" : "Add / update supplier"}
          </button>
        </div>
      </form>

      <div className={styles.cardGrid} style={{ marginTop: "1.5rem" }}>
        {rows.map((s) => (
          <article key={s.id} className={styles.card}>
            <span className={styles.cardLabel}>
              {s.code} — {s.name}
            </span>
            <span className={styles.cardBlurb}>
              {s.email ?? "no email"} · {s.payment_terms ?? "terms n/a"}
              {(s.product_categories?.length ?? 0) > 0
                ? ` · ${s.product_categories!.join(", ")}`
                : ""}
            </span>
            <button
              type="button"
              className={styles.btnGhost}
              disabled={busy}
              onClick={() => void remove(s.id)}
            >
              Remove from roster
            </button>
          </article>
        ))}
        {rows.length === 0 ? (
          <p className={styles.lede}>No preferred suppliers yet.</p>
        ) : null}
      </div>
    </div>
  );
}
