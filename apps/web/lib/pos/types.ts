/**
 * Web POS domain types. These mirror the tablet POS (`PosViewModel` / `RpcClient`) so both
 * runtimes describe the same product (owner decision D7, `docs/design/pos/WEB_POS_PARITY.md`).
 */

export type PosCurrency = "USD" | "ZIG";

export type Money = { amount: number; currency: PosCurrency };

export type PosPart = {
  stockItemId: string | null;
  oemPartNumber: string;
  name: string;
  price: Money | null;
  saleableQty: number | null;
  imageUrl: string | null;
  categoryName?: string | null;
};

/** Model → Generation → Engine. Make is never part of the cascade (owner decision D2). */
export type VehicleModel = { slug: string; name: string; yearStart: number | null; yearEnd: number | null };
export type VehicleVariant = {
  slug: string;
  chassisCode: string;
  engineCode: string | null;
  yearLabel: string | null;
};
export type VehicleGeneration = { chassisCode: string; label: string };
export type SelectedVehicle = {
  modelSlug: string;
  modelName: string;
  generation: string;
  chassisCode: string;
  engineCode: string;
};

export type PopularPinKind = "part" | "model" | "category" | "subcategory";

export type PopularPin = {
  kind: PopularPinKind;
  key: string;
  label: string;
  subtitle: string | null;
  searchQuery: string;
  oemPartNumber: string | null;
  imageUrl: string | null;
};

/** One card in the Popular Items row: an operator pin or a server best seller (D1). */
export type PopularRowItem =
  | { source: "pin"; key: string; pin: PopularPin }
  | { source: "bestseller"; key: string; part: PosPart };

export type CartLine = {
  id: string;
  stockItemId: string;
  oemPartNumber: string;
  name: string;
  qty: number;
  unitPrice: number;
  lineTotal: number;
  imageUrl: string | null;
};

export type PosCart = {
  id: string;
  documentNumber: string | null;
  status: string;
  currency: PosCurrency;
  warehouseId: string | null;
  fulfillmentMode: FulfillmentMode;
  lines: CartLine[];
  customerId: string | null;
  customerName: string | null;
  vehicle: SelectedVehicle | null;
  /** Every vehicle shopped for in this sale (multi-vehicle context). */
  vehicles: SelectedVehicle[];
  /** Till session the sale is rung through (required before reserve-first payment). */
  tillSessionId: string | null;
};

export type PosResult<T> = { ok: true; data: T } | { ok: false; error: string };

// ───────── Sale setup (Quick Sale) ─────────
export type FulfillmentMode = "immediate" | "dispatch";
export type Warehouse = { id: string; code: string; name: string };
export type SaleSetup = { warehouseId: string | null; currency: PosCurrency; fulfillmentMode: FulfillmentMode };

// ───────── Customers + garage ─────────
export type CustomerKind = "individual" | "business";
export type PosCustomer = {
  id: string;
  kind: CustomerKind;
  displayName: string;
  businessName: string | null;
  email: string | null;
  phoneE164: string | null;
  whatsappE164: string | null;
};
export type CustomerInput = Omit<PosCustomer, "id">;
export type GarageVehicle = {
  id: string;
  modelSlug: string | null;
  model: string | null;
  generation: string | null;
  chassisCode: string | null;
  engine: string | null;
  vin: string | null;
  isPrimary: boolean;
};

