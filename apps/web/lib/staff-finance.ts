import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  requireSession,
  zigExchangeRate,
  type StorefrontResult,
} from "@/lib/customer-storefront";

export { requireSession, zigExchangeRate };

export type CurrencyCode = Database["public"]["Enums"]["currency_code"];
export type PaymentTender = Database["public"]["Enums"]["payment_tender"];

export type JournalEntryOption = {
  id: string;
  document_number: string | null;
  status: string;
  entry_date: string;
  description: string | null;
  currency: CurrencyCode;
  exchange_rate_applied: number;
  created_at: string;
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

export async function listChartAccounts(
  client: SupabaseClient,
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
  client: SupabaseClient,
): Promise<StorefrontResult<JournalEntryOption[]>> {
  const { data, error } = await client
    .from("journal_entries")
    .select(
      "id, document_number, status, entry_date, description, currency, exchange_rate_applied, created_at",
    )
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as JournalEntryOption[]) ?? [] };
}

export async function listDraftPayments(
  client: SupabaseClient,
): Promise<StorefrontResult<PaymentEntryOption[]>> {
  const { data, error } = await client
    .from("payment_entries")
    .select(
      "id, document_number, status, amount, currency, tender, customer_id, created_at",
    )
    .eq("status", "draft")
    .order("created_at", { ascending: false })
    .limit(40);
  if (error) return { ok: false, error: error.message };
  return { ok: true, data: (data as PaymentEntryOption[]) ?? [] };
}

export async function createJournalDraft(
  client: SupabaseClient,
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
  client: SupabaseClient,
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
  client: SupabaseClient,
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
  client: SupabaseClient,
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
  client: SupabaseClient,
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

export async function createPaymentEntry(
  client: SupabaseClient,
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
  client: SupabaseClient,
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
  client: SupabaseClient,
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
  client: SupabaseClient,
  paymentEntryId: string,
): Promise<StorefrontResult<string>> {
  const { data, error } = await client.rpc("cancel_payment_entry", {
    p_payment_entry_id: paymentEntryId,
  });
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: false, error: "cancel_payment_entry returned no id." };
  return { ok: true, data };
}
