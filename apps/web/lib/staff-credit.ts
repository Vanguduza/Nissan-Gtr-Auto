import type { Database, SupabaseClient } from "@gtr/supabase-client";
import {
  displayCreditLimitMajor,
  displayOpenBalanceMajor,
  type CurrencyCode as SharedCurrency,
} from "@gtr/shared";
import type { StorefrontResult } from "@/lib/customer-storefront";
import { searchCustomers, type CustomerOption } from "@/lib/staff-finance";

export type CurrencyCode = Database["public"]["Enums"]["currency_code"];

export type CustomerCreditRow = {
  id: string;
  display_name: string;
  credit_limit: number;
  credit_hold: boolean;
  open_balance: number;
  marketing_opt_in: boolean;
  last_promotional_message_at: string | null;
  /** Display currency for limit/balance — customers are multi-currency aware via RPC. */
  currency?: CurrencyCode;
  /** B-MONEY-1 dual-read; prefer when present. */
  credit_limit_minor?: number | null;
  open_balance_minor?: number | null;
};

export type CustomerCreditSnapshot = {
  customer_id: string;
  credit_limit: number;
  credit_hold: boolean;
  open_balance: number;
  currency: CurrencyCode;
  credit_limit_minor?: number | null;
  open_balance_minor?: number | null;
};

export { searchCustomers };
export type { CustomerOption };

export async function loadCustomerCredit(
  client: SupabaseClient,
  customerId: string,
): Promise<StorefrontResult<CustomerCreditRow | null>> {
  const { data, error } = await client
    .from("customers")
    .select(
      "id, display_name, credit_limit, credit_limit_minor, credit_hold, open_balance, open_balance_minor, marketing_opt_in, last_promotional_message_at, currency",
    )
    .eq("id", customerId)
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: true, data: null };
  const currency = (data.currency ?? "USD") as CurrencyCode;
  return {
    ok: true,
    data: {
      id: data.id,
      display_name: data.display_name,
      credit_limit: displayCreditLimitMajor(
        {
          credit_limit: Number(data.credit_limit ?? 0),
          credit_limit_minor: data.credit_limit_minor ?? null,
        },
        currency as SharedCurrency,
      ),
      credit_hold: !!data.credit_hold,
      open_balance: displayOpenBalanceMajor(
        {
          open_balance: Number(data.open_balance ?? 0),
          open_balance_minor: data.open_balance_minor ?? null,
        },
        currency as SharedCurrency,
      ),
      marketing_opt_in: !!data.marketing_opt_in,
      last_promotional_message_at: data.last_promotional_message_at ?? null,
      currency,
      credit_limit_minor: data.credit_limit_minor ?? null,
      open_balance_minor: data.open_balance_minor ?? null,
    },
  };
}

export async function setCustomerCredit(
  client: SupabaseClient,
  args: {
    customerId: string;
    creditLimit?: number;
    creditHold?: boolean;
  },
): Promise<StorefrontResult<CustomerCreditSnapshot>> {
  if (args.creditLimit == null && args.creditHold == null) {
    return { ok: false, error: "Provide credit_limit and/or credit_hold." };
  }
  const { data, error } = await client.rpc("set_customer_credit", {
    p_customer_id: args.customerId,
    p_credit_limit: args.creditLimit ?? undefined,
    p_credit_hold: args.creditHold ?? undefined,
  });
  if (error) return { ok: false, error: error.message };
  const row = (data as CustomerCreditSnapshot[] | null)?.[0];
  if (!row) return { ok: false, error: "set_customer_credit returned no row." };
  const currency = row.currency ?? "USD";
  return {
    ok: true,
    data: {
      customer_id: row.customer_id,
      credit_limit: displayCreditLimitMajor(
        {
          credit_limit: Number(row.credit_limit ?? 0),
          credit_limit_minor: row.credit_limit_minor ?? null,
        },
        currency as SharedCurrency,
      ),
      credit_hold: row.credit_hold,
      open_balance: displayOpenBalanceMajor(
        {
          open_balance: Number(row.open_balance ?? 0),
          open_balance_minor: row.open_balance_minor ?? null,
        },
        currency as SharedCurrency,
      ),
      currency,
      credit_limit_minor: row.credit_limit_minor ?? null,
      open_balance_minor: row.open_balance_minor ?? null,
    },
  };
}