// ───────── Payment + receipt ─────────
/** `public.payment_tender` values usable at the counter (ContiPay/Paynow are online PSP flows). */
export type Tender = "cash" | "bank" | "ecocash" | "store_credit";
export type TenderLine = { tender: Tender; amount: number };
/** Tenders the counter settles itself (`settle_pos_commerce_tenders`); digital money goes through a provider. */
export type ManualTender = "cash" | "bank" | "store_credit";
export type ManualTenderLine = { tender: ManualTender; amount: number };
/** Digital providers: the customer pays on their phone; the provider's webhook settles the order. */
export type DigitalProvider = "ecocash" | "paynow" | "contipay";
/** Paynow and ContiPay payment methods (`paynow_method`, `contipay_method`). */
export type PaynowMethod = "ecocash" | "onemoney" | "innbucks" | "visa";
export type ContipayMethod = "ecocash" | "visa" | "zimswitch";
/** Blueprint §10.7: every attempt ends as exactly one of these. */
export type TenderOutcome = "approved" | "declined" | "cancelled" | "error" | "unknown";
export type PaymentException = { id: string; provider: string | null; code: string; detail: string | null; resolvedAt: string | null; resolution: string | null; createdAt: string };
/** `get_pos_payment_status`: the server's view of a reserved checkout (`commerce_orders`). */
export type PaymentStatus = {
  orderId: string;
  cartId: string | null;
  state: string;
  total: number;
  currency: PosCurrency;
  reservationExpiresAt: string | null;
  activeProvider: string | null;
  activeIntentId: string | null;
  providerStatus: string | null;
  providerFailure: string | null;
  settledProvider: string | null;
  settledProviderRef: string | null;
  salesInvoiceId: string | null;
  paymentException: string | null;
  exceptions: PaymentException[];
};
/** A started provider attempt; [checkoutUrl] is the hosted page the customer opens (Paynow / ContiPay). */
export type ProviderStart = { intentId: string; checkoutUrl: string | null; message: string | null };
/** Why a digital provider cannot be offered right now; null when it can. */
export type ProviderAvailability = Record<DigitalProvider, string | null>;
/** `list_pos_payment_recovery` row: a checkout whose money is not settled cleanly. */
export type RecoveryItem = {
  orderId: string;
  state: string;
  total: number;
  currency: PosCurrency;
  activeProvider: string | null;
  settledProvider: string | null;
  salesInvoiceId: string | null;
  paymentException: string | null;
  reservationExpiresAt: string | null;
  updatedAt: string;
  openExceptions: number;
};
/** `list_pos_pickup_orders` row: paid (or on account), waiting for the customer to collect. */
export type PickupOrder = {
  orderId: string;
  documentNumber: string | null;
  customerName: string | null;
  state: string;
  total: number;
  currency: PosCurrency;
  salesInvoiceId: string | null;
  settledProvider: string | null;
  updatedAt: string;
};
// ───────── Part payments (staged split, Blueprint §10.5 / §10.8) ─────────
/** Tenders a part payment can take at the counter (`add_pos_split_payment_leg`). */
export type SplitTender = "cash" | "bank" | "store_credit";
export type SplitLegStatus =
  | "planned"
  | "held"
  | "pending"
  | "captured"
  | "failed"
  | "unknown"
  | "allocated"
  | "refund_review"
  | "refund_pending"
  | "refunded"
  | "cancelled";
export type SplitSessionStatus =
  | "open"
  | "partially_captured"
  | "leg_pending"
  | "fully_committed"
  | "finalizing"
  | "settled"
  | "finalization_failed"
  | "refund_review"
  | "refund_pending"
  | "refunded"
  | "cancelled";
export type SplitLeg = {
  id: string;
  sequenceNo: number;
  tender: string;
  amount: number;
  status: SplitLegStatus;
  reference: string | null;
  providerRef: string | null;
  statusDetail: string | null;
  appliedAmount: number | null;
  refundRequired: number | null;
};
export type RefundFeePolicy = "business_absorbs" | "customer_bears" | "manual_review";
export type SplitRefund = {
  id: string;
  legId: string;
  status: "review" | "pending" | "settled" | "failed" | "cancelled" | string;
  grossAmount: number;
  feePolicy: RefundFeePolicy | string;
  netCustomerRefund: number | null;
  providerRef: string | null;
  failureReason: string | null;
  notes: string | null;
};
/** `pos_split_payment_payload`: every amount is the server's, never worked out at the counter. */
export type SplitSession = {
  sessionId: string;
  orderId: string;
  status: SplitSessionStatus;
  total: number;
  currency: PosCurrency;
  captured: number;
  held: number;
  pending: number;
  locked: number;
  balanceDue: number;
  availableToAllocate: number;
  finalInvoiceId: string | null;
  finalizationError: string | null;
  reducedBasketAcceptedAt: string | null;
  legs: SplitLeg[];
  refunds: SplitRefund[];
};
/** `list_pos_split_payment_recovery` row. */
export type SplitRecoveryItem = {
  sessionId: string;
  orderId: string;
  status: SplitSessionStatus;
  documentNumber: string | null;
  customerName: string | null;
  total: number;
  currency: PosCurrency;
  updatedAt: string;
  session: SplitSession;
};
/** Refund steps for captured part payments: manager or finance (or an approver badge). */
export type SplitRefundStep =
  | { kind: "approve"; feePolicy: RefundFeePolicy; customerFee: number; notes: string | null }
  | { kind: "complete"; providerRef: string; notes: string | null }
  | { kind: "fail"; reason: string };

