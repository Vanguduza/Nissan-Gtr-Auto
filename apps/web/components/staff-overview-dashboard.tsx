"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import {
  Banknote,
  BarChart3,
  Bell,
  iconStroke,
  LayoutGrid,
  Package,
  Search,
  ShoppingCart,
  Users,
  Warehouse,
} from "@/components/icons";
import { useStaffAuth } from "@/components/staff-auth-context";
import {
  staffNavIconForModule,
} from "@/components/staff-nav";
import {
  filterNavTreeForModuleAccess,
  type StaffNavModule,
} from "@/lib/staff-auth";
import {
  fetchStaffOverview,
  moneyUsd,
  overviewDateBounds,
  overviewDefaultDates,
  percentChange,
  searchStaffOverview,
  type OverviewCustomerSegment,
  type OverviewPaymentMix,
  type OverviewTrendPoint,
  type StaffOverviewDashboard,
  type StaffOverviewSearchHit,
} from "@/lib/staff-overview";
import { createWebClient } from "@/lib/supabase";
import styles from "./staff-overview.module.css";

const chartColors = ["#3b82f6", "#22c7a9", "#f6c65b", "#8b72e8", "#f17f7f", "#7aa2f7", "#94a3b8"];

function n(value: unknown): number {
  const parsed = Number(value ?? 0);
  return Number.isFinite(parsed) ? parsed : 0;
}

function pctLabel(value: number | null): string {
  if (value == null) return "No prior baseline";
  if (Math.abs(value) < 0.05) return "0% vs prior period";
  return `${value > 0 ? "+" : ""}${value.toFixed(1)}% vs prior period`;
}

function roleLabel(roles: string[]): string {
  const preferred = ["admin", "finance", "sales", "warehouse", "dispatcher", "hr"];
  const hit = preferred.find((r) => roles.includes(r)) ?? roles[0] ?? "staff";
  return hit === "admin" ? "Administrator" : `${hit.charAt(0).toUpperCase()}${hit.slice(1)}`;
}

function statusLabel(raw: string): string {
  return raw.replaceAll("_", " ").replace(/\b\w/g, (m) => m.toUpperCase());
}

function SearchResults({
  query,
  hits,
  busy,
}: {
  query: string;
  hits: StaffOverviewSearchHit[];
  busy: boolean;
}) {
  if (query.trim().length < 2) return null;
  return (
    <div className={styles.searchResults} role="listbox" aria-label="Staff search results">
      {busy ? <p className={styles.searchState}>Searching…</p> : null}
      {!busy && hits.length === 0 ? <p className={styles.searchState}>No matching staff records</p> : null}
      {!busy
        ? hits.map((hit) => {
            const sep = hit.destination.includes("?") ? "&" : "?";
            const href = `${hit.destination}${sep}overview_q=${encodeURIComponent(hit.filter)}`;
            return (
              <Link key={`${hit.kind}:${hit.id}`} href={href} className={styles.searchResult}>
                <span className={styles.searchResultKind}>{hit.kind}</span>
                <span>
                  <strong>{hit.label}</strong>
                  <small>{hit.subtitle}</small>
                </span>
              </Link>
            );
          })
        : null}
    </div>
  );
}

function MetricCard({
  icon,
  label,
  value,
  change,
  detail,
}: {
  icon: React.ReactNode;
  label: string;
  value: string;
  change?: number | null;
  detail?: string;
}) {
  return (
    <article className={styles.metricCard}>
      <div className={styles.metricIcon}>{icon}</div>
      <div className={styles.metricBody}>
        <span>{label}</span>
        <strong>{value}</strong>
        <small className={change != null && change < 0 ? styles.changeDown : styles.changeUp}>
          {detail ?? pctLabel(change ?? null)}
        </small>
      </div>
    </article>
  );
}

