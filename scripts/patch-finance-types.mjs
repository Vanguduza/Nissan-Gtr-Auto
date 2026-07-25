/**
 * One-shot patch: add finance period/requisition types to database.types.ts
 * Run: node scripts/patch-finance-types.mjs
 */
import fs from "node:fs";

const path = "packages/supabase-client/src/database.types.ts";
let s = fs.readFileSync(path, "utf8");
if (s.includes("account_period_balances:")) {
  console.log("already patched");
  process.exit(0);
}

function insertBefore(haystack, needle, insert) {
  const i = haystack.indexOf(needle);
  if (i < 0) throw new Error("missing before: " + needle.slice(0, 80));
  return haystack.slice(0, i) + insert + haystack.slice(i);
}
function insertAfter(haystack, needle, insert) {
  const i = haystack.indexOf(needle);
  if (i < 0) throw new Error("missing after: " + needle.slice(0, 80));
  const j = i + needle.length;
  return haystack.slice(0, j) + insert + haystack.slice(j);
}

const accountPeriodBalances = `      account_period_balances: {
        Row: {
          account_code: string
          closed_at: string | null
          closed_by: string | null
          closing_balance: number | null
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          id: string
          notes: string | null
          opened_at: string
          opened_by: string | null
          opening_balance: number
          period_end: string
          period_start: string
          physical_count: number | null
          status: Database["public"]["Enums"]["account_period_status"]
          variance: number | null
        }
        Insert: {
          account_code: string
          closed_at?: string | null
          closed_by?: string | null
          closing_balance?: number | null
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          id?: string
          notes?: string | null
          opened_at?: string
          opened_by?: string | null
          opening_balance?: number
          period_end: string
          period_start: string
          physical_count?: number | null
          status?: Database["public"]["Enums"]["account_period_status"]
          variance?: number | null
        }
        Update: {
          account_code?: string
          closed_at?: string | null
          closed_by?: string | null
          closing_balance?: number | null
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          id?: string
          notes?: string | null
          opened_at?: string
          opened_by?: string | null
          opening_balance?: number
          period_end?: string
          period_start?: string
          physical_count?: number | null
          status?: Database["public"]["Enums"]["account_period_status"]
          variance?: number | null
        }
        Relationships: [
          {
            foreignKeyName: "account_period_balances_account_code_fkey"
            columns: ["account_code"]
            isOneToOne: false
            referencedRelation: "chart_of_accounts"
            referencedColumns: ["code"]
          },
        ]
      }
`;

s = insertAfter(
  s,
  `        Relationships: []
      }
      ai_report_deliveries:`,
  "\n" + accountPeriodBalances.trimEnd() + "\n",
);
// Fix: insertAfter put content after the needle which includes ai_report - wrong.
// Redo with insertBefore on ai_report_deliveries after accounting_periods block.

s = fs.readFileSync(path, "utf8"); // reset — we'll do carefully below
if (s.includes("account_period_balances:")) {
  console.log("already patched");
  process.exit(0);
}

s = insertBefore(s, "      ai_report_deliveries: {", accountPeriodBalances);

