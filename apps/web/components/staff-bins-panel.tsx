"use client";

import Link from "next/link";
import { FormEvent, useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import {
  createWarehouseBin,
  deactivateWarehouseBin,
  getPickPathHints,
  listWarehouseBins,
  listWarehouses,
  requireSession,
  searchStockItems,
  setStockLevelBin,
  updateWarehouseBin,
  type PickPathHint,
  type StockItemOption,
  type WarehouseBinRow,
  type WarehouseOption,
} from "@/lib/staff-bins";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; warehouses: WarehouseOption[] };

export function StaffBinsPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [warehouseId, setWarehouseId] = useState("");
  const [bins, setBins] = useState<WarehouseBinRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [pickPath, setPickPath] = useState("100");
  const [aisle, setAisle] = useState("");
  const [rack, setRack] = useState("");
  const [shelf, setShelf] = useState("");

  const [itemQuery, setItemQuery] = useState("");
  const [hits, setHits] = useState<StockItemOption[]>([]);
  const [selectedItem, setSelectedItem] = useState<StockItemOption | null>(null);
  const [assignBinId, setAssignBinId] = useState("");

  const [hintItems, setHintItems] = useState<StockItemOption[]>([]);
  const [hintQuery, setHintQuery] = useState("");
  const [hintHits, setHintHits] = useState<StockItemOption[]>([]);
  const [hints, setHints] = useState<PickPathHint[]>([]);
  const [hintBusy, setHintBusy] = useState(false);

  const loadBins = useCallback(async (whId: string) => {
    const client = createWebClient();
    if (!client || !whId) {
      setBins([]);
      return;
    }
    const res = await listWarehouseBins(client, whId);
    if (!res.ok) {
      setMessage(res.error);
      setBins([]);
      return;
    }
    setBins(res.data);
  }, []);

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
    const wh = await listWarehouses(client, { includeQuarantine: true });
    if (!wh.ok) {
      setBoot({ kind: "error", message: wh.error });
      return;
    }
    setBoot({ kind: "ready", warehouses: wh.data });
    const first = wh.data[0]?.id || "";
    setWarehouseId((prev) => prev || first);
    await loadBins(first || warehouseId);
  }, [loadBins, warehouseId]);

  useEffect(() => {
    void refresh();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- mount once
  }, []);

  useEffect(() => {
    if (boot.kind !== "ready" || !warehouseId) return;
    void loadBins(warehouseId);
  }, [boot.kind, warehouseId, loadBins]);

  useEffect(() => {
    if (boot.kind !== "ready") return;
    const q = itemQuery.trim();
    if (q.length < 2) {
      setHits([]);
      return;
    }
    const t = window.setTimeout(() => {
      void (async () => {
        const client = createWebClient();
        if (!client) return;
        const res = await searchStockItems(client, q);
        if (!res.ok) {
          setMessage(res.error);
          setHits([]);
          return;
        }
        setHits(res.data);
      })();
    }, 250);
    return () => window.clearTimeout(t);
  }, [boot.kind, itemQuery]);

  useEffect(() => {
    if (boot.kind !== "ready") return;
    const q = hintQuery.trim();
    if (q.length < 2) {
      setHintHits([]);
      return;
    }
    const t = window.setTimeout(() => {
      void (async () => {
        const client = createWebClient();
        if (!client) return;
        const res = await searchStockItems(client, q);
        if (!res.ok) {
          setMessage(res.error);
          setHintHits([]);
          return;
        }
        setHintHits(res.data);
      })();
    }, 250);
    return () => window.clearTimeout(t);
  }, [boot.kind, hintQuery]);

  async function onLoadPickHints(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !warehouseId) return;
    setHintBusy(true);
    setMessage(null);
    const res = await getPickPathHints(client, {
      warehouseId,
      stockItemIds: hintItems.length
        ? hintItems.map((i) => i.id)
        : undefined,
    });
    setHintBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setHints(res.data);
    setMessage(
      res.data.length
        ? `Pick path · ${res.data.length} preferred bin hint(s)`
        : "No pick-path hints for this selection (assign preferred bins first).",
    );
  }

  async function onCreate(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !warehouseId) return;
    setBusy(true);
    setMessage(null);
    const res = await createWarehouseBin(client, {
      warehouseId,
      code,
      name,
      pickPathSeq: Number(pickPath) || 100,
      aisle: aisle || undefined,
      rack: rack || undefined,
      shelf: shelf || undefined,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setCode("");
    setName("");
    setMessage(`Bin created · ${res.data.slice(0, 8)}…`);
    await loadBins(warehouseId);
  }

  async function onDeactivate(binId: string) {
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await deactivateWarehouseBin(client, binId);
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Bin deactivated.");
    await loadBins(warehouseId);
  }

  async function onRename(bin: WarehouseBinRow) {
    const next = window.prompt("Bin name", bin.name);
    if (next == null || !next.trim()) return;
    const client = createWebClient();
    if (!client) return;
    setBusy(true);
    setMessage(null);
    const res = await updateWarehouseBin(client, {
      binId: bin.id,
      name: next.trim(),
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage("Bin updated.");
    await loadBins(warehouseId);
  }

  async function onAssignBin(e: FormEvent) {
    e.preventDefault();
    const client = createWebClient();
    if (!client || !selectedItem || !warehouseId) return;
    setBusy(true);
    setMessage(null);
    const res = await setStockLevelBin(client, {
      stockItemId: selectedItem.id,
      warehouseId,
      binId: assignBinId || null,
    });
    setBusy(false);
    if (!res.ok) {
      setMessage(res.error);
      return;
    }
    setMessage(
      assignBinId
        ? `Preferred bin set for ${selectedItem.oem_part_number}`
        : `Preferred bin cleared for ${selectedItem.oem_part_number}`,
    );
  }

  if (boot.kind === "loading") {
    return <p className={styles.muted}>Loading bins…</p>;
  }
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede}>
        <Link href="/login">Sign in</Link> with warehouse staff to manage bins.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <p className={styles.lede} role="alert">
        {boot.message}{" "}
        <button type="button" className={styles.btnGhost} onClick={() => void refresh()}>
          Retry
        </button>
      </p>
    );
  }

  if (boot.warehouses.length === 0) {
    return (
      <p className={styles.muted}>
        No active warehouses. Create a warehouse before adding bins.
      </p>
    );
  }

  const activeBins = bins.filter((b) => b.is_active);

  return (
    <div className={styles.form}>
      <label className={styles.field}>
        Warehouse
        <select
          value={warehouseId}
          onChange={(e) => setWarehouseId(e.target.value)}
          disabled={busy}
        >
          {boot.warehouses.map((w) => (
            <option key={w.id} value={w.id}>
              {w.code} — {w.name}
              {w.is_quarantine ? " (quarantine)" : ""}
            </option>
          ))}
        </select>
      </label>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Create bin</legend>
        <form onSubmit={(e) => void onCreate(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              Code
              <input
                value={code}
                onChange={(e) => setCode(e.target.value)}
                disabled={busy}
                required
              />
            </label>
            <label className={styles.field}>
              Name
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                disabled={busy}
                required
              />
            </label>
            <label className={styles.field}>
              Pick path seq
              <input
                value={pickPath}
                onChange={(e) => setPickPath(e.target.value)}
                disabled={busy}
                inputMode="numeric"
              />
            </label>
            <label className={styles.field}>
              Aisle
              <input value={aisle} onChange={(e) => setAisle(e.target.value)} disabled={busy} />
            </label>
            <label className={styles.field}>
              Rack
              <input value={rack} onChange={(e) => setRack(e.target.value)} disabled={busy} />
            </label>
            <label className={styles.field}>
              Shelf
              <input value={shelf} onChange={(e) => setShelf(e.target.value)} disabled={busy} />
            </label>
          </div>
          <div className={styles.formActions}>
            <button type="submit" className={styles.btn} disabled={busy}>
              Create bin
            </button>
          </div>
        </form>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Bins</legend>
        {bins.length === 0 ? (
          <p className={styles.muted}>No bins in this warehouse yet.</p>
        ) : (
          <ul className={styles.list}>
            {bins.map((b) => (
              <li key={b.id}>
                <strong>
                  {b.code} — {b.name}
                </strong>
                {!b.is_active ? " · inactive" : ""}
                <br />
                <span className={styles.muted}>
                  path {b.pick_path_seq}
                  {b.aisle ? ` · aisle ${b.aisle}` : ""}
                  {b.rack ? ` · rack ${b.rack}` : ""}
                  {b.shelf ? ` · shelf ${b.shelf}` : ""}
                </span>
                <br />
                <button
                  type="button"
                  className={styles.btnGhost}
                  disabled={busy}
                  onClick={() => void onRename(b)}
                >
                  Rename
                </button>{" "}
                {b.is_active ? (
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy}
                    onClick={() => void onDeactivate(b.id)}
                  >
                    Deactivate
                  </button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={styles.legend}>Set preferred stock bin</legend>
        <form onSubmit={(e) => void onAssignBin(e)}>
          <div className={styles.formGrid}>
            <label className={styles.field}>
              OEM / stock
              <input
                value={itemQuery}
                onChange={(e) => {
                  setItemQuery(e.target.value);
                  setSelectedItem(null);
                }}
                disabled={busy}
                placeholder="Type OEM…"
                autoComplete="off"
              />
            </label>
            <label className={styles.field}>
              Bin
              <select
                value={assignBinId}
                onChange={(e) => setAssignBinId(e.target.value)}
                disabled={busy}
              >
                <option value="">Clear preferred bin</option>
                {activeBins.map((b) => (
                  <option key={b.id} value={b.id}>
                    {b.code} — {b.name}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {hits.length > 0 && !selectedItem ? (
            <ul className={styles.list}>
              {hits.map((item) => (
                <li key={item.id}>
                  <button
                    type="button"
                    className={styles.btnGhost}
                    onClick={() => {
                      setSelectedItem(item);
                      setItemQuery(item.oem_part_number);
                      setHits([]);
                    }}
                  >
                    {item.oem_part_number}
                    {item.description ? ` — ${item.description}` : ""}
                  </button>
                </li>
              ))}
            </ul>
          ) : null}
          {selectedItem ? (
            <p className={styles.muted}>
              Selected {selectedItem.oem_part_number}
              {selectedItem.description ? ` — ${selectedItem.description}` : ""}
            </p>
          ) : null}
          <div className={styles.formActions}>
            <button
              type="submit"
              className={styles.btn}
              disabled={busy || !selectedItem}
            >
              Set stock level bin
            </button>
          </div>
        </form>
      </fieldset>

      {message ? (
        <p className={styles.formStatus} role="status">
          {message}
        </p>
      ) : null}
    </div>
  );
}
