import type { CurrencyCode, Money } from "../money.js";
import { assertCurrency } from "../money.js";

export interface JournalLineInput {
  accountCode: string;
  debit: number;
  credit: number;
  currency: CurrencyCode;
}

export interface JournalEntryInput {
  description: string;
  currency: CurrencyCode;
  exchangeRateApplied?: number | null;
  lines: JournalLineInput[];
}

/** Validate double-entry balance (debits === credits) within 0.01. */
export function assertBalanced(lines: JournalLineInput[]): void {
  const debit = lines.reduce((s, l) => s + l.debit, 0);
  const credit = lines.reduce((s, l) => s + l.credit, 0);
  if (Math.abs(debit - credit) > 0.009) {
    throw new Error(`Unbalanced journal entry: debit=${debit} credit=${credit}`);
  }
  for (const line of lines) {
    if (line.debit < 0 || line.credit < 0) {
      throw new Error("Debit/credit amounts must be non-negative");
    }
    if (line.debit > 0 && line.credit > 0) {
      throw new Error(`Line ${line.accountCode} cannot have both debit and credit`);
    }
  }
}

export function createJournalDraft(input: JournalEntryInput): JournalEntryInput {
  assertBalanced(input.lines);
  return input;
}

/** Sale pattern helper — amounts only; posting is append-only in DB. */
export function saleJournalLines(params: {
  revenue: Money;
  cogs: Money;
  inventoryAccount?: string;
  cashOrArAccount?: "1100" | "1200";
}): JournalLineInput[] {
  assertCurrency(params.revenue.currency);
  assertCurrency(params.cogs.currency);
  const cashOrAr = params.cashOrArAccount ?? "1200";
  const inventory = params.inventoryAccount ?? "1300";
  const lines: JournalLineInput[] = [
    {
      accountCode: cashOrAr,
      debit: params.revenue.amount,
      credit: 0,
      currency: params.revenue.currency,
    },
    {
      accountCode: "4100",
      debit: 0,
      credit: params.revenue.amount,
      currency: params.revenue.currency,
    },
    {
      accountCode: "5100",
      debit: params.cogs.amount,
      credit: 0,
      currency: params.cogs.currency,
    },
    {
      accountCode: inventory,
      debit: 0,
      credit: params.cogs.amount,
      currency: params.cogs.currency,
    },
  ];
  assertBalanced(lines);
  return lines;
}