function LineChart({ points }: { points: OverviewTrendPoint[] }) {
  const width = 700;
  const height = 250;
  const padX = 36;
  const padTop = 18;
  const padBottom = 30;
  const max = Math.max(1, ...points.map((p) => n(p.revenue_usd)));
  const innerW = width - padX * 2;
  const innerH = height - padTop - padBottom;
  const coords = points.map((p, index) => {
    const x = points.length <= 1 ? padX : padX + (index / (points.length - 1)) * innerW;
    const y = padTop + innerH - (n(p.revenue_usd) / max) * innerH;
    return { x, y, p };
  });
  const line = coords.map((c, i) => `${i === 0 ? "M" : "L"}${c.x.toFixed(1)},${c.y.toFixed(1)}`).join(" ");
  const area = coords.length
    ? `${line} L${coords.at(-1)!.x.toFixed(1)},${(padTop + innerH).toFixed(1)} L${coords[0].x.toFixed(1)},${(padTop + innerH).toFixed(1)} Z`
    : "";
  const ticks = [0, 0.25, 0.5, 0.75, 1];
  const labels = coords.filter((_, i) => i === 0 || i === coords.length - 1 || i % Math.max(1, Math.floor(coords.length / 5)) === 0);

  return (
    <div className={styles.lineChart}>
      {points.length === 0 ? <div className={styles.emptyChart}>No sales in this period</div> : null}
      <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Sales revenue trend in USD equivalent">
        <defs>
          <linearGradient id="salesFill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#2f7de1" stopOpacity="0.24" />
            <stop offset="100%" stopColor="#2f7de1" stopOpacity="0" />
          </linearGradient>
        </defs>
        {ticks.map((t) => {
          const y = padTop + innerH - innerH * t;
          return (
            <g key={t}>
              <line x1={padX} x2={width - padX} y1={y} y2={y} className={styles.gridLine} />
              <text x={4} y={y + 4} className={styles.axisText}>{moneyUsd(max * t).replace("$", "$ ")}</text>
            </g>
          );
        })}
        {area ? <path d={area} fill="url(#salesFill)" /> : null}
        {line ? <path d={line} className={styles.trendLine} /> : null}
        {coords.map((c) => <circle key={c.p.date} cx={c.x} cy={c.y} r="3.5" className={styles.trendPoint} />)}
        {labels.map((c) => (
          <text key={`x-${c.p.date}`} x={c.x} y={height - 7} textAnchor="middle" className={styles.axisText}>
            {new Date(`${c.p.date}T00:00:00`).toLocaleDateString(undefined, { month: "short", day: "numeric" })}
          </text>
        ))}
      </svg>
    </div>
  );
}