const financeTables = `      finance_requisition_lines: {
        Row: {
          amount: number
          created_at: string
          description: string | null
          expense_account_code: string
          id: string
          line_no: number
          requisition_id: string
        }
        Insert: {
          amount: number
          created_at?: string
          description?: string | null
          expense_account_code: string
          id?: string
          line_no: number
          requisition_id: string
        }
        Update: {
          amount?: number
          created_at?: string
          description?: string | null
          expense_account_code?: string
          id?: string
          line_no?: number
          requisition_id?: string
        }
        Relationships: [
          {
            foreignKeyName: "finance_requisition_lines_expense_account_code_fkey"
            columns: ["expense_account_code"]
            isOneToOne: false
            referencedRelation: "chart_of_accounts"
            referencedColumns: ["code"]
          },
          {
            foreignKeyName: "finance_requisition_lines_requisition_id_fkey"
            columns: ["requisition_id"]
            isOneToOne: false
            referencedRelation: "finance_requisitions"
            referencedColumns: ["id"]
          },
        ]
      }
      finance_requisitions: {
        Row: {
          amount: number
          approved_at: string | null
          approved_by: string | null
          cancelled_at: string | null
          cancelled_by: string | null
          cash_account_code: string
          created_at: string
          currency: Database["public"]["Enums"]["currency_code"]
          disbursed_at: string | null
          disbursed_by: string | null
          document_number: string | null
          exchange_rate_applied: number | null
          expense_account_code: string
          id: string
          journal_entry_id: string | null
          memo: string | null
          payee: string | null
          payment_entry_id: string | null
          rejected_at: string | null
          rejected_by: string | null
          rejection_reason: string | null
          req_type: Database["public"]["Enums"]["finance_requisition_type"]
          requested_by: string
          status: Database["public"]["Enums"]["finance_requisition_status"]
          submitted_at: string | null
          updated_at: string
        }
        Insert: {
          amount: number
          approved_at?: string | null
          approved_by?: string | null
          cancelled_at?: string | null
          cancelled_by?: string | null
          cash_account_code: string
          created_at?: string
          currency: Database["public"]["Enums"]["currency_code"]
          disbursed_at?: string | null
          disbursed_by?: string | null
          document_number?: string | null
          exchange_rate_applied?: number | null
          expense_account_code: string
          id?: string
          journal_entry_id?: string | null
          memo?: string | null
          payee?: string | null
          payment_entry_id?: string | null
          rejected_at?: string | null
          rejected_by?: string | null
          rejection_reason?: string | null
          req_type: Database["public"]["Enums"]["finance_requisition_type"]
          requested_by: string
          status?: Database["public"]["Enums"]["finance_requisition_status"]
          submitted_at?: string | null
          updated_at?: string
        }
        Update: {
          amount?: number
          approved_at?: string | null
          approved_by?: string | null
          cancelled_at?: string | null
          cancelled_by?: string | null
          cash_account_code?: string
          created_at?: string
          currency?: Database["public"]["Enums"]["currency_code"]
          disbursed_at?: string | null
          disbursed_by?: string | null
          document_number?: string | null
          exchange_rate_applied?: number | null
          expense_account_code?: string
          id?: string
          journal_entry_id?: string | null
          memo?: string | null
          payee?: string | null
          payment_entry_id?: string | null
          rejected_at?: string | null
          rejected_by?: string | null
          rejection_reason?: string | null
          req_type?: Database["public"]["Enums"]["finance_requisition_type"]
          requested_by?: string
          status?: Database["public"]["Enums"]["finance_requisition_status"]
          submitted_at?: string | null
          updated_at?: string
        }
        Relationships: [
          {
            foreignKeyName: "finance_requisitions_cash_account_code_fkey"
            columns: ["cash_account_code"]
            isOneToOne: false
            referencedRelation: "chart_of_accounts"
            referencedColumns: ["code"]
          },
          {
            foreignKeyName: "finance_requisitions_expense_account_code_fkey"
            columns: ["expense_account_code"]
            isOneToOne: false
            referencedRelation: "chart_of_accounts"
            referencedColumns: ["code"]
          },
          {
            foreignKeyName: "finance_requisitions_journal_entry_id_fkey"
            columns: ["journal_entry_id"]
            isOneToOne: false
            referencedRelation: "journal_entries"
            referencedColumns: ["id"]
          },
          {
            foreignKeyName: "finance_requisitions_payment_entry_id_fkey"
            columns: ["payment_entry_id"]
            isOneToOne: false
            referencedRelation: "payment_entries"
            referencedColumns: ["id"]
          },
        ]
      }
`;

s = insertBefore(s, "      goods_receipt_lines: {", financeTables);

s = insertAfter(
  s,
  `      approve_warranty_claim: {
        Args: {
          p_claim_id: string
          p_lines?: Json
          p_replacement_lines?: Json
          p_resolution: Database["public"]["Enums"]["warranty_claim_resolution"]
        }
        Returns: string
      }
`,
  `      approve_finance_requisition: {
        Args: { p_requisition_id: string }
        Returns: string
      }
`,
);

s = insertBefore(
  s,
  "      cancel_consignment_entry: {",
  `      cancel_finance_requisition: {
        Args: { p_requisition_id: string }
        Returns: string
      }
`,
);

