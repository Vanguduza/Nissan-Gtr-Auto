"use client";

import { useCallback, useEffect, useState } from "react";
import styles from "@/components/account.module.css";
import { createWebClient } from "@/lib/supabase";

type Row = {
  stock_item_id: string;
  oem_part_number: string;
  description: string | null;
  qty_total: number;
  qty_wh1: number;
  qty_wh2: number;
};

export function MasterStockPanel() {
  const [rows, setRows] = useState<Row[]>([]);
  const [query, setQuery] = useState("");
  const [message, setMessage] = useState<string | null>(null);

  const load = useCallback(async (q: string) => {
    const client = createWebClient();
    if (!client) {
      setMessage("Supabase is not configured.");
      return;
    }
    const { data, error } = await client.rpc("list_master_stock", {
      p_limit: 200,
      p_query: q.trim() || undefined,
    });
    if (error) {
      setMessage(error.message);
      return;
    }
    setRows((data ?? []) as Row[]);
  }, []);

  useEffect(() => {
    void load("");
  }, [load]);

  return (
    <div className={styles.panel}>
      <h2 className={styles.title}>Master stock</h2>
      <p className={styles.lede}>
        Totals across warehouses with WH1 (receiving) and WH2 (storefloor)
        columns. Move WH1 → WH2 via approved stock transfers.
      </p>
      {message ? <p className={styles.formStatus}>{message}</p> : null}
      <form
        className={styles.form}
        onSubmit={(e) => {
          e.preventDefault();
          void load(query);
        }}
      >
        <label className={styles.field}>
          Search OEM / description
          <input value={query} onChange={(e) => setQuery(e.target.value)} />
        </label>
        <div className={styles.formActions}>
          <button type="submit" className={styles.btn}>
            Search
          </button>
        </div>
      </form>
      <div className={styles.tableWrap}>
        <table className={styles.table}>
          <thead>
            <tr>
              <th>OEM</th>
              <th>Description</th>
              <th>Total</th>
              <th>WH1</th>
              <th>WH2</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.stock_item_id}>
                <td>{r.oem_part_number}</td>
                <td>{r.description ?? "—"}</td>
                <td>{Number(r.qty_total)}</td>
                <td>{Number(r.qty_wh1)}</td>
                <td>{Number(r.qty_wh2)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