// ───────── Card machines (ECR, adapter android_intent_v1) ─────────
/** `adapter_config` keys an admin may set: intent actions and extra names only — never secrets. */
export type CardTerminalConfig = Partial<
  Record<
    | "package_name"
    | "purchase_action"
    | "refund_action"
    | "status_action"
    | "reversal_action"
    | "amount_minor_key"
    | "currency_key"
    | "reference_key"
    | "operation_key"
    | "original_transaction_id_key"
    | "result_status_key"
    | "result_transaction_id_key"
    | "result_rrn_key"
    | "result_auth_code_key"
    | "result_last4_key"
    | "result_scheme_key"
    | "result_response_code_key"
    | "result_response_message_key",
    string
  >
>;
export type CardTerminal = {
  id: string;
  code: string;
  label: string;
  acquirerName: string | null;
  externalTerminalId: string | null;
  config: CardTerminalConfig;
  warehouseId: string | null;
  deviceId: string | null;
  isActive: boolean;
};
export type CardTerminalInput = Omit<CardTerminal, "id"> & { id: string | null };
/** `list_pos_card_terminal_recovery` row: a card-machine payment that needs someone. */
export type TerminalRecoveryItem = {
  attemptId: string;
  operation: string;
  status: string;
  terminalLabel: string | null;
  orderId: string | null;
  amount: number;
  currency: PosCurrency;
  transactionId: string | null;
  cardLast4: string | null;
  message: string | null;
  updatedAt: string;
};

export type ReceiptContacts = { email: string | null; whatsappE164: string | null; phoneE164: string | null };
export type ReceiptLine = { name: string; oemPartNumber: string; qty: number; unitPrice: number; lineTotal: number };
export type ReceiptDocument = {
  invoiceId: string;
  documentNumber: string | null;
  postedAt: string | null;
  currency: PosCurrency;
  customerName: string | null;
  lines: ReceiptLine[];
  subtotal: number;
  total: number;
  amountPaid: number;
  tenders: TenderLine[];
  vehicleLabel: string | null;
  operator: string;
};

// ───────── Manager approval ─────────
export type ManagerCredentials = { identifier: string; password: string };
/**
 * How a manager approves one action: their password (isolated sign-in), their scanned ID badge,
 * or nothing extra because the signed-in user is a manager.
 */
export type ManagerProof = { kind: "password"; credentials: ManagerCredentials } | { kind: "badge"; payload: string } | { kind: "self" };
/** Actions a scanned badge can approve (`pos_badge_approve`). */
export type BadgeAction =
  | "discount"
  | "price_override"
  | "void_sale"
  | "refund"
  | "cash_out"
  | "till_variance"
  | "till_handover"
  | "repair_paid_order"
  | "split_refund_approve"
  | "split_refund_complete"
  | "split_refund_fail"
  | "return_post"
  | "core_return"
  | "warranty_approve"
  | "warranty_reject";
export type ApproverStatus = { isApprover: boolean; source: string | null };
/**
 * Someone who could approve: an employee (login optional) or an admin user with no employee record.
 * Badges are issued to that holder.
 */
export type ManagerCandidate = {
  holderType: "employee" | "user";
  employeeId: string | null;
  userId: string | null;
  fullName: string;
  employeeCode: string | null;
  grade: string | null;
  roleTitle: string | null;
  department: string | null;
  hasLogin: boolean;
  isApprover: boolean;
  /** senior_grade · approval_role · department_manager · assigned · admin_role */
  source: string | null;
  assigned: boolean;
  activeBadges: number;
};
export type ManagerBadge = {
  badgeId: string;
  employeeId: string | null;
  userId: string | null;
  fullName: string;
  label: string | null;
  issuedAt: string;
  expiresAt: string;
  revokedAt: string | null;
  revokeReason: string | null;
  lastUsedAt: string | null;
  useCount: number;
  status: "active" | "revoked" | "expired";
};
/** A new badge; [payload] is what the card's QR carries and is never shown again. */
export type IssuedBadge = { badgeId: string; payload: string; expiresAt: string; fullName: string; employeeCode: string | null; title: string | null };
export type ApprovalTrailRow = {
  at: string;
  /** badge · approver_session · admin */
  method: string;
  outcome: string;
  action: string;
  managerName: string | null;
  requestedByName: string | null;
  reasonCode: string | null;
  detail: string | null;
  deviceId: string | null;
};

