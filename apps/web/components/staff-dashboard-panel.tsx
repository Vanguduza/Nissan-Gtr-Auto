"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import { HourlySalesChart } from "@/components/staff-hourly-sales-chart";
import { waitedFor } from "@/lib/staff-approvals";
import {
  CHANNEL_LABEL,
  TENDER_LABEL,
  getDailyDashboard,
  harareToday,
  money,
  requireSession,
  vsLastWeek,
  type DailyDashboard,
} from "@/lib/staff-dashboard";
import { listWarehouses, type WarehouseOption } from "@/lib/staff-warehouse";
import { createWebClient } from "@/lib/supabase";

type Boot =
  | { kind: "loading" }
  | { kind: "auth" }
  | { kind: "error"; message: string }
  | { kind: "ready"; d: DailyDashboard };

function Tile({ label, value, sub, alert, href }: { label: string; value: string; sub?: string | null; alert?: boolean; href?: string }) {
  const body = (
    <>
      <div className={styles.tileLabel}>{label}</div>
      <div className={styles.tileValue}>{value}</div>
      {sub ? <div className={styles.tileSub}>{sub}</div> : null}
    </>
  );
  const cls = alert ? `${styles.tile} ${styles.tileAlert}` : styles.tile;
  return href ? (
    <Link href={href} className={cls} style={{ textDecoration: "none" }}>
      {body}
    </Link>
  ) : (
    <div className={cls}>{body}</div>
  );
}

/**
 * The day at a glance for a manager: sales and margin, money taken by tender, what is still owed,
 * tills, deliveries, driver cash, stock to reorder and decisions waiting. Pick a day and a branch;
 * money is shown per currency.
 */
