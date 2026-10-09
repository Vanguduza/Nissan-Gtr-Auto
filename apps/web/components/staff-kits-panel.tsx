"use client";

import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  addStaffKitComponent,
  createStaffKit,
  listChassisOptions,
  listStaffKits,
  removeStaffKitComponent,
  searchStockItems,
  updateStaffKit,
  type ChassisOption,
  type StaffKitRow,
  type StockItemOption,
} from "@/lib/staff-kits";
import { createWebClient } from "@/lib/supabase";

type Status =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ready"; rows: StaffKitRow[] };

type CompSlot = {
  key: string;
  query: string;
  hits: StockItemOption[];
  selected: StockItemOption | null;
  qty: string;
};

function emptySlot(): CompSlot {
  return {
    key: crypto.randomUUID(),
    query: "",
    hits: [],
    selected: null,
    qty: "1",
  };
}

export function StaffKitsPanel() {
  const [status, setStatus] = useState<Status>({ kind: "loading" });
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [chassisOptions, setChassisOptions] = useState<ChassisOption[]>([]);

  // Create form
  const [title, setTitle] = useState("");
  const [oem, setOem] = useState("");
  const [chassis, setChassis] = useState("");
  const [slots, setSlots] = useState<CompSlot[]>([emptySlot(), emptySlot()]);

  // Edit form
  const [editTitle, setEditTitle] = useState("");
  const [editActive, setEditActive] = useState(true);
  const [addQuery, setAddQuery] = useState("");
  const [addHits, setAddHits] = useState<StockItemOption[]>([]);

  const selected =
    status.kind === "ready"
      ? status.rows.find((r) => r.kitId === selectedId) ?? null
      : null;

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setStatus({
        kind: "error",
        message: "Supabase is not configured on this environment.",
      });
      return;
    }
    const [kitsRes, chassisRes] = await Promise.all([
      listStaffKits(client),
      listChassisOptions(client),
    ]);
    if (!kitsRes.ok) {
      setStatus({ kind: "error", message: kitsRes.error });
      return;
    }
    if (chassisRes.ok) setChassisOptions(chassisRes.data);
    setStatus({ kind: "ready", rows: kitsRes.data });
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!selected) return;
    setEditTitle(selected.title);
    setEditActive(selected.isActive);
    setAddQuery("");
    setAddHits([]);
  }, [selected]);

  const slotQueriesKey = slots.map((s) => `${s.query}\t${s.selected?.id ?? ""}`).join("\0");

  useEffect(() => {
    const snapshot = slots;
    const timers: number[] = [];
    snapshot.forEach((slot, idx) => {
      if (slot.selected || slot.query.trim().length < 2) return;
      const q = slot.query.trim();
      const t = window.setTimeout(() => {
        void (async () => {
          const client = createWebClient();
          if (!client) return;
          const res = await searchStockItems(client, q);
          if (!res.ok) return;
          setSlots((prev) => {
            const cur = prev[idx];
            if (!cur || cur.selected || cur.query.trim() !== q) return prev;
            return prev.map((s, j) =>
              j === idx ? { ...s, hits: res.data } : s,
            );
          });
        })();
      }, 250);
      timers.push(t);
    });
    return () => timers.forEach((t) => window.clearTimeout(t));
    // slotQueriesKey captures query + selection; omit `slots` to avoid hit-update loops.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [slotQueriesKey]);

  useEffect(() => {
    const q = addQuery.trim();
    if (q.length < 2) {
      setAddHits([]);
      return;
    }
    const t = window.setTimeout(() => {
      void (async () => {
        const client = createWebClient();
        if (!client) return;
        const res = await searchStockItems(client, q);
        if (res.ok) setAddHits(res.data);
      })();
    }, 250);
    return () => window.clearTimeout(t);
  }, [addQuery]);

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    const resolved = slots
      .map((s) => s.selected)
      .filter((s): s is StockItemOption => Boolean(s));
    if (!title.trim() || !oem.trim()) {
      setMessage("Title and kit OEM are required.");
      return;
    }
    if (resolved.length < 2) {
      setMessage("Select at least two catalog parts as components.");
      return;
    }
    const ids = resolved.map((r) => r.id);
    if (new Set(ids).size !== ids.length) {
      setMessage("Components must be unique.");
      return;
    }

    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const qtys = slots
      .filter((s) => s.selected)
      .map((s) => {
        const n = Number(s.qty);
        return Number.isFinite(n) && n > 0 ? n : 1;
      });
    const res = await createStaffKit(client, {
      oem,
      title,
      componentItemIds: ids,
      chassisCode: chassis || null,
      qtys,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(`Kit created (${res.data.slice(0, 8)}…).`);
    setTitle("");
    setOem("");
    setChassis("");
    setSlots([emptySlot(), emptySlot()]);
    setSelectedId(res.data);
    await refresh();
  }

  async function onSaveEdit(e: FormEvent) {
    e.preventDefault();
    if (!selected) return;
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const res = await updateStaffKit(client, {
      kitId: selected.kitId,
      title: editTitle,
      isActive: editActive,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Kit updated.");
    await refresh();
  }

  async function onAddComponent(item: StockItemOption) {
    if (!selected) return;
    if (!item.base_uom_id) {
      setMessage("Selected part is missing base UOM.");
      return;
    }
    if (selected.components.some((c) => c.componentItemId === item.id)) {
      setMessage("That part is already a component.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const res = await addStaffKitComponent(client, {
      kitId: selected.kitId,
      componentItemId: item.id,
      qty: 1,
      uomId: item.base_uom_id,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setAddQuery("");
    setAddHits([]);
    setMessage("Component added.");
    await refresh();
  }

  async function onRemoveComponent(componentItemId: string) {
    if (!selected) return;
    if (selected.components.length <= 2) {
      setMessage("Kits must keep at least two components.");
      return;
    }
    setBusy(true);
    setMessage(null);
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      setBusy(false);
      return;
    }
    const res = await removeStaffKitComponent(client, {
      kitId: selected.kitId,
      componentItemId,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Component removed.");
    await refresh();
  }

  if (status.kind === "loading") {
    return <p className={styles.muted}>Loading kits…</p>;
  }
  if (status.kind === "error") {
    return <p className={styles.formStatus}>{status.message}</p>;
  }

  return (
    <div className={styles.pageBody}>
      <p className={styles.lede}>
        Create sellable kits (manual OEM, explode BOM). Pick at least two catalog
        parts; optional chassis writes fitment for the kit OEM.
      </p>
      {message ? <p className={styles.formStatus}>{message}</p> : null}

      <form className={styles.form} onSubmit={onCreate}>
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Create kit</legend>
          <label className={styles.field}>
            Title
            <input
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Brake pad + rotor service kit"
              required
            />
          </label>
          <label className={styles.field}>
            Kit OEM / SKU
            <input
              value={oem}
              onChange={(e) => setOem(e.target.value)}
              placeholder="KIT-…"
              required
            />
          </label>
          <label className={styles.field}>
            Chassis (optional)
            <select
              value={chassis}
              onChange={(e) => setChassis(e.target.value)}
            >
              <option value="">— none —</option>
              {chassisOptions.map((c) => (
                <option key={c.chassisCode} value={c.chassisCode}>
                  {c.label}
                </option>
              ))}
            </select>
          </label>

          <p className={styles.muted}>
            Components (qty defaults to 1 EA). Sell mode is explode.
          </p>
          {slots.map((slot, idx) => (
            <div key={slot.key} className={styles.field}>
              <label>
                Part {idx + 1}
                <input
                  value={
                    slot.selected
                      ? `${slot.selected.oem_part_number} — ${slot.selected.description ?? ""}`
                      : slot.query
                  }
                  onChange={(e) => {
                    const q = e.target.value;
                    setSlots((prev) =>
                      prev.map((s, j) =>
                        j === idx
                          ? { ...s, query: q, selected: null, hits: [] }
                          : s,
                      ),
                    );
                  }}
                  placeholder="Search OEM / description (≥2 chars)"
                />
              </label>
              {slot.selected ? (
                <div className={styles.formActions}>
                  <label className={styles.field}>
                    Qty
                    <input
                      type="number"
                      min={0.001}
                      step="any"
                      value={slot.qty}
                      onChange={(e) =>
                        setSlots((prev) =>
                          prev.map((s, j) =>
                            j === idx ? { ...s, qty: e.target.value } : s,
                          ),
                        )
                      }
                    />
                  </label>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={() =>
                      setSlots((prev) =>
                        prev.map((s, j) =>
                          j === idx
                            ? { ...s, selected: null, query: "", hits: [] }
                            : s,
                        ),
                      )
                    }
                  >
                    Clear
                  </button>
                </div>
              ) : null}
              {!slot.selected && slot.hits.length > 0 ? (
                <ul className={styles.list}>
                  {slot.hits.map((h) => (
                    <li key={h.id}>
                      <button
                        type="button"
                        className={styles.btnGhost}
                        onClick={() =>
                          setSlots((prev) =>
                            prev.map((s, j) =>
                              j === idx
                                ? {
                                    ...s,
                                    selected: h,
                                    query: h.oem_part_number,
                                    hits: [],
                                  }
                                : s,
                            ),
                          )
                        }
                      >
                        <code>{h.oem_part_number}</code>{" "}
                        {h.description?.trim() || ""}
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
            </div>
          ))}

          <div className={styles.formActions}>
            <button
              type="button"
              className={styles.btnGhost}
              onClick={() => setSlots((prev) => [...prev, emptySlot()])}
            >
              Add another part
            </button>
            <button
              type="submit"
              className={styles.btn}
              disabled={busy || slots.filter((s) => s.selected).length < 2}
            >
              {busy ? "Saving…" : "Create kit"}
            </button>
          </div>
        </fieldset>
      </form>

      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>OEM</th>
              <th>Title</th>
              <th>Mode</th>
              <th>Active</th>
              <th>Parts</th>
              <th>Chassis</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {status.rows.length === 0 ? (
              <tr>
                <td colSpan={7} className={styles.muted}>
                  No kits yet.
                </td>
              </tr>
            ) : (
              status.rows.map((r) => (
                <tr key={r.kitId}>
                  <td>
                    <code>{r.oem}</code>
                  </td>
                  <td>{r.title}</td>
                  <td>{r.sellMode}</td>
                  <td>{r.isActive ? "yes" : "no"}</td>
                  <td>{r.components.length}</td>
                  <td>
                    {r.chassisCodes.length
                      ? r.chassisCodes.join(", ")
                      : "—"}
                  </td>
                  <td>
                    <button
                      type="button"
                      className={styles.btnGhost}
                      onClick={() => setSelectedId(r.kitId)}
                    >
                      Edit
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {selected ? (
        <form className={styles.form} onSubmit={onSaveEdit}>
          <fieldset className={styles.fieldset}>
            <legend className={styles.legend}>
              Edit <code>{selected.oem}</code>
            </legend>
            <label className={styles.field}>
              Title
              <input
                value={editTitle}
                onChange={(e) => setEditTitle(e.target.value)}
                required
              />
            </label>
            <label className={styles.field}>
              <span>
                <input
                  type="checkbox"
                  checked={editActive}
                  onChange={(e) => setEditActive(e.target.checked)}
                />{" "}
                Active
              </span>
            </label>

            <p className={styles.muted}>
              Components ({selected.components.length}) — sell mode{" "}
              {selected.sellMode}
              {selected.chassisCodes.length
                ? `; chassis ${selected.chassisCodes.join(", ")}`
                : ""}
            </p>
            <ul className={styles.list}>
              {selected.components.map((c) => (
                <li key={c.componentItemId}>
                  <code>{c.oem}</code> × {c.qty} — {c.name}{" "}
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy || selected.components.length <= 2}
                    onClick={() => void onRemoveComponent(c.componentItemId)}
                  >
                    Remove
                  </button>
                </li>
              ))}
            </ul>

            <label className={styles.field}>
              Add component
              <input
                value={addQuery}
                onChange={(e) => setAddQuery(e.target.value)}
                placeholder="Search OEM / description"
              />
            </label>
            {addHits.length > 0 ? (
              <ul className={styles.list}>
                {addHits.map((h) => (
                  <li key={h.id}>
                    <button
                      type="button"
                      className={styles.btnGhost}
                      disabled={busy}
                      onClick={() => void onAddComponent(h)}
                    >
                      <code>{h.oem_part_number}</code>{" "}
                      {h.description?.trim() || ""}
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}

            <div className={styles.formActions}>
              <button type="submit" className={styles.btn} disabled={busy}>
                {busy ? "Saving…" : "Save kit"}
              </button>
              <button
                type="button"
                className={styles.btnGhost}
                onClick={() => setSelectedId(null)}
              >
                Close
              </button>
            </div>
          </fieldset>
        </form>
      ) : null}
    </div>
  );
}