// ───────── Orders + returns ─────────
export type QuotationStatus = "draft" | "issued" | "sent" | "converted" | "cancelled" | "expired";
export type Quotation = {
  id: string;
  documentNumber: string | null;
  status: QuotationStatus;
  validUntil: string | null;
  sentChannel: string | null;
  createdAt: string;
  lineCount: number;
  total: number;
  currency: PosCurrency;
};
export type ParkedCart = { id: string; documentNumber: string | null; updatedAt: string; lineCount: number; total: number; currency: PosCurrency };
export type RecentInvoice = {
  id: string;
  documentNumber: string | null;
  customerName: string | null;
  total: number;
  currency: PosCurrency;
  postedAt: string | null;
  vehicleLabel: string | null;
};

/** A posted sale's lines with what can still come back (`get_pos_invoice_detail`); core charges separate. */
export type InvoiceDetailLine = {
  id: string;
  stockItemId: string;
  partNumber: string;
  description: string | null;
  uomId: string;
  qty: number;
  unitPrice: number;
  lineTotal: number;
  isCore: boolean;
  returnableQty: number;
};
export type InvoiceDetail = {
  id: string;
  documentNumber: string | null;
  customerId: string | null;
  currency: PosCurrency;
  total: number;
  amountPaid: number;
  postedAt: string | null;
  tillSessionId: string | null;
  lines: InvoiceDetailLine[];
};
export type ReturnResolution = "credit_note" | "cash_refund" | "store_credit" | "replacement" | "warranty";
export type ReturnCondition = "sealed" | "unopened" | "opened" | "damaged" | "defective";
/** Replacement stock handed over (same part by default); serial only for serialised parts. */
export type ReplacementLine = { stockItemId: string; uomId: string; qty: number; replacementSerialId?: string | null };
/** A return case a sales person drafts (`create_pos_return_case`); a manager posts it. */
export type ReturnCaseDraft = {
  invoiceId: string;
  resolution: ReturnResolution;
  reasonCode: string;
  notes: string | null;
  lines: { invoiceLineId: string; qty: number; condition: ReturnCondition }[];
  replacementLines: ReplacementLine[] | null;
  tillSessionId: string | null;
};
export type CoreReturnResolution = "cash_refund" | "account_credit" | "store_credit";
export type CoreReturn = {
  invoiceId: string;
  coreLineId: string;
  qty: number;
  resolution: CoreReturnResolution;
  reasonCode: string;
  tillSessionId: string | null;
  notes: string | null;
};
export type WarrantyStatus = "open" | "approved" | "rejected" | "closed";
export type WarrantyClaim = {
  id: string;
  documentNumber: string | null;
  status: WarrantyStatus;
  resolution: string | null;
  invoiceId: string | null;
  invoiceNumber: string | null;
  stockItemId: string | null;
  partNumber: string | null;
  serialNumber: string | null;
  notes: string | null;
  rejectReason: string | null;
  creditNoteId: string | null;
  createdAt: string;
  decidedAt: string | null;
  closedAt: string | null;
};
export type WarrantySerial = { id: string; serialNumber: string; stockItemId: string; partNumber: string | null; status: string };
/** A manager's warranty decision. Credit note lines are valued at the sold price on the server. */
export type WarrantyDecision =
  | { kind: "approve"; resolution: "replacement" | "credit_note" | "return_only"; qty: number; replacement: ReplacementLine[] | null }
  | { kind: "reject"; reason: string };
/** Stock of one part per warehouse (`list_pos_stock_availability`). */
export type StockAvailability = {
  warehouseId: string;
  code: string;
  name: string;
  onHand: number;
  reserved: number;
  available: number;
  incoming: number;
};

/** Getting a part to the customer when it is not on this shelf (`pos_fulfillment_requests`). */
export type FulfillmentKind = "customer_collection" | "alternate_pickup" | "branch_transfer" | "backorder";
export type FulfillmentStatus = "requested" | "reserved" | "awaiting_transfer_approval" | "ready" | "collected" | "cancelled" | "rejected";
export type FulfillmentRequest = {
  id: string;
  documentNumber: string | null;
  kind: FulfillmentKind;
  status: FulfillmentStatus;
  stockItemId: string;
  partNumber: string;
  description: string | null;
  qty: number;
  sourceWarehouseId: string | null;
  sourceName: string | null;
  destinationWarehouseId: string | null;
  destinationName: string | null;
  customerId: string | null;
  cartId: string | null;
  invoiceId: string | null;
  expiresAt: string | null;
  readyAt: string | null;
  collectedAt: string | null;
  createdAt: string;
};
export type FulfillmentInput = {
  kind: FulfillmentKind;
  stockItemId: string;
  qty: number;
  sourceWarehouseId: string | null;
  destinationWarehouseId: string | null;
  customerId: string | null;
  /** The sale it is for: when that sale posts, the hold becomes ready with its invoice. */
  cartId: string | null;
  notes: string | null;
  holdMinutes: number;
};
export type FulfillmentStep = "approve" | "ready" | "collect" | "cancel";

