"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import styles from "@/components/account.module.css";
import { harareToday, money } from "@/lib/staff-dashboard";
import { EXCEPTION_LABEL, getExceptionReport, requireSession, type ExceptionReport } from "@/lib/staff-exceptions";
import { listWarehouses, type WarehouseOption } from "@/lib/staff-warehouse";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; r: ExceptionReport };

function daysBefore(iso: string, n: number): string {
  const d = new Date(iso + "T12:00:00Z");
  d.setUTCDate(d.getUTCDate() - n);
  return d.toISOString().slice(0, 10);
}

const SEVERITY_LABEL = { high: "High", medium: "Check", low: "Note" } as const;

/**
 * Unusual activity over a period: voids, parts removed after ringing up, discounts and price cuts,
 * returns, till and driver cash differences, card trouble, sales below cost or after hours, failed
 * deliveries, balances left on account and stock count differences. "By person" shows patterns.
 */
export function StaffExceptionsPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [to, setTo] = useState(harareToday());
  const [from, setFrom] = useState(daysBefore(harareToday(), 6));
  const [warehouseId, setWarehouseId] = useState("");
  const [warehouses, setWarehouses] = useState<WarehouseOption[]>([]);
  const [kind, setKind] = useState<string | null>(null);
  const [person, setPerson] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    const client = createWebClient();
    if (!client) {
      setBoot({ kind: "error", message: "Supabase is not configured on this environment." });
      return;
    }
    const session = await requireSession(client);
    if (!session.ok) {
      setBoot({ kind: "auth" });
      return;
    }
    const res = await getExceptionReport(client, from, to, warehouseId || null);
    setBoot(res.ok ? { kind: "ready", r: res.data } : { kind: "error", message: res.error });
  }, [from, to, warehouseId]);

  useEffect(() => {
    const client = createWebClient();
    if (client) void listWarehouses(client).then((r) => r.ok && setWarehouses(r.data));
  }, []);
  useEffect(() => {
    void refresh();
  }, [refresh]);

  const shown = useMemo(() => {
    if (boot.kind !== "ready") return [];
    return boot.r.items.filter((i) => (!kind || i.kind === kind) && (!person || i.personId === person));
  }, [boot, kind, person]);

  const filters = (
    <div className={styles.formActions} role="group" aria-label="Period and branch" style={{ flexWrap: "wrap" }}>
      <label className={styles.muted}>
        From{" "}
        <input type="date" className={styles.input} value={from} max={to} onChange={(e) => setFrom(e.target.value || from)} />
      </label>
      <label className={styles.muted}>
        to <input type="date" className={styles.input} value={to} max={harareToday()} onChange={(e) => setTo(e.target.value || to)} />
      </label>
      <select className={styles.input} aria-label="Branch" value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} style={{ flex: "0 1 220px" }}>
        <option value="">All branches</option>
        {warehouses.map((w) => (
          <option key={w.id} value={w.id}>
            {w.name}
          </option>
        ))}
      </select>
    </div>
  );

  if (boot.kind === "loading") return <>{filters}<p className={styles.muted}>Looking for exceptions…</p></>;
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede} role="status">
        <Link href="/login">Sign in</Link> as a manager, finance or admin.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <>
        {filters}
        <p className={styles.lede} role="alert">
          {boot.message}
        </p>
      </>
    );
  }

  const { r } = boot;
  const high = r.items.filter((i) => i.severity === "high").length;

  return (
    <div className={styles.form}>
      {filters}
      <div className={styles.tiles}>
        <div className={styles.tile}>
          <div className={styles.tileLabel}>Exceptions</div>
          <div className={styles.tileValue}>{r.items.length}</div>
          <div className={styles.tileSub}>
            {r.from} to {r.to} · {r.warehouseName ?? "all branches"}
          </div>
        </div>
        <div className={high ? `${styles.tile} ${styles.tileAlert}` : styles.tile}>
          <div className={styles.tileLabel}>High</div>
          <div className={styles.tileValue}>{high}</div>
          <div className={styles.tileSub}>look at these first</div>
        </div>
      </div>

      {r.items.length === 0 ? (
        <p className={styles.lede} role="status">
          Nothing unusual in this period.
        </p>
      ) : (
        <div className={styles.dashGrid}>
          <div>
            <h2 className={styles.sectionTitle}>By kind</h2>
            <table className={styles.table}>
              <tbody>
                {r.byKind.map((k) => (
                  <tr key={k.kind + k.currency} aria-selected={kind === k.kind}>
                    <td>
                      <button type="button" className={styles.linkButton} onClick={() => setKind(kind === k.kind ? null : k.kind)}>
                        {kind === k.kind ? "✓ " : ""}
                        {EXCEPTION_LABEL[k.kind] ?? k.kind}
                      </button>
                    </td>
                    <td className={styles.num}>{k.count}</td>
                    <td className={styles.num}>{k.currency && k.amount ? money(k.amount, k.currency) : ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div>
            <h2 className={styles.sectionTitle}>By person</h2>
            <table className={styles.table}>
              <tbody>
                {r.byPerson.map((p) => (
                  <tr key={p.personId}>
                    <td>
                      <button type="button" className={styles.linkButton} onClick={() => setPerson(person === p.personId ? null : p.personId)}>
                        {person === p.personId ? "✓ " : ""}
                        {p.person ?? "Unknown"}
                      </button>
                      <br />
                      <span className={styles.muted}>
                        {Object.entries(p.kinds)
                          .sort((a, b) => b[1] - a[1])
                          .map(([k, n]) => `${EXCEPTION_LABEL[k] ?? k} ${n}`)
                          .join(" · ")}
                      </span>
                    </td>
                    <td className={styles.num}>
                      {p.count}
                      {p.high ? <span className={styles.urgentTag} style={{ marginLeft: 6 }}>{p.high} high</span> : null}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {r.items.length > 0 ? (
        <>
          <h2 className={styles.sectionTitle}>
            {kind || person ? "Filtered" : "All"} ({shown.length})
            {kind || person ? (
              <button
                type="button"
                className={styles.btnGhost}
                style={{ marginLeft: 12 }}
                onClick={() => {
                  setKind(null);
                  setPerson(null);
                }}
              >
                Clear filter
              </button>
            ) : null}
          </h2>
          <div className={styles.tableWrap}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>When</th>
                  <th>What</th>
                  <th>Who</th>
                  <th className={styles.num}>Amount</th>
                </tr>
              </thead>
              <tbody>
                {shown.map((i, n) => (
                  <tr key={n}>
                    <td style={{ whiteSpace: "nowrap" }}>
                      {new Date(i.at).toLocaleString([], { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" })}
                    </td>
                    <td>
                      {i.severity !== "low" ? (
                        <span className={styles.urgentTag} style={i.severity === "medium" ? { background: "var(--gtr-steel)" } : undefined}>
                          {SEVERITY_LABEL[i.severity]}
                        </span>
                      ) : null}
                      <Link href={i.href}>{EXCEPTION_LABEL[i.kind] ?? i.kind}</Link>
                      <br />
                      <span className={styles.muted}>
                        {i.detail}
                        {i.warehouse ? ` · ${i.warehouse}` : ""}
                      </span>
                    </td>
                    <td>
                      {i.person ?? "—"}
                      {i.approvedBy ? (
                        <>
                          <br />
                          <span className={styles.muted}>approved by {i.approvedBy}</span>
                        </>
                      ) : null}
                    </td>
                    <td className={styles.num}>{i.amount != null && i.currency ? money(i.amount, i.currency) : ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      ) : null}
    </div>
  );
}
