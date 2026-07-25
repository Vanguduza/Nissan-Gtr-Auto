import type { CurrencyCode } from "../money.js";
import { assertBalanced, type JournalLineInput } from "./journal";

/** Swap debit/credit for a reversing entry (Cancel path). */
export function reverseJournalLines(lines: JournalLineInput[]): JournalLineInput[] {
  const reversed = lines.map((l) => ({
    accountCode: l.accountCode,
    debit: l.credit,
    credit: l.debit,
    currency: l.currency,
  }));
  assertBalanced(reversed);
  return reversed;
}

export interface OpeningBalanceLine {
  accountCode: string;
  /** Positive = debit for assets; for liabilities/equity pass credit via credit field. */
  debit: number;
  credit: number;
  currency: CurrencyCode;
}

/** Build balanced opening-balance lines; caller must include equity plug (3100) if needed. */
export function createOpeningBalanceLines(lines: OpeningBalanceLine[]): JournalLineInput[] {
  const mapped: JournalLineInput[] = lines.map((l) => ({
    accountCode: l.accountCode,
    debit: l.debit,
    credit: l.credit,
    currency: l.currency,
  }));
  assertBalanced(mapped);
  return mapped;
}

/** JSON payload shape for `create_journal_draft` / `post_journal_entry` RPCs. */
export function toJournalRpcLines(lines: JournalLineInput[]) {
  assertBalanced(lines);
  return lines.map((l) => ({
    account_code: l.accountCode,
    debit: l.debit,
    credit: l.credit,
    currency: l.currency,
  }));
}
