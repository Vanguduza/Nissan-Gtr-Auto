import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  fetchZigExchangeRate,
  requireSession,
  zigExchangeRate,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export { requireSession, zigExchangeRate, fetchZigExchangeRate };

export type CurrencyCode = Database["public"]["Enums"]["currency_code"];
export type PaymentTender = Database["public"]["Enums"]["payment_tender"];

type FinanceClient = SupabaseClient<Database>;

export type JournalEntryOption = {
  id: string;
  document_number: string | null;
  status: string;
  entry_date: string;
  description: string | null;
  currency: CurrencyCode;
  exchange_rate_applied: number | null;
  posted_at: string;
  is_reversal: boolean;
  reverses_entry_id: string | null;
};

export type AccountingPeriodOption = {
  id: string;
  period_start: string;
  period_end: string;
  label: string;
  locked_at: string | null;
};

export type BankReconStatus = Database["public"]["Enums"]["bank_recon_status"];

export type BankStatementOption = {
  id: string;
  account_code: string;
  currency: CurrencyCode;
  statement_date: string;
  opening_balance: number;
  closing_balance: number;
  document_number: string | null;
  created_at: string;
};

export type BankStatementLineOption = {
  id: string;
  statement_id: string;
  line_date: string;
  description: string | null;
  amount: number;
  status: BankReconStatus;
};

export type BankReconMatchOption = {
  id: string;
  statement_line_id: string;
  journal_entry_line_id: string;
  matched_at: string;
};

export type JournalLineOption = {
  id: string;
  journal_entry_id: string;
  account_code: string;
  debit: number;
  credit: number;
  currency: CurrencyCode;
};

export type PaymentEntryOption = {
  id: string;
  document_number: string | null;
  status: string;
  amount: number;
  currency: CurrencyCode;
  tender: PaymentTender;
  customer_id: string;
  created_at: string;
  customers?: { display_name: string } | null;
};

export type CustomerOption = {
  id: string;
  display_name: string;
};

export type AccountOption = {
  code: string;
  name: string;
  account_type: string;
  is_active: boolean;
};

export type JournalLineInput = {
  account_code: string;
  debit: number;
  credit: number;
  currency: CurrencyCode;
};

export type PnLRow = {
  account_code: string;
  account_name: string;
  account_type: string;
  amount: number;
  amount_usd: number;
};

export type BalanceSheetRow = {
  account_code: string;
  account_name: string;
  account_type: string;
  balance: number;
  balance_usd: number;
};

export type CashFlowRow = {
  section: string;
  label: string;
  amount_usd: number;
};

export type TrialBalanceRow = {
  account_code: string;
  account_name: string;
  account_type: string;
  debit: number;
  credit: number;
  debit_usd: number;
  credit_usd: number;
};

export type OpenInvoiceOption = {
  id: string;
  document_number: string | null;
  currency: CurrencyCode;
  total: number;
  amount_paid: number;
  open_balance: number;
};

export type ArAgingBucket = {
  bucket: string;
  currency: string;
  invoice_count: number;
  open_amount: number;
};

export type ArAgingSnapshot = {
  as_of: string | null;
  customers_with_open_balance: number;
  customer_open_balance_by_currency: {
    currency: string;
    customer_count?: number;
    open_balance: number;
  }[];
  invoice_aging_buckets: ArAgingBucket[];
};

/** Client-side CSV download (no fiscal QR). Neutralizes spreadsheet formula injection. */
export function downloadCsv(
  filename: string,
  headers: string[],
  rows: (string | number)[][],
): void {
  const esc = (v: string | number) => {
    let s = String(v);
    // Prevent Excel/Sheets treating cells as formulas (=, +, -, @, tab/CR).
    if (/^[=+\-@\t\r]/.test(s)) {
      s = `'${s}`;
    }
    return /[",\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s;
  };
  const body = [
    headers.map(esc).join(","),
    ...rows.map((r) => r.map(esc).join(",")),
  ].join("\n");
  const blob = new Blob([body], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}

export async function listChartAccounts(
  client: FinanceClient,
): Promise<StorefrontResult<AccountOption[]>> {
  const { data, error } = await client
    .from("chart_of_accounts")
    .select("code, name, account_type, is_active")
    .eq("is_active", true)
    .order("code")
    .limit(200);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as AccountOption[]) ?? [] };
}

