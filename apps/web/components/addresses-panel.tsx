"use client";

import { FormEvent, useState } from "react";
import styles from "@/components/account.module.css";

type Address = {
  id: string;
  label: string;
  line1: string;
  line2: string;
  city: string;
  province: string;
  postal: string;
  country: string;
  isDefault: boolean;
};

const seed: Address[] = [
  {
    id: "home",
    label: "Home",
    line1: "12 Borrowdale Road",
    line2: "",
    city: "Harare",
    province: "Harare",
    postal: "",
    country: "Zimbabwe",
    isDefault: true,
  },
  {
    id: "workshop",
    label: "Workshop",
    line1: "Unit 4, Msasa Industrial",
    line2: "Mutare Road",
    city: "Harare",
    province: "Harare",
    postal: "",
    country: "Zimbabwe",
    isDefault: false,
  },
];

export function AddressesPanel() {
  const [addresses, setAddresses] = useState(seed);
  const [editing, setEditing] = useState<Address | null>(null);
  const [status, setStatus] = useState("");

  function startNew() {
    setEditing({
      id: `new-${Date.now()}`,
      label: "",
      line1: "",
      line2: "",
      city: "",
      province: "",
      postal: "",
      country: "Zimbabwe",
      isDefault: addresses.length === 0,
    });
    setStatus("");
  }

  function onSave(e: FormEvent) {
    e.preventDefault();
    if (!editing) return;
    setAddresses((list) => {
      const exists = list.some((a) => a.id === editing.id);
      const next = exists
        ? list.map((a) => (a.id === editing.id ? editing : a))
        : [...list, editing];
      if (editing.isDefault) {
        return next.map((a) => ({
          ...a,
          isDefault: a.id === editing.id,
        }));
      }
      return next;
    });
    setEditing(null);
    setStatus("Address saved (demo).");
  }

  function makeDefault(id: string) {
    setAddresses((list) =>
      list.map((a) => ({ ...a, isDefault: a.id === id })),
    );
    setStatus("Default delivery address updated (demo).");
  }

  function remove(id: string) {
    setAddresses((list) => list.filter((a) => a.id !== id));
    setStatus("Address removed (demo).");
  }

  return (
    <div className={styles.addrWrap}>
      <ul className={styles.list}>
        {addresses.map((a) => (
          <li key={a.id}>
            <strong>
              {a.label}
              {a.isDefault ? " · Default" : ""}
            </strong>
            <p className={styles.muted}>
              {[a.line1, a.line2, a.city, a.province, a.country]
                .filter(Boolean)
                .join(", ")}
            </p>
            <div className={styles.addrActions}>
              <button
                type="button"
                className={styles.btnGhost}
                onClick={() => {
                  setEditing(a);
                  setStatus("");
                }}
              >
                Edit
              </button>
              {!a.isDefault ? (
                <button
                  type="button"
                  className={styles.btnGhost}
                  onClick={() => makeDefault(a.id)}
                >
                  Set default
                </button>
              ) : null}
              <button
                type="button"
                className={styles.btnGhost}
                onClick={() => remove(a.id)}
              >
                Remove
              </button>
            </div>
          </li>
        ))}
      </ul>

      {!editing ? (
        <button type="button" className={styles.btn} onClick={startNew}>
          Add address
        </button>
      ) : (
        <form className={styles.form} onSubmit={onSave}>
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>
              {addresses.some((a) => a.id === editing.id)
                ? "Edit address"
                : "New address"}
            </legend>
            <div className={styles.formGrid}>
              <label className={styles.field}>
                Label
                <input
                  value={editing.label}
                  onChange={(e) =>
                    setEditing({ ...editing, label: e.target.value })
                  }
                  placeholder="Home, Workshop…"
                  required
                />
              </label>
              <label className={styles.field}>
                Street address
                <input
                  value={editing.line1}
                  onChange={(e) =>
                    setEditing({ ...editing, line1: e.target.value })
                  }
                  autoComplete="address-line1"
                  required
                />
              </label>
              <label className={styles.field}>
                Line 2
                <input
                  value={editing.line2}
                  onChange={(e) =>
                    setEditing({ ...editing, line2: e.target.value })
                  }
                  autoComplete="address-line2"
                />
              </label>
              <label className={styles.field}>
                City
                <input
                  value={editing.city}
                  onChange={(e) =>
                    setEditing({ ...editing, city: e.target.value })
                  }
                  autoComplete="address-level2"
                  required
                />
              </label>
              <label className={styles.field}>
                Province
                <input
                  value={editing.province}
                  onChange={(e) =>
                    setEditing({ ...editing, province: e.target.value })
                  }
                  autoComplete="address-level1"
                />
              </label>
              <label className={styles.field}>
                Postal code
                <input
                  value={editing.postal}
                  onChange={(e) =>
                    setEditing({ ...editing, postal: e.target.value })
                  }
                  autoComplete="postal-code"
                />
              </label>
              <label className={styles.field}>
                Country
                <input
                  value={editing.country}
                  onChange={(e) =>
                    setEditing({ ...editing, country: e.target.value })
                  }
                  autoComplete="country-name"
                  required
                />
              </label>
              <label className={styles.checkField}>
                <input
                  type="checkbox"
                  checked={editing.isDefault}
                  onChange={(e) =>
                    setEditing({ ...editing, isDefault: e.target.checked })
                  }
                />
                Default delivery address
              </label>
            </div>
          </fieldset>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn}>
              Save address
            </button>
            <button
              type="button"
              className={styles.btnGhost}
              onClick={() => setEditing(null)}
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {status ? (
        <p className={styles.formStatus} role="status">
          {status}
        </p>
      ) : null}
    </div>
  );
}