export function StaffDashboardPanel() {
  const [boot, setBoot] = useState<Boot>({ kind: "loading" });
  const [date, setDate] = useState(harareToday());
  const [warehouseId, setWarehouseId] = useState<string>("");

  // A link to a given day (e.g. the 07:00 summary notice: ?date=YYYY-MM-DD) opens that day.
  useEffect(() => {
    const d = new URLSearchParams(window.location.search).get("date");
    if (d && /^\d{4}-\d{2}-\d{2}$/.test(d) && d <= harareToday()) setDate(d);
  }, []);
  const [warehouses, setWarehouses] = useState<WarehouseOption[]>([]);

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
    const res = await getDailyDashboard(client, date, warehouseId || null);
    setBoot(res.ok ? { kind: "ready", d: res.data } : { kind: "error", message: res.error });
  }, [date, warehouseId]);

  useEffect(() => {
    const client = createWebClient();
    if (!client) return;
    void listWarehouses(client).then((r) => r.ok && setWarehouses(r.data));
  }, []);

  useEffect(() => {
    void refresh();
    // Today's numbers move: refresh every 2 minutes while open.
    if (date !== harareToday()) return;
    const t = window.setInterval(() => void refresh(), 120_000);
    return () => window.clearInterval(t);
  }, [refresh, date]);

  const filters = (
    <div className={styles.formActions} role="group" aria-label="Day and branch" style={{ flexWrap: "wrap" }}>
      <input
        type="date"
        className={styles.input}
        aria-label="Day"
        value={date}
        max={harareToday()}
        onChange={(e) => setDate(e.target.value || harareToday())}
        style={{ flex: "0 0 auto" }}
      />
      <select className={styles.input} aria-label="Branch" value={warehouseId} onChange={(e) => setWarehouseId(e.target.value)} style={{ flex: "0 1 220px" }}>
        <option value="">All branches</option>
        {warehouses.map((w) => (
          <option key={w.id} value={w.id}>
            {w.name}
          </option>
        ))}
      </select>
      {date !== harareToday() ? (
        <button type="button" className={styles.btnGhost} onClick={() => setDate(harareToday())}>
          Today
        </button>
      ) : null}
    </div>
  );

  if (boot.kind === "loading") return <>{filters}<p className={styles.muted}>Loading the day…</p></>;
  if (boot.kind === "auth") {
    return (
      <p className={styles.lede} role="status">
        <Link href="/login">Sign in</Link> as a manager, finance or admin to see the day.
      </p>
    );
  }
  if (boot.kind === "error") {
    return (
      <>
        {filters}
        <p className={styles.lede} role="alert">
          {boot.message}{" "}
          <button type="button" className={styles.btnGhost} onClick={() => void refresh()}>
            Retry
          </button>
        </p>
      </>
    );
  }

  const { d } = boot;
  const currencies = Array.from(new Set([...d.sales.map((x) => x.currency), ...d.payments.map((x) => x.currency)])).sort();
  const isToday = d.date === harareToday();
  const variances = d.tills.filter((t) => t.variance != null && Math.abs(t.variance) > 0.009);

  return (
    <div className={styles.form}>
      {filters}

      {currencies.length === 0 ? (
        <p className={styles.lede} role="status">
          No sales {isToday ? "yet today" : "on this day"}
          {d.warehouseName ? ` at ${d.warehouseName}` : ""}.
        </p>
      ) : null}

      {currencies.map((cur) => {
        const s = d.sales.find((x) => x.currency === cur);
        const taken = d.payments.filter((p) => p.currency === cur).reduce((a, p) => a + p.amount, 0);
        const unpaid = d.unpaidToday.find((u) => u.currency === cur);
        return (
          <section key={cur}>
            <h2 className={styles.sectionTitle}>Sales · {cur}</h2>
            <div className={styles.tiles}>
              <Tile label="Net sales" value={money(s?.net ?? 0, cur)} sub={s ? vsLastWeek(s.net, s.lastWeekNet) ?? `${s.invoices} sales` : null} />
              <Tile
                label="Gross margin"
                value={s?.marginPct != null ? `${s.marginPct}%` : "—"}
                sub={s ? `${money(s.margin, cur)} on cost ${money(s.cost, cur)}` : null}
              />
              <Tile label="Average sale" value={s?.averageSale != null ? money(s.averageSale, cur) : "—"} sub={s ? `${s.invoices} sales` : null} />
              <Tile label="Returns" value={money(s?.returns ?? 0, cur)} />
              <Tile label="Money taken" value={money(taken, cur)} sub="all tenders, posted" />
              <Tile label="Sold, not yet paid" value={money(unpaid?.amount ?? 0, cur)} sub={unpaid ? `${unpaid.invoices} sales on account / on delivery` : null} />
            </div>
            <h3 className={styles.sectionTitle} style={{ fontSize: "0.95rem" }}>
              Sales by hour
            </h3>
            <HourlySalesChart rows={d.hourly} currency={cur} />
            <div className={styles.dashGrid}>
              <div>
                <h3 className={styles.sectionTitle} style={{ fontSize: "0.95rem" }}>
                  Where and how
                </h3>
                <table className={styles.table}>
                  <tbody>
                    {d.channels
                      .filter((c) => c.currency === cur)
                      .map((c) => (
                        <tr key={"c" + c.channel}>
                          <td>{CHANNEL_LABEL[c.channel] ?? c.channel}</td>
                          <td className={styles.num}>{c.invoices}</td>
                          <td className={styles.num}>{money(c.total, cur)}</td>
                        </tr>
                      ))}
                    {d.payments
                      .filter((p) => p.currency === cur)
                      .map((p) => (
                        <tr key={"p" + p.tender}>
                          <td>Paid by {TENDER_LABEL[p.tender] ?? p.tender}</td>
                          <td className={styles.num}>{p.count}</td>
                          <td className={styles.num}>{money(p.amount, cur)}</td>
                        </tr>
                      ))}
                  </tbody>
                </table>
              </div>
            </div>
          </section>
        );
      })}

      <h2 className={styles.sectionTitle}>Needs attention</h2>
      <div className={styles.tiles}>
        <Tile label="Decisions waiting" value={String(d.approvalsWaiting)} alert={d.approvalsWaiting > 0} href="/staff/approvals" sub="open the inbox" />
        <Tile
          label="Till differences"
          value={String(variances.length)}
          alert={variances.some((t) => !t.approved)}
          sub={variances.length ? variances.map((t) => `${t.cashier ?? "till"} ${money(t.variance ?? 0, t.currency)}`).join(" · ") : `${d.tills.length} tills`}
        />
        {d.driverCashHeld.map((h) => (
          <Tile
            key={h.currency}
            label="Cash with drivers"
            value={money(h.amount, h.currency)}
            alert={h.oldestAt != null && Date.now() - Date.parse(h.oldestAt) > 24 * 3600_000}
            sub={`${h.drivers} driver${h.drivers === 1 ? "" : "s"}${h.oldestAt ? ` · oldest ${waitedFor(h.oldestAt)}` : ""}`}
            href="/staff/logistics/driver-cash"
          />
        ))}
        <Tile label="Stock to reorder" value={String(d.lowStockCount)} alert={d.lowStockCount > 0} sub={`${d.backordersWaiting} back-orders waiting`} />
      </div>

      <div className={styles.dashGrid}>
        <div>
          <h2 className={styles.sectionTitle}>Deliveries</h2>
          <table className={styles.table}>
            <tbody>
              <tr><td>Sent out {isToday ? "today" : ""}</td><td className={styles.num}>{d.deliveries.dispatched}</td></tr>
              <tr><td>Delivered</td><td className={styles.num}>{d.deliveries.completed}</td></tr>
              <tr><td>Failed (refused, absent…)</td><td className={styles.num}>{d.deliveries.failed}</td></tr>
              <tr><td>On the road now</td><td className={styles.num}>{d.deliveries.onTheRoad}</td></tr>
              <tr><td>Waiting for a driver</td><td className={styles.num}>{d.deliveries.waitingForDriver}</td></tr>
              {d.cashOnDelivery.map((c) => (
                <tr key={c.currency}>
                  <td>Cash collected at the door</td>
                  <td className={styles.num}>{money(c.amount, c.currency)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div>
          <h2 className={styles.sectionTitle}>Owed by customers</h2>
          <table className={styles.table}>
            <tbody>
              {d.owed.length === 0 ? (
                <tr><td>Nothing owed</td><td /></tr>
              ) : (
                d.owed.map((o) => (
                  <tr key={o.currency}>
                    <td>
                      {o.customers} customer{o.customers === 1 ? "" : "s"}
                      {o.overdue30 > 0 ? ` · ${money(o.overdue30, o.currency)} over 30 days` : ""}
                    </td>
                    <td className={styles.num}>{money(o.amount, o.currency)}</td>
                  </tr>
                ))
              )}
              <tr>
                <td>
                  <Link href="/staff/crm/credit">Suspended customers</Link>
                  {d.suspendedToday ? ` · ${d.suspendedToday} suspended ${isToday ? "today" : "this day"}` : ""}
                </td>
                <td className={styles.num}>{d.suspendedCustomers}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <div className={styles.dashGrid}>
        <div>
          <h2 className={styles.sectionTitle}>Best sellers</h2>
          {d.topParts.length === 0 ? (
            <p className={styles.muted}>No sales.</p>
          ) : (
            <table className={styles.table}>
              <tbody>
                {d.topParts.map((p) => (
                  <tr key={p.oemPartNumber + p.currency}>
                    <td>
                      {p.description ?? p.oemPartNumber}
                      <br />
                      <span className={styles.muted}>{p.oemPartNumber}</span>
                    </td>
                    <td className={styles.num}>× {p.qty}</td>
                    <td className={styles.num}>{money(p.total, p.currency)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        <div>
          <h2 className={styles.sectionTitle}>Stock to reorder</h2>
          {d.lowStock.length === 0 ? (
            <p className={styles.muted}>Nothing at or below its reorder point.</p>
          ) : (
            <table className={styles.table}>
              <thead>
                <tr>
                  <th>Part</th>
                  <th className={styles.num}>On hand</th>
                  <th className={styles.num}>Reorder at</th>
                </tr>
              </thead>
              <tbody>
                {d.lowStock.map((l) => (
                  <tr key={l.oemPartNumber + l.warehouse}>
                    <td>
                      {l.description ?? l.oemPartNumber}
                      <br />
                      <span className={styles.muted}>
                        {l.oemPartNumber} · {l.warehouse}
                        {l.reorderQty ? ` · order ${l.reorderQty}` : ""}
                      </span>
                    </td>
                    <td className={styles.num}>{l.onHand}</td>
                    <td className={styles.num}>{l.reorderPoint}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </div>

      <h2 className={styles.sectionTitle}>Tills</h2>
      {d.tills.length === 0 ? (
        <p className={styles.muted}>No till opened.</p>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.table}>
            <thead>
              <tr>
                <th>Till</th>
                <th>Status</th>
                <th className={styles.num}>Float</th>
                <th className={styles.num}>Expected</th>
                <th className={styles.num}>Counted</th>
                <th className={styles.num}>Difference</th>
              </tr>
            </thead>
            <tbody>
              {d.tills.map((t) => (
                <tr key={t.id}>
                  <td>
                    {t.cashier ?? "—"}
                    <br />
                    <span className={styles.muted}>{t.warehouse}</span>
                  </td>
                  <td>{t.status === "variance_pending" ? "Difference to approve" : t.status === "open" ? "Open" : t.approved ? "Closed · approved" : "Closed"}</td>
                  <td className={styles.num}>{money(t.openingFloat, t.currency)}</td>
                  <td className={styles.num}>{t.expectedCash != null ? money(t.expectedCash, t.currency) : "—"}</td>
                  <td className={styles.num}>{t.countedCash != null ? money(t.countedCash, t.currency) : "—"}</td>
                  <td className={styles.num}>{t.variance != null ? money(t.variance, t.currency) : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <p className={styles.muted} style={{ marginTop: "1rem" }}>
        Updated {new Date(d.generatedAt).toLocaleTimeString()} · {d.warehouseName ?? "all branches"} · day in Harare time
        {isToday ? " · refreshes every 2 minutes" : ""}
      </p>
    </div>
  );
}