/** A payment a resolution letter can be about (`payment_resolution_source_kind`). */
export type LetterSourceKind = "card_terminal" | "ecocash" | "paynow" | "contipay" | "split_leg" | "split_refund";
export type PaymentLetterSummary = {
  id: string;
  documentNumber: string | null;
  sourceKind: LetterSourceKind;
  provider: string | null;
  observedStatus: string | null;
  amount: number;
  currency: PosCurrency;
  customerName: string | null;
  invoiceNumber: string | null;
  managerName: string | null;
  managerTitle: string | null;
  issuedAt: string;
};
/** Name and contact details printed on staff documents (`business_document_profile`). */
export type BusinessProfile = {
  legalName: string;
  tradingName: string;
  domain: string;
  city: string | null;
  country: string | null;
  addressLine1: string | null;
  addressLine2: string | null;
  phone: string | null;
  email: string | null;
  registrationNumber: string | null;
};
/** Everything a printed letter shows: the letter's own frozen copy and the business profile. */
export type PaymentLetter = PaymentLetterSummary & {
  externalReference: string | null;
  providerReference: string | null;
  terminalTransactionId: string | null;
  rrn: string | null;
  authorizationCode: string | null;
  cardLast4: string | null;
  cardScheme: string | null;
  failureDetail: string | null;
  managerEmployeeCode: string | null;
  issueNotes: string | null;
  signatureSha256: string | null;
  /** Short-lived link to the signature image; null when this user may not read it. */
  signatureUrl: string | null;
  business: BusinessProfile | null;
};
export type MySignature = { fullName: string; employeeCode: string | null; hasSignature: boolean; capturedAt: string | null; imageUrl: string | null };

// ───────── EPC ─────────
export type EpcSection = { slug: string; name: string; thumbnailUrl: string | null };
/** [id] is the full-catalogue diagram id that keys its R2 part shard and image. */
export type EpcDiagramRef = { id: string | null; slug: string; title: string; imageUrl: string | null };
export type EpcHotspot = { oem: string; pnc: string | null; x: number; y: number; w: number; h: number };
export type EpcDiagramPart = {
  oemPartNumber: string;
  pnc: string | null;
  /** Reference number printed on the diagram artwork. */
  ref?: string | null;
  name: string;
  categoryName: string | null;
  subcategoryName: string | null;
};
export type EpcDiagram = {
  title: string;
  imageUrl: string | null;
  width: number | null;
  height: number | null;
  hotspots: EpcHotspot[];
  parts: EpcDiagramPart[];
  /** Why parts or the image are missing (e.g. not yet published to the live catalogue). */
  notice?: string | null;
};

// ───────── Till sessions (cash drawer) ─────────
export type TillStatus = "open" | "variance_pending" | "closed";
export type TillSession = {
  id: string;
  deviceId: string;
  warehouseId: string;
  currency: PosCurrency;
  operatorUserId: string;
  openingFloat: number;
  status: TillStatus;
  /** Known only once the till is closed (blind count). */
  expectedCash: number | null;
  countedCash: number | null;
  variance: number | null;
  varianceReasonCode: string | null;
  openedAt: string;
  closedAt: string | null;
};
export type TillCloseResult = {
  sessionId: string;
  expectedCash: number;
  countedCash: number;
  variance: number;
  status: "closed" | "variance_pending";
};
export type CashMovementKind = "cash_in" | "cash_out" | "petty_cash" | "bank_drop" | "cash_refund";
export type DenominationCount = { denomination: number; quantity: number };
/** Configured reason for a governed action (`pos_approval_reason_codes`). */
export type ReasonCode = { code: string; label: string; requiresNotes: boolean };
/**
 * `pos_approval_policies` row: when an action needs a manager, and whether it needs a reason.
 * [thresholdValue] is the action's own unit (percent for discount and price override).
 */
export type ApprovalPolicy = {
  action: string;
  thresholdValue: number;
  alwaysRequireManager: boolean;
  reasonRequired: boolean;
  updatedAt: string | null;
};
/** A governed action: the configured reason and, when policy asks for one, the manager who approved. */
export type Governed = { reasonCode: string; notes: string | null; manager: ManagerCredentials | null };
export type HandoverOperator = { userId: string; employeeCode: string; fullName: string; roles: string[] };