export async function listJournalEntries(
  client: FinanceClient,
): Promise<StorefrontResult<JournalEntryOption[]>> {
  const { data, error } = await client
    .from("journal_entries")
    .select(
      "id, document_number, status, entry_date, description, currency, exchange_rate_applied, posted_at, is_reversal, reverses_entry_id",
    )
    .order("posted_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as JournalEntryOption[]) ?? [] };
}

export async function listDraftPayments(
  client: FinanceClient,
): Promise<StorefrontResult<PaymentEntryOption[]>> {
  const { data, error } = await client
    .from("payment_entries")
    .select(
      "id, document_number, status, amount, currency, tender, customer_id, created_at, customers ( display_name )",
    )
    .eq("status", "draft")
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  const rows = (data ?? []).map((row) => {
    const r = row as {
      id: string;
      document_number: string | null;
      status: string;
      amount: number;
      currency: CurrencyCode;
      tender: PaymentTender;
      customer_id: string;
      created_at: string;
      customers?: { display_name: string } | { display_name: string }[] | null;
    };
    const customers = Array.isArray(r.customers)
      ? (r.customers[0] ?? null)
      : (r.customers ?? null);
    return {
      id: r.id,
      document_number: r.document_number,
      status: r.status,
      amount: r.amount,
      currency: r.currency,
      tender: r.tender,
      customer_id: r.customer_id,
      created_at: r.created_at,
      customers,
    } satisfies PaymentEntryOption;
  });
  return { ok: true, data: rows };
}

export async function searchCustomers(
  client: FinanceClient,
  query: string,
): Promise<StorefrontResult<CustomerOption[]>> {
  const q = query.trim();
  if (q.length < 2) return { ok: true, data: [] };
  const uuidLike =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(q);
  if (uuidLike) {
    const { data, error } = await client
      .from("customers")
      .select("id, display_name")
      .eq("id", q)
      .limit(1);
    if (error) return { ok: false, error: error.message };
    return { ok: true, data: (data as CustomerOption[]) ?? [] };
  }
  const { data, error } = await client
    .from("customers")
    .select("id, display_name")
    .ilike("display_name", `%${q}%`)
    .order("display_name")
    .limit(20);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as CustomerOption[]) ?? [] };
}

