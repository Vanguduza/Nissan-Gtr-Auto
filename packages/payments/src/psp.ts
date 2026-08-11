/**
 * PspAdapter registry (DIAL D-43 habits) — ContiPay / Paynow / EcoCash / COD.
 * Edge functions remain the HTTP adapters; this package owns the contract SoR.
 */
import type { CurrencyCode, MoneyMinor } from "@gtr/shared";

export type PspMethod =
  | "contipay"
  | "paynow"
  | "ecocash"
  | "cod"
  | "cash"
  | "store_credit";

export type PspInitiateRequest = {
  method: PspMethod;
  amount: MoneyMinor;
  /** Business idempotency key */
  idempotencyKey: string;
  returnUrl?: string;
  metadata?: Record<string, string>;
};

export type PspInitiateResult = {
  ok: true;
  intentId: string;
  redirectUrl?: string | null;
  pollUrl?: string | null;
} | {
  ok: false;
  error: string;
};

export type PspWebhookResult = {
  ok: true;
  intentId: string;
  status: "authorized" | "captured" | "failed" | "cancelled" | "pending";
  duplicate: boolean;
} | {
  ok: false;
  error: string;
};

export interface PspAdapter {
  readonly method: PspMethod;
  initiate(req: PspInitiateRequest): Promise<PspInitiateResult>;
  /** Verify signature + parse; never trust client redirects. */
  handleWebhook(rawBody: string, headers: Record<string, string>): Promise<PspWebhookResult>;
}

export class PspRegistry {
  private adapters = new Map<PspMethod, PspAdapter>();

  register(adapter: PspAdapter): void {
    this.adapters.set(adapter.method, adapter);
  }

  get(method: PspMethod): PspAdapter | undefined {
    return this.adapters.get(method);
  }

  require(method: PspMethod): PspAdapter {
    const a = this.adapters.get(method);
    if (!a) throw new Error(`PspAdapter not registered: ${method}`);
    return a;
  }

  list(): PspMethod[] {
    return [...this.adapters.keys()];
  }
}

/** Stub adapter for local scaffold / tests — not for production traffic. */
export function createStubPspAdapter(method: PspMethod): PspAdapter {
  return {
    method,
    async initiate(req) {
      return {
        ok: true,
        intentId: `stub_${method}_${req.idempotencyKey}`,
        redirectUrl: null,
        pollUrl: null,
      };
    },
    async handleWebhook() {
      return {
        ok: true,
        intentId: "stub",
        status: "captured",
        duplicate: false,
      };
    },
  };
}

export function defaultPspRegistry(): PspRegistry {
  const r = new PspRegistry();
  for (const m of ["contipay", "paynow", "ecocash", "cod"] as PspMethod[]) {
    r.register(createStubPspAdapter(m));
  }
  return r;
}

/** D-57: browse/cart display stays USD; ZiG only at pay step with fx_rate_id. */
export type CheckoutDisplay = {
  browseCurrency: "USD";
  payCurrency: CurrencyCode;
  payable: MoneyMinor;
  fxRateId?: string | null;
  /** Indicative ZiG for COD confirm when settling USD */
  indicativeZigMinor?: bigint | null;
};

export function buildCheckoutDisplay(input: {
  usdMinor: bigint;
  payMethod: PspMethod;
  zigRatePerUsd?: number | null;
  fxRateId?: string | null;
}): CheckoutDisplay {
  const zigWallet = input.payMethod === "ecocash";
  if (zigWallet) {
    const rate = input.zigRatePerUsd;
    if (rate == null || !(rate > 0)) {
      throw new Error("Daily ZiG rate required for EcoCash checkout");
    }
    const zigMinor = BigInt(Math.round(Number(input.usdMinor) * rate));
    return {
      browseCurrency: "USD",
      payCurrency: "ZIG",
      payable: {
        amountMinor: zigMinor,
        currency: "ZIG",
        fxRateId: input.fxRateId ?? null,
      },
      fxRateId: input.fxRateId ?? null,
    };
  }
  const indicative =
    input.zigRatePerUsd && input.zigRatePerUsd > 0
      ? BigInt(Math.round(Number(input.usdMinor) * input.zigRatePerUsd))
      : null;
  return {
    browseCurrency: "USD",
    payCurrency: "USD",
    payable: {
      amountMinor: input.usdMinor,
      currency: "USD",
      fxRateId: null,
    },
    indicativeZigMinor: indicative,
  };
}
