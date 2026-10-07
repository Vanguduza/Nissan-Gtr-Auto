"use client";

import { useState } from "react";
import styles from "@/components/account.module.css";
import { money, type HourRow } from "@/lib/staff-dashboard";

/** One series per currency: a single hue (validated against the panel surface). */
const BAR = "#2f6fb0";
const W = 900;
const H = 200;
const PAD = { top: 22, right: 8, bottom: 22, left: 86 };

function niceMax(v: number): number {
  if (v <= 0) return 1;
  const p = 10 ** Math.floor(Math.log10(v));
  return [1, 2, 2.5, 5, 10].map((m) => m * p).find((x) => x >= v) ?? v;
}

/**
 * Sales by hour (Harare) for one currency: bars anchored to the baseline, a hover tooltip per bar,
 * and the same numbers as a table for screen readers.
 */
export function HourlySalesChart({ rows, currency }: { rows: HourRow[]; currency: string }) {
  const [hover, setHover] = useState<number | null>(null);
  const data = rows.filter((r) => r.currency === currency);
  if (data.length === 0) return <p className={styles.muted}>No sales yet.</p>;
  // Trading hours, widened to any hour that had sales.
  const first = Math.min(7, ...data.map((r) => r.hour));
  const last = Math.max(18, ...data.map((r) => r.hour));
  const hours = Array.from({ length: last - first + 1 }, (_, i) => first + i);
  const byHour = new Map(data.map((r) => [r.hour, r.total]));
  const max = niceMax(Math.max(...data.map((r) => r.total)));
  const plotW = W - PAD.left - PAD.right;
  const plotH = H - PAD.top - PAD.bottom;
  const slot = plotW / hours.length;
  const barW = Math.max(4, slot - 2); // 2px surface gap between bars
  const y = (v: number) => PAD.top + plotH - (v / max) * plotH;
  const peak = data.reduce((a, b) => (b.total > a.total ? b : a));
  const hovered = hover == null ? null : { hour: hover, total: byHour.get(hover) ?? 0 };

  return (
    <figure style={{ margin: 0 }}>
      <div style={{ position: "relative" }}>
        <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label={`Sales by hour in ${currency}; busiest ${peak.hour}:00 with ${money(peak.total, currency)}`}>
          {/* Recessive grid: half and full scale. */}
          {[0.5, 1].map((f) => (
            <g key={f}>
              <line x1={PAD.left} x2={W - PAD.right} y1={y(max * f)} y2={y(max * f)} stroke="var(--staff-border, #e5e7eb)" strokeWidth={1} />
              <text x={PAD.left - 8} y={y(max * f) + 4} textAnchor="end" fontSize={11} fill="#6b7280">
                {money(max * f, currency)}
              </text>
            </g>
          ))}
          <line x1={PAD.left} x2={W - PAD.right} y1={PAD.top + plotH} y2={PAD.top + plotH} stroke="#9ca3af" strokeWidth={1} />
          {hours.map((h, i) => {
            const v = byHour.get(h) ?? 0;
            const x = PAD.left + i * slot + (slot - barW) / 2;
            const top = y(v);
            const height = PAD.top + plotH - top;
            const r = Math.min(4, barW / 2, height);
            return (
              <g key={h} onMouseEnter={() => setHover(h)} onMouseLeave={() => setHover(null)}>
                {/* Hit target: the whole column, bigger than the bar. */}
                <rect x={PAD.left + i * slot} y={PAD.top} width={slot} height={plotH} fill="transparent" />
                {v > 0 ? (
                  <path
                    d={`M${x},${PAD.top + plotH} V${top + r} Q${x},${top} ${x + r},${top} H${x + barW - r} Q${x + barW},${top} ${x + barW},${top + r} V${PAD.top + plotH} Z`}
                    fill={BAR}
                    opacity={hover == null || hover === h ? 1 : 0.55}
                  />
                ) : null}
                {i % 2 === 0 || hours.length <= 12 ? (
                  <text x={x + barW / 2} y={H - 6} textAnchor="middle" fontSize={11} fill="#6b7280">
                    {String(h).padStart(2, "0")}
                  </text>
                ) : null}
              </g>
            );
          })}
          {/* One direct label: the busiest hour. */}
          <text
            x={PAD.left + hours.indexOf(peak.hour) * slot + slot / 2}
            y={y(peak.total) - 5}
            textAnchor="middle"
            fontSize={12}
            fontWeight={700}
            fill="var(--gtr-steel)"
          >
            {money(peak.total, currency)}
          </text>
        </svg>
        {hovered ? (
          <div
            role="status"
            style={{
              position: "absolute",
              top: 0,
              left: `${((PAD.left + hours.indexOf(hovered.hour) * slot + slot / 2) / W) * 100}%`,
              transform: "translate(-50%, -100%)",
              padding: "4px 8px",
              borderRadius: 6,
              background: "var(--gtr-steel)",
              color: "var(--gtr-white)",
              fontSize: 12,
              whiteSpace: "nowrap",
              pointerEvents: "none",
            }}
          >
            {String(hovered.hour).padStart(2, "0")}:00–{String(hovered.hour + 1).padStart(2, "0")}:00 · {money(hovered.total, currency)}
          </div>
        ) : null}
      </div>
      <details>
        <summary className={styles.muted} style={{ cursor: "pointer" }}>
          Show as table
        </summary>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Hour</th>
              <th style={{ textAlign: "right" }}>Sales</th>
            </tr>
          </thead>
          <tbody>
            {data.map((r) => (
              <tr key={r.hour}>
                <td>{String(r.hour).padStart(2, "0")}:00</td>
                <td style={{ textAlign: "right" }}>{money(r.total, currency)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}
