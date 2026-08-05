import type { Database, SupabaseClient } from "@gtr/supabase-client";
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
};

export type CustomerCreditSnapshot = {
  customer_id: string;
  credit_limit: number;
  credit_hold: boolean;
  open_balance: number;
  currency: CurrencyCode;
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
      "id, display_name, credit_limit, credit_hold, open_balance, marketing_opt_in, last_promotional_message_at",
    )
    .eq("id", customerId)
    .maybeSingle();
  if (error) return { ok: false, error: error.message };
  if (!data) return { ok: true, data: null };
  return {
    ok: true,
    data: {
      id: data.id,
      display_name: data.display_name,
      credit_limit: Number(data.credit_limit ?? 0),
      credit_hold: !!data.credit_hold,
      open_balance: Number(data.open_balance ?? 0),
      marketing_opt_in: !!data.marketing_opt_in,
      last_promotional_message_at: data.last_promotional_message_at ?? null,
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
  return { ok: true, data: row };
}