export async function createJournalDraft(
  client: FinanceClient,
  args: {
    entryDate: string;
    description: string;
    currency: CurrencyCode;
    exchangeRate?: number;
    lines: JournalLineInput[];
  },
): Promise<StorefrontResult<string>> {
  const exchangeRate =
    args.currency === "ZIG"
      ? (args.exchangeRate ?? zigExchangeRate())
      : (args.exchangeRate ?? 1);

  const { data, error } = await client.rpc("create_journal_draft", {
    p_entry_date: args.entryDate,
    p_description: args.description,
    p_currency: args.currency,
    p_exchange_rate: exchangeRate,
    p_lines: args.lines,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_journal_draft returned no id." };
  return { ok: true, data };
}

export async function postJournal(
  client: FinanceClient,
  entryId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("post_journal", {
    p_entry_id: entryId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "post_journal returned no id." };
  return { ok: true, data };
}

export async function reportProfitAndLoss(
  client: FinanceClient,
  args: { from: string; to: string; currency?: CurrencyCode },
): Promise<StorefrontResult<PnLRow[]>> {
  const { data, error } = await client.rpc("report_profit_and_loss", {
    p_from: args.from,
    p_to: args.to,
    p_currency: args.currency,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as PnLRow[]) ?? [] };
}

export async function reportBalanceSheet(
  client: FinanceClient,
  args: { asOf?: string; currency?: CurrencyCode },
): Promise<StorefrontResult<BalanceSheetRow[]>> {
  const { data, error } = await client.rpc("report_balance_sheet", {
    p_as_of: args.asOf,
    p_currency: args.currency,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as BalanceSheetRow[]) ?? [] };
}

export async function reportCashFlow(
  client: FinanceClient,
  args: { from: string; to: string; currency?: CurrencyCode },
): Promise<StorefrontResult<CashFlowRow[]>> {
  const { data, error } = await client.rpc("report_cash_flow", {
    p_from: args.from,
    p_to: args.to,
    p_currency: args.currency,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as CashFlowRow[]) ?? [] };
}

export async function reportTrialBalance(
  client: FinanceClient,
  args: { asOf?: string; currency?: CurrencyCode },
): Promise<StorefrontResult<TrialBalanceRow[]>> {
  const { data, error } = await client.rpc("report_trial_balance", {
    p_as_of: args.asOf,
    p_currency: args.currency,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as TrialBalanceRow[]) ?? [] };
}

export async function fetchArAgingSnapshot(
  client: FinanceClient,
): Promise<StorefrontResult<ArAgingSnapshot>> {
  const { data, error } = await client.rpc("kpi_ar_aging_snapshot");
  if (error) return { ok: false, error: error.message };
  const raw = (data ?? {}) as Record<string, unknown>;
  const bucketsRaw = Array.isArray(raw.invoice_aging_buckets)
    ? raw.invoice_aging_buckets
    : [];
  const byCurRaw = Array.isArray(raw.customer_open_balance_by_currency)
    ? raw.customer_open_balance_by_currency
    : [];
  return {
    ok: true,
    data: {
      as_of: typeof raw.as_of === "string" ? raw.as_of : null,
      customers_with_open_balance: Number(raw.customers_with_open_balance ?? 0),
      customer_open_balance_by_currency: byCurRaw.map((row) => {
        const r = row as Record<string, unknown>;
        return {
          currency: String(r.currency ?? ""),
          customer_count:
            r.customer_count != null ? Number(r.customer_count) : undefined,
          open_balance: Number(r.open_balance ?? 0),
        };
      }),
      invoice_aging_buckets: bucketsRaw.map((row) => {
        const r = row as Record<string, unknown>;
        return {
          bucket: String(r.bucket ?? ""),
          currency: String(r.currency ?? ""),
          invoice_count: Number(r.invoice_count ?? 0),
          open_amount: Number(r.open_amount ?? 0),
        };
      }),
    },
  };
}

/** Open posted invoices for a customer (same-currency allocate hints). */
export async function listOpenInvoicesForCustomer(
  client: FinanceClient,
  args: { customerId: string; currency?: CurrencyCode },
): Promise<StorefrontResult<OpenInvoiceOption[]>> {
  let q = client
    .from("sales_invoices")
    .select("id, document_number, currency, total, amount_paid")
    .eq("customer_id", args.customerId)
    .eq("doc_type", "invoice")
    .eq("status", "posted")
    .order("posted_at", { ascending: false })
    .limit(40);
  if (args.currency) q = q.eq("currency", args.currency);
  const { data, error } = await q;
  if (error) return { ok: false, error: error.message };
  const rows = ((data ?? []) as {
    id: string;
    document_number: string | null;
    currency: CurrencyCode;
    total: number;
    amount_paid: number;
  }[])
    .map((r) => ({
      id: r.id,
      document_number: r.document_number,
      currency: r.currency,
      total: Number(r.total),
      amount_paid: Number(r.amount_paid),
      open_balance: Number(r.total) - Number(r.amount_paid),
    }))
    .filter((r) => r.open_balance > 0);
  return { ok: true, data: rows };
}

export async function createPaymentEntry(
  client: FinanceClient,
  args: {
    customerId: string;
    amount: number;
    currency: CurrencyCode;
    tender: PaymentTender;
    exchangeRate?: number;
    notes?: string;
  },
): Promise<StorefrontResult<string>> {
  const exchangeRate =
    args.currency === "ZIG"
      ? (args.exchangeRate ?? zigExchangeRate())
      : (args.exchangeRate ?? 1);

  const { data, error } = await client.rpc("create_payment_entry", {
    p_customer_id: args.customerId,
    p_amount: args.amount,
    p_currency: args.currency,
    p_exchange_rate: exchangeRate,
    p_tender: args.tender,
    p_notes: args.notes || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "create_payment_entry returned no id." };
  return { ok: true, data };
}

export async function allocatePayment(
  client: FinanceClient,
  args: {
    paymentEntryId: string;
    allocations: { sales_invoice_id: string; amount: number }[];
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("allocate_payment", {
    p_payment_entry_id: args.paymentEntryId,
    p_allocations: args.allocations,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "allocate_payment returned no id." };
  return { ok: true, data };
}

export async function postPaymentEntry(
  client: FinanceClient,
  paymentEntryId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("post_payment_entry", {
    p_payment_entry_id: paymentEntryId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "post_payment_entry returned no id." };
  return { ok: true, data };
}

export async function cancelPaymentEntry(
  client: FinanceClient,
  paymentEntryId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("cancel_payment_entry", {
    p_payment_entry_id: paymentEntryId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "cancel_payment_entry returned no id." };
  return { ok: true, data };
}

export async function reverseJournal(
  client: FinanceClient,
  args: { entryId: string; reason?: string },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("reverse_journal", {
    p_entry_id: args.entryId,
    p_description: args.reason || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "reverse_journal returned no id." };
  return { ok: true, data };
}

export async function listAccountingPeriods(
  client: FinanceClient,
): Promise<StorefrontResult<AccountingPeriodOption[]>> {
  const { data, error } = await client
    .from("accounting_periods")
    .select("id, period_start, period_end, label, locked_at")
    .order("period_start", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as AccountingPeriodOption[]) ?? [] };
}

export async function createAccountingPeriod(
  client: FinanceClient,
  args: { periodStart: string; periodEnd: string; label: string },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client
    .from("accounting_periods")
    .insert({
      period_start: args.periodStart,
      period_end: args.periodEnd,
      label: args.label,
    })
    .select("id")
    .single();
  if (error) return { ok: false, error: error.message };
  if (!data?.id) return { ok: false, error: "Period insert returned no id." };
  return { ok: true, data: data.id };
}

export async function lockAccountingPeriod(
  client: FinanceClient,
  periodId: string,
): Promise<StorefrontResult<true>> {
  const { error } = await client.rpc("lock_accounting_period", {
    p_period_id: periodId,
  });
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: true };
}

export async function listBankStatements(
  client: FinanceClient,
): Promise<StorefrontResult<BankStatementOption[]>> {
  const { data, error } = await client
    .from("bank_statements")
    .select(
      "id, account_code, currency, statement_date, opening_balance, closing_balance, document_number, created_at",
    )
    .order("statement_date", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as BankStatementOption[]) ?? [] };
}

export async function listBankStatementLines(
  client: FinanceClient,
  statementId: string,
): Promise<StorefrontResult<BankStatementLineOption[]>> {
  const { data, error } = await client
    .from("bank_statement_lines")
    .select("id, statement_id, line_date, description, amount, status")
    .eq("statement_id", statementId)
    .order("line_date", { ascending: true })
    .limit(100);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as BankStatementLineOption[]) ?? [] };
}

export async function listBankReconMatches(
  client: FinanceClient,
  statementLineIds: string[],
): Promise<StorefrontResult<BankReconMatchOption[]>> {
  if (!statementLineIds.length) return { ok: true, data: [] };
  const { data, error } = await client
    .from("bank_recon_matches")
    .select("id, statement_line_id, journal_entry_line_id, matched_at")
    .in("statement_line_id", statementLineIds)
    .order("matched_at", { ascending: false })
    .limit(100);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as BankReconMatchOption[]) ?? [] };
}

export async function listJournalLinesForAccount(
  client: FinanceClient,
  accountCode: string,
): Promise<StorefrontResult<JournalLineOption[]>> {
  const { data, error } = await client
    .from("journal_entry_lines")
    .select("id, journal_entry_id, account_code, debit, credit, currency")
    .eq("account_code", accountCode)
    .order("id", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as JournalLineOption[]) ?? [] };
}

/** Import = insert statement header + optional first line (no import RPC exists). */
export async function importBankStatement(
  client: FinanceClient,
  args: {
    accountCode: string;
    currency: CurrencyCode;
    statementDate: string;
    openingBalance: number;
    closingBalance: number;
    documentNumber?: string;
    line?: { lineDate: string; description: string; amount: number };
  },
): Promise<StorefrontResult<string>> {
  const { data: stmt, error } = await client
    .from("bank_statements")
    .insert({
      account_code: args.accountCode,
      currency: args.currency,
      statement_date: args.statementDate,
      opening_balance: args.openingBalance,
      closing_balance: args.closingBalance,
      document_number: args.documentNumber || null,
    })
    .select("id")
    .single();
  if (error) return { ok: false, error: error.message };
  if (!stmt?.id) return { ok: false, error: "bank_statements insert returned no id." };

  if (args.line) {
    const { error: lineErr } = await client.from("bank_statement_lines").insert({
      statement_id: stmt.id,
      line_date: args.line.lineDate,
      description: args.line.description || null,
      amount: args.line.amount,
      status: "open",
    });
    if (lineErr) return { ok: false, error: lineErr.message };
  }
  return { ok: true, data: stmt.id };
}

export async function addBankStatementLine(
  client: FinanceClient,
  args: {
    statementId: string;
    lineDate: string;
    description: string;
    amount: number;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client
    .from("bank_statement_lines")
    .insert({
      statement_id: args.statementId,
      line_date: args.lineDate,
      description: args.description || null,
      amount: args.amount,
      status: "open",
    })
    .select("id")
    .single();
  if (error) return { ok: false, error: error.message };
  if (!data?.id) return { ok: false, error: "bank_statement_lines insert returned no id." };
  return { ok: true, data: data.id };
}

/** Match via table insert + status update (no match RPC). */
export async function matchBankLine(
  client: FinanceClient,
  args: { statementLineId: string; journalEntryLineId: string },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client
    .from("bank_recon_matches")
    .insert({
      statement_line_id: args.statementLineId,
      journal_entry_line_id: args.journalEntryLineId,
    })
    .select("id")
    .single();
  if (error) return { ok: false, error: error.message };
  if (!data?.id) return { ok: false, error: "bank_recon_matches insert returned no id." };

  const { error: updErr } = await client
    .from("bank_statement_lines")
    .update({ status: "matched" })
    .eq("id", args.statementLineId)
    .eq("status", "open");
  if (updErr) return { ok: false, error: updErr.message };

  return { ok: true, data: data.id };
}

export async function clearBankMatches(
  client: FinanceClient,
  matchIds: string[],
): Promise<StorefrontResult<number>> {
  const { data, error } = await client.rpc("clear_bank_matches", {
    p_match_ids: matchIds,
  });
  if (error) return { ok: false, error: error.message };
  const n = typeof data === "number" ? data : Number(data);
  if (!Number.isFinite(n)) {
    return { ok: false, error: "clear_bank_matches returned a non-numeric value." };
  }
  return { ok: true, data: n };
}

export type ZigExchangeRateRow = {
  id: string;
  rate_date: string;
  rate: number;
  notes: string | null;
  set_by: string | null;
  created_at: string;
};

export async function listZigExchangeRates(
  client: FinanceClient,
  limit = 30,
): Promise<StorefrontResult<ZigExchangeRateRow[]>> {
  const { data, error } = await client.rpc("list_zig_exchange_rates", {
    p_limit: limit,
  });
  if (error) return { ok: false, error: error.message };
  return {
    ok: true,
    data: ((data as ZigExchangeRateRow[]) ?? []).map((r) => ({
      ...r,
      rate: Number(r.rate),
    })),
  };
}

export async function setZigExchangeRate(
  client: FinanceClient,
  args: { rate: number; rateDate?: string; notes?: string },
): Promise<StorefrontResult<string>> {
  if (!(args.rate > 0)) {
    return { ok: false, error: "Rate must be > 0 (ZiG per 1 USD)." };
  }
  const { data, error } = await client.rpc("set_zig_exchange_rate", {
    p_rate: args.rate,
    p_rate_date: args.rateDate ?? undefined,
    p_notes: args.notes?.trim() || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "set_zig_exchange_rate returned no id." };
  return { ok: true, data: data as string };
}

// ---------------------------------------------------------------------------
// Period balances, GL register, petty replenish, finance requisitions
// (typed RPCs: 20260725250000 + 20260725260000 line items)
// ---------------------------------------------------------------------------

export type AccountRegisterRow = {
  entry_date: string;
  document_number: string | null;
  description: string | null;
  debit: number;
  credit: number;
  running_balance: number;
  currency: CurrencyCode;
  journal_entry_id: string;
};

export type AccountPeriodBalanceOption = {
  id: string;
  account_code: string;
  currency: CurrencyCode;
  period_start: string;
  period_end: string;
  opening_balance: number;
  closing_balance: number | null;
  status: Database["public"]["Enums"]["account_period_status"];
  notes: string | null;
  physical_count: number | null;
  variance: number | null;
  opened_at: string;
  closed_at: string | null;
};

export type FinanceRequisitionType =
  Database["public"]["Enums"]["finance_requisition_type"];
export type FinanceRequisitionStatus =
  Database["public"]["Enums"]["finance_requisition_status"];

export type FinanceRequisitionLineOption = {
  id: string;
  requisition_id: string;
  line_no: number;
  description: string | null;
  expense_account_code: string;
  amount: number;
};

export type FinanceRequisitionOption = {
  id: string;
  document_number: string | null;
  req_type: FinanceRequisitionType;
  status: FinanceRequisitionStatus;
  amount: number;
  currency: CurrencyCode;
  exchange_rate_applied: number | null;
  payee: string | null;
  memo: string | null;
  expense_account_code: string;
  cash_account_code: string;
  requested_by: string;
  submitted_at: string | null;
  approved_at: string | null;
  rejected_at: string | null;
  rejection_reason: string | null;
  disbursed_at: string | null;
  journal_entry_id: string | null;
  payment_entry_id: string | null;
  created_at: string;
  lines: FinanceRequisitionLineOption[];
};

export type FinanceRequisitionLineInput = {
  expenseAccountCode: string;
  amount: number;
  description?: string;
};

export async function reportAccountRegister(
  client: FinanceClient,
  args: {
    accountCode: string;
    from: string;
    to: string;
    currency?: CurrencyCode | null;
  },
): Promise<StorefrontResult<AccountRegisterRow[]>> {
  const { data, error } = await client.rpc("report_account_register", {
    p_account_code: args.accountCode,
    p_from: args.from,
    p_to: args.to,
    p_currency: args.currency ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  const rows = (data ?? []).map((r) => ({
    entry_date: String(r.entry_date),
    document_number: r.document_number ?? null,
    description: r.description ?? null,
    debit: Number(r.debit ?? 0),
    credit: Number(r.credit ?? 0),
    running_balance: Number(r.running_balance ?? 0),
    currency: r.currency,
    journal_entry_id: String(r.journal_entry_id),
  }));
  return { ok: true, data: rows };
}

export async function openAccountPeriod(
  client: FinanceClient,
  args: {
    accountCode: string;
    currency: CurrencyCode;
    periodStart: string;
    periodEnd: string;
    openingBalance?: number;
    notes?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("open_account_period", {
    p_account_code: args.accountCode,
    p_currency: args.currency,
    p_period_start: args.periodStart,
    p_period_end: args.periodEnd,
    p_opening_balance: args.openingBalance ?? 0,
    p_notes: args.notes?.trim() || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "open_account_period returned no id." };
  return { ok: true, data };
}

export async function closeAccountPeriod(
  client: FinanceClient,
  args: {
    periodId: string;
    physicalCount?: number | null;
    notes?: string;
  },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("close_account_period", {
    p_period_id: args.periodId,
    p_physical_count:
      args.physicalCount != null && Number.isFinite(args.physicalCount)
        ? args.physicalCount
        : undefined,
    p_notes: args.notes?.trim() || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "close_account_period returned no id." };
  return { ok: true, data };
}

export async function getOpenAccountPeriod(
  client: FinanceClient,
  args: { accountCode: string; currency: CurrencyCode },
): Promise<StorefrontResult<AccountPeriodBalanceOption | null>> {
  const { data, error } = await client
    .from("account_period_balances")
    .select(
      "id, account_code, currency, period_start, period_end, opening_balance, closing_balance, status, notes, physical_count, variance, opened_at, closed_at",
    )
    .eq("account_code", args.accountCode)
    .eq("currency", args.currency)
    .eq("status", "open")
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: true, data: null };
  return {
    ok: true,
    data: {
      ...data,
      opening_balance: Number(data.opening_balance),
      closing_balance:
        data.closing_balance != null ? Number(data.closing_balance) : null,
      physical_count:
        data.physical_count != null ? Number(data.physical_count) : null,
      variance: data.variance != null ? Number(data.variance) : null,
    },
  };
}

export async function pettyCashFundingAccountCode(
  client: FinanceClient,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("petty_cash_funding_account_code");
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: String(data ?? "1100") };
}

export async function computePettyCashReplenishAmount(
  client: FinanceClient,
  args?: { currency?: CurrencyCode; asOf?: string },
): Promise<StorefrontResult<number>> {
  const { data, error } = await client.rpc(
    "compute_petty_cash_replenish_amount",
    {
      p_currency: args?.currency ?? "USD",
      p_as_of: args?.asOf ?? undefined,
    },
  );
  if (error) return { ok: false, error: error.message };
  const n = Number(data ?? 0);
  if (!Number.isFinite(n)) {
    return {
      ok: false,
      error: "compute_petty_cash_replenish_amount returned non-numeric.",
    };
  }
  return { ok: true, data: n };
}

export async function listFinanceRequisitions(
  client: FinanceClient,
  limit = 40,
): Promise<StorefrontResult<FinanceRequisitionOption[]>> {
  const { data, error } = await client
    .from("finance_requisitions")
    .select(
      "id, document_number, req_type, status, amount, currency, exchange_rate_applied, payee, memo, expense_account_code, cash_account_code, requested_by, submitted_at, approved_at, rejected_at, rejection_reason, disbursed_at, journal_entry_id, payment_entry_id, created_at",
    )
    .order("created_at", { ascending: false })
    .limit(limit);
  if (error) return { ok: false, error: error.message };

  const headers = data ?? [];
  const ids = headers.map((r) => r.id);
  const linesByReq = new Map<string, FinanceRequisitionLineOption[]>();

  if (ids.length > 0) {
    const { data: lineRows, error: lineErr } = await client
      .from("finance_requisition_lines")
      .select(
        "id, requisition_id, line_no, description, expense_account_code, amount",
      )
      .in("requisition_id", ids)
      .order("line_no", { ascending: true });
    if (lineErr) return { ok: false, error: lineErr.message };
    for (const l of lineRows ?? []) {
      const list = linesByReq.get(l.requisition_id) ?? [];
      list.push({
        id: l.id,
        requisition_id: l.requisition_id,
        line_no: l.line_no,
        description: l.description ?? null,
        expense_account_code: l.expense_account_code,
        amount: Number(l.amount),
      });
      linesByReq.set(l.requisition_id, list);
    }
  }

  const rows: FinanceRequisitionOption[] = headers.map((r) => ({
    id: r.id,
    document_number: r.document_number,
    req_type: r.req_type,
    status: r.status,
    amount: Number(r.amount),
    currency: r.currency,
    exchange_rate_applied:
      r.exchange_rate_applied != null
        ? Number(r.exchange_rate_applied)
        : null,
    payee: r.payee,
    memo: r.memo,
    expense_account_code: r.expense_account_code,
    cash_account_code: r.cash_account_code,
    requested_by: r.requested_by,
    submitted_at: r.submitted_at,
    approved_at: r.approved_at,
    rejected_at: r.rejected_at,
    rejection_reason: r.rejection_reason,
    disbursed_at: r.disbursed_at,
    journal_entry_id: r.journal_entry_id,
    payment_entry_id: r.payment_entry_id,
    created_at: r.created_at,
    lines: linesByReq.get(r.id) ?? [],
  }));
  return { ok: true, data: rows };
}

export async function createFinanceRequisition(
  client: FinanceClient,
  args: {
    reqType: FinanceRequisitionType;
    amount: number;
    currency: CurrencyCode;
    payee?: string;
    memo?: string;
    expenseAccountCode?: string;
    cashAccountCode?: string;
    exchangeRate?: number;
    /** When set (2+ lines), replaces the seeded header line after create. */
    lines?: FinanceRequisitionLineInput[];
  },
): Promise<StorefrontResult<string>> {
  const exchangeRate =
    args.currency === "ZIG"
      ? (args.exchangeRate ?? zigExchangeRate())
      : (args.exchangeRate ?? 1);

  const seedAmount =
    args.lines && args.lines.length > 0
      ? args.lines.reduce((s, l) => s + l.amount, 0)
      : args.amount;
  const seedExpense =
    args.lines?.[0]?.expenseAccountCode ||
    args.expenseAccountCode ||
    "5300";

  const { data, error } = await client.rpc("create_finance_requisition", {
    p_req_type: args.reqType,
    p_amount: seedAmount,
    p_currency: args.currency,
    p_payee: args.payee?.trim() || undefined,
    p_memo: args.memo?.trim() || undefined,
    p_expense_account_code: seedExpense,
    p_cash_account_code: args.cashAccountCode || undefined,
    p_exchange_rate: exchangeRate,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "create_finance_requisition returned no id." };
  }

  if (args.lines && args.lines.length > 0) {
    const setRes = await setFinanceRequisitionLines(client, {
      requisitionId: data,
      lines: args.lines,
    });
    if (!setRes.ok) return setRes;
  }

  return { ok: true, data };
}

export async function setFinanceRequisitionLines(
  client: FinanceClient,
  args: { requisitionId: string; lines: FinanceRequisitionLineInput[] },
): Promise<StorefrontResult<string>> {
  if (args.lines.length < 1) {
    return { ok: false, error: "At least one requisition line is required." };
  }
  for (const line of args.lines) {
    if (!Number.isFinite(line.amount) || line.amount <= 0) {
      return { ok: false, error: "Each line amount must be a positive number." };
    }
    if (!line.expenseAccountCode?.trim()) {
      return { ok: false, error: "Each line needs an expense account." };
    }
  }
  const { data, error } = await client.rpc("set_finance_requisition_lines", {
    p_requisition_id: args.requisitionId,
    p_lines: args.lines.map((l) => ({
      expense_account_code: l.expenseAccountCode.trim(),
      amount: l.amount,
      description: l.description?.trim() || null,
    })),
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "set_finance_requisition_lines returned no id." };
  }
  return { ok: true, data };
}

export async function submitFinanceRequisition(
  client: FinanceClient,
  requisitionId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("submit_finance_requisition", {
    p_requisition_id: requisitionId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "submit_finance_requisition returned no id." };
  }
  return { ok: true, data };
}

export async function approveFinanceRequisition(
  client: FinanceClient,
  requisitionId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("approve_finance_requisition", {
    p_requisition_id: requisitionId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "approve_finance_requisition returned no id." };
  }
  return { ok: true, data };
}

export async function rejectFinanceRequisition(
  client: FinanceClient,
  args: { requisitionId: string; reason?: string },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("reject_finance_requisition", {
    p_requisition_id: args.requisitionId,
    p_reason: args.reason?.trim() || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "reject_finance_requisition returned no id." };
  }
  return { ok: true, data };
}

export async function cancelFinanceRequisition(
  client: FinanceClient,
  requisitionId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("cancel_finance_requisition", {
    p_requisition_id: requisitionId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "cancel_finance_requisition returned no id." };
  }
  return { ok: true, data };
}

export async function disburseFinanceRequisition(
  client: FinanceClient,
  args: { requisitionId: string; entryDate?: string },
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("disburse_finance_requisition", {
    p_requisition_id: args.requisitionId,
    p_entry_date: args.entryDate || undefined,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) {
    return { ok: false, error: "disburse_finance_requisition returned no id." };
  }
  return { ok: true, data };
}
