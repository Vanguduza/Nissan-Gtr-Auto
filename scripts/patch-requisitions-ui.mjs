import fs from "node:fs";

const path = "apps/web/components/staff-finance-panel.tsx";
let s = fs.readFileSync(path, "utf8");
const start = s.indexOf('      {tab === "requisitions" ? (');
const end = s.indexOf('      {tab === "reports" ? (');
if (start < 0 || end < 0) {
  console.error("markers", start, end);
  process.exit(1);
}

const replacement = `      {tab === "requisitions" ? (
        <fieldset className={styles.fieldset}>
          <legend className={styles.legend}>Finance requisitions</legend>
          <p className={styles.muted} style={{ marginBottom: "0.75rem" }}>
            Petty cash and payment requests with expense line items: draft →
            submit → finance approve → disburse (posts multi-line JE). Cannot
            skip approval. Disbursement links <code>journal_entry_id</code>;{" "}
            <code>payment_entry_id</code> stays reserved for a future AP payment
            desk. Procurement PO approval is a separate epic — see{" "}
            <Link href="/procurement">procurement</Link> for PO submit only.
          </p>
          <form onSubmit={(e) => void onCreateRequisition(e)}>
            <div className={styles.formGrid}>
              <label className={styles.field}>
                Type
                <select
                  value={reqType}
                  onChange={(e) =>
                    setReqType(e.target.value as FinanceRequisitionType)
                  }
                  disabled={busy}
                >
                  <option value="petty_cash">Petty cash (1110)</option>
                  <option value="payment">Payment (bank 1100)</option>
                </select>
              </label>
              <label className={styles.field}>
                Currency
                <select
                  value={reqCurrency}
                  onChange={(e) => {
                    const c = e.target.value as CurrencyCode;
                    setReqCurrency(c);
                    if (c === "ZIG") setReqRate(officialRate);
                  }}
                  disabled={busy}
                >
                  <option value="USD">USD</option>
                  <option value="ZIG">ZIG</option>
                </select>
              </label>
              {reqCurrency === "ZIG" ? (
                <label className={styles.field}>
                  ZiG exchange rate
                  <input
                    value={reqRate}
                    onChange={(e) => setReqRate(e.target.value)}
                    disabled={busy}
                    inputMode="decimal"
                  />
                </label>
              ) : null}
              <label className={styles.field}>
                Payee
                <input
                  value={reqPayee}
                  onChange={(e) => setReqPayee(e.target.value)}
                  disabled={busy}
                  placeholder="Optional"
                />
              </label>
              <label className={styles.field} style={{ gridColumn: "1 / -1" }}>
                Memo
                <input
                  value={reqMemo}
                  onChange={(e) => setReqMemo(e.target.value)}
                  disabled={busy}
                  placeholder="What is this for?"
                />
              </label>
            </div>

            <p className={styles.muted} style={{ margin: "0.75rem 0 0.35rem" }}>
              Expense lines (total{" "}
              {reqLines
                .reduce((sum, l) => sum + (Number(l.amount) || 0), 0)
                .toFixed(2)}{" "}
              {reqCurrency})
            </p>
            {reqLines.map((line, idx) => (
              <div
                key={\`req-line-\${idx}\`}
                className={styles.formGrid}
                style={{ marginBottom: "0.5rem" }}
              >
                <label className={styles.field}>
                  Expense account
                  <select
                    value={line.expenseAccountCode}
                    onChange={(e) => {
                      const next = [...reqLines];
                      const cur = next[idx];
                      if (!cur) return;
                      next[idx] = {
                        ...cur,
                        expenseAccountCode: e.target.value,
                      };
                      setReqLines(next);
                    }}
                    disabled={busy}
                  >
                    {boot.accounts.map((a) => (
                      <option
                        key={\`req-exp-\${idx}-\${a.code}\`}
                        value={a.code}
                      >
                        {a.code} — {a.name}
                      </option>
                    ))}
                  </select>
                </label>
                <label className={styles.field}>
                  Amount ({reqCurrency})
                  <input
                    value={line.amount}
                    onChange={(e) => {
                      const next = [...reqLines];
                      const cur = next[idx];
                      if (!cur) return;
                      next[idx] = { ...cur, amount: e.target.value };
                      setReqLines(next);
                    }}
                    disabled={busy}
                    inputMode="decimal"
                  />
                </label>
                <label className={styles.field}>
                  Line description
                  <input
                    value={line.description}
                    onChange={(e) => {
                      const next = [...reqLines];
                      const cur = next[idx];
                      if (!cur) return;
                      next[idx] = { ...cur, description: e.target.value };
                      setReqLines(next);
                    }}
                    disabled={busy}
                    placeholder="Optional"
                  />
                </label>
                <div
                  className={styles.formActions}
                  style={{ alignItems: "end" }}
                >
                  <button
                    type="button"
                    className={styles.btnGhost}
                    disabled={busy || reqLines.length <= 1}
                    onClick={() =>
                      setReqLines(reqLines.filter((_, i) => i !== idx))
                    }
                  >
                    Remove
                  </button>
                </div>
              </div>
            ))}
            <div className={styles.formActions}>
              <button
                type="button"
                className={styles.btnGhost}
                disabled={busy}
                onClick={() =>
                  setReqLines([
                    ...reqLines,
                    {
                      expenseAccountCode:
                        reqLines[reqLines.length - 1]?.expenseAccountCode ||
                        "5300",
                      amount: "",
                      description: "",
                    },
                  ])
                }
              >
                Add line
              </button>
              <button type="submit" className={styles.btnGhost} disabled={busy}>
                Create draft
              </button>
              <button
                type="button"
                className={styles.btnGhost}
                disabled={busy}
                onClick={() => void loadRequisitions()}
              >
                Refresh
              </button>
            </div>
          </form>

          <label className={styles.field} style={{ marginTop: "1rem" }}>
            Reject reason (for reject action)
            <input
              value={reqRejectReason}
              onChange={(e) => setReqRejectReason(e.target.value)}
              disabled={busy}
              placeholder="Required when rejecting"
            />
          </label>

          {requisitions.length === 0 ? (
            <p className={styles.muted} style={{ marginTop: "0.75rem" }}>
              No requisitions yet.
            </p>
          ) : (
            <div style={{ overflowX: "auto", marginTop: "0.75rem" }}>
              <table className={styles.table}>
                <thead>
                  <tr>
                    <th>Doc</th>
                    <th>Type</th>
                    <th>Status</th>
                    <th>Amount</th>
                    <th>Lines</th>
                    <th>Payee / memo</th>
                    <th>Cash / JE</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {requisitions.map((r) => (
                    <Fragment key={r.id}>
                      <tr>
                        <td>{r.document_number ?? r.id.slice(0, 8)}</td>
                        <td>{r.req_type}</td>
                        <td>{r.status}</td>
                        <td>
                          {Number(r.amount).toFixed(2)} {r.currency}
                          {r.currency === "ZIG" &&
                          r.exchange_rate_applied != null
                            ? \` @ \${r.exchange_rate_applied}\`
                            : ""}
                        </td>
                        <td>
                          <button
                            type="button"
                            className={styles.btnGhost}
                            disabled={busy}
                            onClick={() =>
                              setExpandedReqId(
                                expandedReqId === r.id ? null : r.id,
                              )
                            }
                          >
                            {r.lines.length} line
                            {r.lines.length === 1 ? "" : "s"}
                          </button>
                        </td>
                        <td>
                          {r.payee || "—"}
                          {r.memo ? \` · \${r.memo}\` : ""}
                          {r.rejection_reason
                            ? \` · reject: \${r.rejection_reason}\`
                            : ""}
                        </td>
                        <td>
                          Cr {r.cash_account_code}
                          {r.journal_entry_id
                            ? \` · JE \${r.journal_entry_id.slice(0, 8)}…\`
                            : ""}
                        </td>
                        <td>
                          <div
                            className={styles.formActions}
                            style={{ flexWrap: "wrap", margin: 0 }}
                          >
                            {r.status === "draft" ? (
                              <>
                                <button
                                  type="button"
                                  className={styles.btnGhost}
                                  disabled={busy}
                                  onClick={() =>
                                    void onReqAction("submit", r.id)
                                  }
                                >
                                  Submit
                                </button>
                                <button
                                  type="button"
                                  className={styles.btnGhost}
                                  disabled={busy}
                                  onClick={() =>
                                    void onReqAction("cancel", r.id)
                                  }
                                >
                                  Cancel
                                </button>
                              </>
                            ) : null}
                            {r.status === "submitted" ? (
                              <>
                                <button
                                  type="button"
                                  className={styles.btnGhost}
                                  disabled={busy}
                                  onClick={() =>
                                    void onReqAction("approve", r.id)
                                  }
                                >
                                  Approve
                                </button>
                                <button
                                  type="button"
                                  className={styles.btnGhost}
                                  disabled={busy || !reqRejectReason.trim()}
                                  onClick={() =>
                                    void onReqAction("reject", r.id)
                                  }
                                >
                                  Reject
                                </button>
                                <button
                                  type="button"
                                  className={styles.btnGhost}
                                  disabled={busy}
                                  onClick={() =>
                                    void onReqAction("cancel", r.id)
                                  }
                                >
                                  Cancel
                                </button>
                              </>
                            ) : null}
                            {r.status === "approved" ? (
                              <>
                                <button
                                  type="button"
                                  className={styles.btnGhost}
                                  disabled={busy}
                                  onClick={() =>
                                    void onReqAction("disburse", r.id)
                                  }
                                >
                                  Disburse
                                </button>
                                <button
                                  type="button"
                                  className={styles.btnGhost}
                                  disabled={busy || !reqRejectReason.trim()}
                                  onClick={() =>
                                    void onReqAction("reject", r.id)
                                  }
                                >
                                  Reject
                                </button>
                              </>
                            ) : null}
                            {r.status === "rejected" ? (
                              <button
                                type="button"
                                className={styles.btnGhost}
                                disabled={busy}
                                onClick={() =>
                                  void onReqAction("cancel", r.id)
                                }
                              >
                                Cancel
                              </button>
                            ) : null}
                          </div>
                        </td>
                      </tr>
                      {expandedReqId === r.id ? (
                        <tr>
                          <td colSpan={8}>
                            <table className={styles.table}>
                              <thead>
                                <tr>
                                  <th>#</th>
                                  <th>Expense</th>
                                  <th>Amount</th>
                                  <th>Description</th>
                                </tr>
                              </thead>
                              <tbody>
                                {r.lines.map((l) => (
                                  <tr key={l.id}>
                                    <td>{l.line_no}</td>
                                    <td>{l.expense_account_code}</td>
                                    <td>
                                      {l.amount.toFixed(2)} {r.currency}
                                    </td>
                                    <td>{l.description || "—"}</td>
                                  </tr>
                                ))}
                              </tbody>
                            </table>
                          </td>
                        </tr>
                      ) : null}
                    </Fragment>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </fieldset>
      ) : null}

`;

s = s.slice(0, start) + replacement + s.slice(end);
fs.writeFileSync(path, s);
console.log("panel updated");