s = insertAfter(
  s,
  "      close_warranty_claim: { Args: { p_claim_id: string }; Returns: string }\n",
  `      close_account_period: {
        Args: {
          p_notes?: string
          p_period_id: string
          p_physical_count?: number
        }
        Returns: string
      }
`,
);

s = insertAfter(
  s,
  `      compute_payroll_run: {
        Args: { p_payroll_run_id: string }
        Returns: string
      }
`,
  `      compute_petty_cash_replenish_amount: {
        Args: {
          p_as_of?: string
          p_currency?: Database["public"]["Enums"]["currency_code"]
        }
        Returns: number
      }
`,
);

s = insertBefore(
  s,
  "      create_goods_receipt: {",
  `      create_finance_requisition: {
        Args: {
          p_amount: number
          p_cash_account_code?: string
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_exchange_rate?: number
          p_expense_account_code?: string
          p_memo?: string
          p_payee?: string
          p_req_type: Database["public"]["Enums"]["finance_requisition_type"]
        }
        Returns: string
      }
`,
);

s = insertBefore(
  s,
  "      fail_delivery_job: {",
  `      disburse_finance_requisition: {
        Args: { p_entry_date?: string; p_requisition_id: string }
        Returns: string
      }
`,
);

s = insertBefore(
  s,
  "      open_warranty_claim: {",
  `      open_account_period: {
        Args: {
          p_account_code: string
          p_currency: Database["public"]["Enums"]["currency_code"]
          p_notes?: string
          p_opening_balance?: number
          p_period_end: string
          p_period_start: string
        }
        Returns: string
      }
`,
);

const postNeedle = s.includes("      post_journal: {")
  ? "      post_journal: {"
  : "      post_journal_entry: {";
s = insertBefore(
  s,
  postNeedle,
  `      petty_cash_funding_account_code: { Args: never; Returns: string }
`,
);

s = insertBefore(
  s,
  "      reject_stock_transfer: {",
  `      reject_finance_requisition: {
        Args: { p_reason?: string; p_requisition_id: string }
        Returns: string
      }
`,
);

s = insertBefore(
  s,
  "      report_balance_sheet: {",
  `      report_account_register: {
        Args: {
          p_account_code: string
          p_currency?: Database["public"]["Enums"]["currency_code"]
          p_from: string
          p_to: string
        }
        Returns: {
          credit: number
          currency: Database["public"]["Enums"]["currency_code"]
          debit: number
          description: string | null
          document_number: string | null
          entry_date: string
          journal_entry_id: string
          running_balance: number
        }[]
      }
`,
);

s = insertBefore(
  s,
  "      set_customer_credit: {",
  `      set_finance_requisition_lines: {
        Args: { p_lines: Json; p_requisition_id: string }
        Returns: string
      }
`,
);

s = insertBefore(
  s,
  "      submit_consignment_entry: {",
  `      submit_finance_requisition: {
        Args: { p_requisition_id: string }
        Returns: string
      }
`,
);

s = insertAfter(
  s,
  '      account_type: "asset" | "liability" | "equity" | "income" | "expense"\n',
  '      account_period_status: "open" | "closed"\n',
);

s = insertAfter(
  s,
  '      fleet_vehicle_status: "active" | "in_service" | "retired"\n',
  `      finance_requisition_status:
        | "draft"
        | "submitted"
        | "approved"
        | "rejected"
        | "disbursed"
        | "cancelled"
      finance_requisition_type: "petty_cash" | "payment"
`,
);

s = insertAfter(
  s,
  '      account_type: ["asset", "liability", "equity", "income", "expense"],\n',
  '      account_period_status: ["open", "closed"],\n',
);

s = insertAfter(
  s,
  '      fleet_vehicle_status: ["active", "in_service", "retired"],\n',
  `      finance_requisition_status: [
        "draft",
        "submitted",
        "approved",
        "rejected",
        "disbursed",
        "cancelled",
      ],
      finance_requisition_type: ["petty_cash", "payment"],
`,
);

fs.writeFileSync(path, s);
console.log(
  "OK",
  {
    account_period_balances: s.includes("account_period_balances:"),
    finance_requisitions: s.includes("finance_requisitions:"),
    set_lines: s.includes("set_finance_requisition_lines:"),
    report_register: s.includes("report_account_register:"),
    enums: s.includes('finance_requisition_type: "petty_cash"'),
  },
);