function Donut({
  items,
  valueOf,
  labelOf,
  centerTop,
  centerBottom,
}: {
  items: (OverviewPaymentMix | OverviewCustomerSegment)[];
  valueOf: (item: OverviewPaymentMix | OverviewCustomerSegment) => number;
  labelOf: (item: OverviewPaymentMix | OverviewCustomerSegment) => string;
  centerTop: string;
  centerBottom: string;
}) {
  const total = items.reduce((sum, item) => sum + Math.max(0, valueOf(item)), 0);
  let cursor = 0;
  const stops = items.map((item, index) => {
    const start = total > 0 ? (cursor / total) * 360 : 0;
    cursor += Math.max(0, valueOf(item));
    const end = total > 0 ? (cursor / total) * 360 : 0;
    return `${chartColors[index % chartColors.length]} ${start}deg ${end}deg`;
  });
  const background = total > 0 ? `conic-gradient(${stops.join(",")})` : "conic-gradient(#e7edf4 0deg 360deg)";

  return (
    <div className={styles.donutWrap}>
      <div className={styles.donut} style={{ background }}>
        <div className={styles.donutHole}>
          <strong>{centerTop}</strong>
          <span>{centerBottom}</span>
        </div>
      </div>
      <div className={styles.legend}>
        {items.length === 0 ? <span className={styles.muted}>No data in this period</span> : null}
        {items.map((item, index) => {
          const value = Math.max(0, valueOf(item));
          const pct = total > 0 ? (value / total) * 100 : 0;
          return (
            <div key={`${labelOf(item)}-${index}`} className={styles.legendRow}>
              <i style={{ background: chartColors[index % chartColors.length] }} />
              <span>{labelOf(item)}</span>
              <strong>{pct.toFixed(0)}%</strong>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function OverviewSidebar({ modules }: { modules: StaffNavModule[] }) {
  return (
    <aside className={styles.sidebar}>
      <Link href="/staff" className={styles.sideBrand} aria-label="Nissan GTR Auto staff overview">
        <Image src="/brand/logo.png" alt="Nissan GTR Auto" width={58} height={58} priority />
      </Link>
      <nav className={styles.sideNav} aria-label="Staff overview navigation">
        <Link href="/staff" className={styles.sideActive}>
          <LayoutGrid size={17} strokeWidth={iconStroke} />
          Overview
        </Link>
        {modules.map((mod) => {
          const Icon = staffNavIconForModule(mod);
          return (
            <Link key={mod.id} href={mod.href} className={styles.sideLink}>
              <Icon size={17} strokeWidth={iconStroke} />
              {mod.label}
            </Link>
          );
        })}
      </nav>
      <div className={styles.sideBottom}>
        <Link href="/staff/change-password" className={styles.sideLink}>
          <Users size={17} strokeWidth={iconStroke} />
          Profile & security
        </Link>
        <Link href="/staff/chat" className={styles.sideLink}>
          <Bell size={17} strokeWidth={iconStroke} />
          Customer chat
        </Link>
      </div>
    </aside>
  );
}

export function StaffOverviewDashboard() {
  const ctx = useStaffAuth();
  const defaults = useMemo(() => overviewDefaultDates(), []);
  const [from, setFrom] = useState(defaults.from);
  const [to, setTo] = useState(defaults.to);
  const [dashboard, setDashboard] = useState<StaffOverviewDashboard | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [searchHits, setSearchHits] = useState<StaffOverviewSearchHit[]>([]);
  const [searchBusy, setSearchBusy] = useState(false);

  const modules = useMemo(
    () =>
      ctx
        ? filterNavTreeForModuleAccess(ctx.roles, ctx.moduleAccess).filter(
            (e): e is StaffNavModule & { kind: "module" } => e.kind === "module",
          )
        : [],
    [ctx],
  );

  useEffect(() => {
    let cancelled = false;
    const bounds = overviewDateBounds(from, to);
    if (!bounds.ok) {
      setError(bounds.error);
      setLoading(false);
      return;
    }
    const client = createWebClient();
    if (!client) {
      setError("Supabase is not configured.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError(null);
    void fetchStaffOverview(client, bounds.data).then((res) => {
      if (cancelled) return;
      setLoading(false);
      if (!res.ok) {
        setDashboard(null);
        setError(res.error);
      } else {
        setDashboard(res.data);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [from, to]);

  useEffect(() => {
    let cancelled = false;
    const q = query.trim();
    if (q.length < 2) {
      setSearchHits([]);
      setSearchBusy(false);
      return;
    }
    const timer = window.setTimeout(() => {
      const client = createWebClient();
      if (!client) return;
      setSearchBusy(true);
      void searchStaffOverview(client, q).then((res) => {
        if (cancelled) return;
        setSearchBusy(false);
        setSearchHits(res.ok ? res.data : []);
      });
    }, 220);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [query]);

  const trendRevenue = dashboard?.sales_trend.reduce((sum, p) => sum + n(p.revenue_usd), 0) ?? 0;
  const orders = n(dashboard?.summary.sales?.order_count);
  const previousOrders = n(dashboard?.summary.previous_sales?.order_count);
  const customers = n(dashboard?.summary.customers);
  const previousCustomers = n(dashboard?.summary.previous_customers);
  const margin = dashboard?.summary.net_margin_pct == null ? null : n(dashboard.summary.net_margin_pct);
  const previousMargin = dashboard?.summary.previous_net_margin_pct == null ? null : n(dashboard.summary.previous_net_margin_pct);
  const displayName = dashboard?.staff.name?.trim() || "Staff";
  const firstName = displayName.split(/\s+/)[0] || "Staff";
  const initials = displayName.split(/\s+/).slice(0, 2).map((s) => s[0]).join("").toUpperCase() || "ST";

  const metricCards = dashboard
    ? [
        dashboard.permissions.commercial
          ? { label: "Total Revenue", value: moneyUsd(trendRevenue), icon: <Banknote size={20} />, detail: "USD equivalent" }
          : dashboard.permissions.inventory
            ? { label: "On Hand", value: n(dashboard.inventory.on_hand_qty).toLocaleString(), icon: <Warehouse size={20} />, detail: "saleable quantity" }
            : { label: "Modules", value: String(modules.length), icon: <LayoutGrid size={20} />, detail: "assigned access" },
        dashboard.permissions.commercial
          ? { label: "Total Orders", value: orders.toLocaleString(), icon: <ShoppingCart size={20} />, change: percentChange(orders, previousOrders) }
          : dashboard.permissions.logistics
            ? { label: "Open Jobs", value: n(dashboard.delivery.open_jobs).toLocaleString(), icon: <ShoppingCart size={20} />, detail: "pending + dispatched" }
            : { label: "Roles", value: String(dashboard.staff.roles.length), icon: <Users size={20} />, detail: roleLabel(dashboard.staff.roles) },
        dashboard.permissions.customers
          ? { label: "Customers", value: customers.toLocaleString(), icon: <Users size={20} />, change: percentChange(customers, previousCustomers) }
          : dashboard.permissions.inventory
            ? { label: "Low Stock", value: n(dashboard.inventory.low_stock).toLocaleString(), icon: <Package size={20} />, detail: "SKUs at reorder point" }
            : { label: "Completed", value: n(dashboard.delivery.completed).toLocaleString(), icon: <Users size={20} />, detail: "deliveries in period" },
        dashboard.permissions.finance
          ? { label: "Net Margin", value: margin == null ? "—" : `${margin.toFixed(1)}%`, icon: <BarChart3 size={20} />, change: margin == null || previousMargin == null ? null : margin - previousMargin, detail: margin == null ? "No income in period" : undefined }
          : dashboard.permissions.inventory
            ? { label: "Stockouts", value: n(dashboard.inventory.out_of_stock).toLocaleString(), icon: <Warehouse size={20} />, detail: "SKUs with no saleable stock" }
            : { label: "Failed Jobs", value: n(dashboard.delivery.failed).toLocaleString(), icon: <Bell size={20} />, detail: "in selected period" },
      ]
    : [];

  return (
    <div className={styles.viewport}>
      <div className={styles.dashboardShell}>
        <OverviewSidebar modules={modules} />
        <main className={styles.main}>
          <div className={styles.topbar}>
            <div className={styles.searchWrap}>
              <Search size={18} strokeWidth={iconStroke} aria-hidden />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search products, orders, or customers…"
                aria-label="Search Nissan GTR Auto staff records"
              />
              <SearchResults query={query} hits={searchHits} busy={searchBusy} />
            </div>
            <Link href="/staff/analytics" className={styles.iconButton} aria-label="Open analytics alerts">
              <Bell size={19} strokeWidth={iconStroke} />
            </Link>
            <div className={styles.userMini}>
              <span>{initials}</span>
              <div><strong>{displayName}</strong><small>{roleLabel(dashboard?.staff.roles ?? ctx?.roles ?? [])}</small></div>
            </div>
          </div>

          <section className={styles.introRow}>
            <div>
              <span className={styles.kicker}>Good {new Date().getHours() < 12 ? "morning" : new Date().getHours() < 18 ? "afternoon" : "evening"},</span>
              <h1>Here’s what’s happening with Nissan GTR Auto</h1>
              <p>Sales, customers, stock and fulfilment from live business data.</p>
            </div>
            <div className={styles.dateRange}>
              <label><span>From</span><input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></label>
              <span>–</span>
              <label><span>To</span><input type="date" value={to} onChange={(e) => setTo(e.target.value)} /></label>
            </div>
          </section>

          {error ? <div className={styles.errorBanner} role="alert">{error}</div> : null}
          {loading && !dashboard ? <div className={styles.loadingPanel}>Loading live overview…</div> : null}

          {dashboard ? (
            <>
              <section className={styles.metricsGrid} aria-label="Overview KPIs">
                {metricCards.map((card) => <MetricCard key={card.label} {...card} />)}
              </section>

              <section className={styles.analyticsGrid}>
                <article className={styles.panelCard}>
                  <div className={styles.cardHeading}>
                    <div><h2>Sales Trend</h2><span>USD equivalent</span></div>
                    <Link href="/staff/analytics">View analytics</Link>
                  </div>
                  <LineChart points={dashboard.sales_trend} />
                </article>
                <article className={styles.panelCard}>
                  <div className={styles.cardHeading}><div><h2>Payment Mix</h2><span>Posted payments</span></div><Link href="/staff/finance?tab=payments">View all</Link></div>
                  <Donut
                    items={dashboard.payment_mix}
                    valueOf={(item) => "amount_usd" in item ? n(item.amount_usd) : 0}
                    labelOf={(item) => "tender" in item ? statusLabel(item.tender) : item.segment}
                    centerTop={moneyUsd(dashboard.payment_mix.reduce((s, i) => s + n(i.amount_usd), 0))}
                    centerBottom="settled"
                  />
                </article>
              </section>

              <section className={styles.analyticsGrid}>
                <article className={styles.panelCard}>
                  <div className={styles.cardHeading}><h2>Top Products</h2><Link href="/staff/warehouse/master-stock">View all</Link></div>
                  <div className={styles.productList}>
                    {dashboard.top_products.length === 0 ? <p className={styles.emptyState}>No posted product sales in this period.</p> : null}
                    {dashboard.top_products.map((item, index) => (
                      <Link key={item.stock_item_id} href={`/staff/warehouse/master-stock?overview_q=${encodeURIComponent(item.oem)}`} className={styles.productRow}>
                        <span className={styles.rank}>{index + 1}</span>
                        <span className={styles.productThumb}><Package size={18} /></span>
                        <span className={styles.productName}><strong>{item.description || item.oem}</strong><small>{item.oem}</small></span>
                        <span className={styles.productRevenue}><strong>{moneyUsd(n(item.revenue_usd))}</strong><small>{n(item.qty).toLocaleString()} sold</small></span>
                      </Link>
                    ))}
                  </div>
                </article>
                <article className={styles.panelCard}>
                  <div className={styles.cardHeading}><h2>Customer Segments</h2><Link href="/staff/crm/credit">View all</Link></div>
                  <Donut
                    items={dashboard.customer_segments}
                    valueOf={(item) => "customer_count" in item ? n(item.customer_count) : 0}
                    labelOf={(item) => "segment" in item ? statusLabel(item.segment) : item.tender}
                    centerTop={n(dashboard.summary.customers).toLocaleString()}
                    centerBottom="customers"
                  />
                </article>
              </section>

              <section className={styles.bottomGrid}>
                <article className={styles.panelCard}>
                  <div className={styles.cardHeading}><h2>Recent Orders</h2><Link href="/staff/finance?tab=accounts">View all</Link></div>
                  <div className={styles.tableWrap}>
                    <table className={styles.orderTable}>
                      <thead><tr><th>Order ID</th><th>Customer</th><th>Amount</th><th>Status</th><th>Date</th></tr></thead>
                      <tbody>
                        {dashboard.recent_orders.length === 0 ? <tr><td colSpan={5} className={styles.emptyTable}>No posted orders in this period.</td></tr> : null}
                        {dashboard.recent_orders.map((order) => (
                          <tr key={order.id}>
                            <td><Link href={`/staff/finance?tab=accounts&overview_q=${encodeURIComponent(order.document_number || order.id)}`}>{order.document_number || "Invoice"}</Link></td>
                            <td>{order.customer}</td>
                            <td>{order.currency} {n(order.total).toLocaleString(undefined, { maximumFractionDigits: 2 })}</td>
                            <td><span className={styles.statusPill}>{statusLabel(order.state)}</span></td>
                            <td>{new Date(order.posted_at).toLocaleDateString()}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </article>

                <article className={styles.panelCard}>
                  <div className={styles.cardHeading}><h2>Inventory Snapshot</h2><Link href="/staff/warehouse/master-stock">View all</Link></div>
                  <div className={styles.inventoryGrid}>
                    {[
                      ["Out of Stock", n(dashboard.inventory.out_of_stock), "#ef6b5e"],
                      ["Low Stock", n(dashboard.inventory.low_stock), "#e9b53f"],
                      ["In Stock", n(dashboard.inventory.in_stock), "#2cb797"],
                      ["Quarantine", n(dashboard.inventory.quarantine), "#7d8a9c"],
                    ].map(([label, value, color]) => (
                      <Link key={String(label)} href="/staff/warehouse/master-stock" className={styles.inventoryTile}>
                        <i style={{ borderColor: String(color), color: String(color) }}><Package size={20} /></i>
                        <span><small>{label}</small><strong>{Number(value).toLocaleString()}</strong></span>
                      </Link>
                    ))}
                  </div>
                </article>
              </section>
            </>
          ) : null}
        </main>
      </div>
    </div>
  );
}
