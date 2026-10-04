import type {
  CashMovementKind,
  CustomerInput,
  DenominationCount,
  HandoverOperator,
  ReasonCode,
  TillCloseResult,
  TillSession,
  EpcDiagram,
  EpcDiagramRef,
  EpcSection,
  GarageVehicle,
  ManagerCredentials,
  ParkedCart,
  PosCustomer,
  Quotation,
  RecentInvoice,
  BusinessProfile,
  LetterSourceKind,
  MySignature,
  PaymentLetter,
  PaymentLetterSummary,
  FulfillmentInput,
  FulfillmentRequest,
  FulfillmentStep,
  CoreReturn,
  InvoiceDetail,
  ReturnCaseDraft,
  StockAvailability,
  WarrantyClaim,
  WarrantyDecision,
  WarrantySerial,
  ReceiptContacts,
  ReceiptDocument,
  SaleSetup,
  TenderLine,
  Warehouse,
  PopularPin,
  PosCart,
  PosCurrency,
  PosPart,
  PosResult,
  SelectedVehicle,
  VehicleModel,
  VehicleVariant,
  ApprovalPolicy,
  Governed,
  ContipayMethod,
  DigitalProvider,
  ManualTenderLine,
  PaymentStatus,
  PaynowMethod,
  PickupOrder,
  ProviderAvailability,
  ProviderStart,
  RecoveryItem,
  ApprovalTrailRow,
  ApproverStatus,
  BadgeAction,
  IssuedBadge,
  ManagerBadge,
  ManagerCandidate,
  RefundFeePolicy,
  SplitRecoveryItem,
  SplitRefundStep,
  SplitSession,
  SplitTender,
  CardTerminal,
  CardTerminalInput,
  TerminalRecoveryItem,
} from "@/lib/pos/types";

/**
 * Typed POS gateway — one method per backend capability (Rev 1.5 §10.2). The UI never calls
 * Supabase directly. Parity matrix: `docs/design/pos/WEB_POS_PARITY.md` §3.
 */
export interface PosGateway {
  readonly isPreview: boolean;

  // Vehicle cascade (Model → Generation → Engine; Make never shown)
  listModels(): Promise<PosResult<VehicleModel[]>>;
  listVariants(modelSlug: string): Promise<PosResult<VehicleVariant[]>>;

  // Discovery
  searchParts(query: string, vehicle: SelectedVehicle | null): Promise<PosResult<PosPart[]>>;
  listBestSellers(): Promise<PosResult<PosPart[]>>;
  listPins(): Promise<PosResult<PopularPin[]>>;
  pin(pin: PopularPin): Promise<PosResult<true>>;
  unpin(pin: PopularPin): Promise<PosResult<true>>;
  listHiddenBestSellers(): Promise<PosResult<string[]>>;
  hideBestSeller(stockItemId: string): Promise<PosResult<true>>;
  unhideBestSeller(stockItemId: string): Promise<PosResult<true>>;

  // Current Sale
  listWarehouses(): Promise<PosResult<Warehouse[]>>;
  openCart(setup: SaleSetup): Promise<PosResult<PosCart>>;
  loadCart(cartId: string): Promise<PosResult<PosCart>>;
  addPart(cartId: string, part: PosPart, qty: number): Promise<PosResult<PosCart>>;
  setLineQty(cartId: string, lineId: string, qty: number): Promise<PosResult<PosCart>>;
  removeLine(cartId: string, lineId: string): Promise<PosResult<PosCart>>;
  setCartVehicle(cartId: string, vehicle: SelectedVehicle | null): Promise<PosResult<PosCart>>;
  // Governed actions (`*_governed`): a configured reason always; the manager's isolated session when
  // `pos_action_requires_manager` says so, otherwise the cashier's own.
  voidCart(cartId: string, g: Governed): Promise<PosResult<true>>;
  applyDiscount(cartId: string, percent: number, g: Governed): Promise<PosResult<PosCart>>;
  overrideLinePrice(cartId: string, lineId: string, unitPrice: number, g: Governed): Promise<PosResult<PosCart>>;
  /** Policy check before asking for a manager; [value] is the action's measure (percent). */
  requiresManager(action: string, value: number): Promise<PosResult<boolean>>;
  listApprovalPolicies(): Promise<PosResult<ApprovalPolicy[]>>;
  /** Admin only (server-enforced). */
  setApprovalPolicy(policy: Omit<ApprovalPolicy, "updatedAt">): Promise<PosResult<true>>;

  // Customers
  searchCustomers(query: string): Promise<PosResult<PosCustomer[]>>;
  createCustomer(input: CustomerInput): Promise<PosResult<PosCustomer>>;
  updateCustomer(id: string, input: CustomerInput): Promise<PosResult<PosCustomer>>;
  setCartCustomer(cartId: string, customerId: string | null): Promise<PosResult<PosCart>>;
  listGarage(customerId: string): Promise<PosResult<GarageVehicle[]>>;
  saveGarageVehicle(customerId: string, vehicle: SelectedVehicle, isPrimary: boolean): Promise<PosResult<true>>;

  // Companion phone (scan sessions; the phone scans with its own camera — never the browser)
  createScanSession(cartId: string): Promise<PosResult<ScanSession>>;
  revokeScanSession(sessionId: string): Promise<PosResult<true>>;
  /** Live updates while a companion is paired: cart lines and session status. Returns unsubscribe. */
  watchCompanion(cartId: string, sessionId: string, onCart: () => void, onStatus: (status: string) => void): () => void;

  // Payment
  // Reserve-first checkout (Blueprint §10.6): reserve stock and lock the sale, then take money against
  // the reserved order. [requestId] / [paymentRequestId] are idempotency keys: a retry returns the same result.
  prepareCheckout(cartId: string, requestId: string, contacts: ReceiptContacts): Promise<PosResult<string>>;
  paymentStatus(orderId: string): Promise<PosResult<PaymentStatus>>;
  /** Cash, card/bank and store credit; must equal the order total. */
  settleTenders(orderId: string, paymentRequestId: string, tenders: ManualTenderLine[]): Promise<PosResult<{ invoiceId: string; state: string }>>;
  /** Which digital providers this shop has set up (a provider without keys answers 503). */
  providerAvailability(): Promise<PosResult<ProviderAvailability>>;
  startProvider(
    orderId: string,
    provider: DigitalProvider,
    params: { msisdn?: string; method?: PaynowMethod | ContipayMethod; returnUrl?: string },
  ): Promise<PosResult<ProviderStart>>;
  /** Releases the reservation and unlocks the sale; refused while money is in flight. */
  cancelCheckout(orderId: string, reason: string): Promise<PosResult<true>>;
  /** On-account sale for a registered customer with credit (server checks limit, hold, currency). */
  checkoutOnAccount(cartId: string, contacts: ReceiptContacts): Promise<PosResult<string>>;
  loadReceipt(invoiceId: string): Promise<PosResult<ReceiptDocument>>;

  // Payment recovery (dedicated screen) and counter pickup
  listPaymentRecovery(): Promise<PosResult<RecoveryItem[]>>;
  /** Paid at the provider but not finalised: finalise and apply the receipt. Manager or finance. */
  repairPaidOrder(orderId: string, notes: string | null, manager: ManagerCredentials | null): Promise<PosResult<string>>;

  // Part payments (staged split): money is taken part by part against the reserved order; the sale
  // posts when the parts cover it. [requestId] is the idempotency key of one part.
  findSplit(orderId: string): Promise<PosResult<SplitSession | null>>;
  startSplit(orderId: string): Promise<PosResult<SplitSession>>;
  getSplit(sessionId: string): Promise<PosResult<SplitSession>>;
  addSplitLeg(sessionId: string, tender: SplitTender, amount: number, requestId: string, reference: string | null): Promise<PosResult<SplitSession>>;
  /** The customer takes fewer items for what is already paid; the server works out the new total. */
  acceptReducedBasket(sessionId: string, items: Array<{ cartLineId: string; qty: number }>, notes: string | null): Promise<PosResult<SplitSession>>;
  /** Cancels the part-paid sale; captured parts become refunds for a manager to settle. */
  cancelSplit(sessionId: string, reason: string, feePolicy: RefundFeePolicy): Promise<PosResult<SplitSession>>;
  /** Fully paid but the invoice did not post: post it again once the cause is fixed. */
  retrySplitFinalization(sessionId: string): Promise<PosResult<SplitSession>>;
  listSplitRecovery(): Promise<PosResult<SplitRecoveryItem[]>>;
  /** Manager or finance: password sign-in for this step, or the signed-in approver when null. */
  splitRefundStep(refundId: string, step: SplitRefundStep, manager: ManagerCredentials | null): Promise<PosResult<SplitSession>>;

  // Card machines (ECR). Charging needs the counter tablet paired with the machine (its answers are
  // signed on the device); the browser lists, sets up (admin) and finishes approved card payments.
  listCardTerminals(): Promise<PosResult<CardTerminal[]>>;
  /** Admin: add or change a card machine (`upsert_pos_card_terminal`). */
  saveCardTerminal(t: CardTerminalInput): Promise<PosResult<string>>;
  listTerminalRecovery(): Promise<PosResult<TerminalRecoveryItem[]>>;
  /** Approved on the machine but not posted: post the sale against that charge. */
  finishTerminalPayment(attemptId: string): Promise<PosResult<{ status: string; error: string | null }>>;

  // Manager approval by ID badge, manager assignment, badges and the approval audit trail.
  /** Is the signed-in user a POS manager (then no approval prompt is needed)? */
  approverStatus(): Promise<PosResult<ApproverStatus>>;
  /** Run one governed action approved by a scanned badge; refusals come back with the manager's name when known. */
  badgeApprove(payload: string, action: BadgeAction, args: Record<string, unknown>, deviceId: string): Promise<PosResult<{ managerName: string | null }>>;
  listManagerCandidates(): Promise<PosResult<ManagerCandidate[]>>;
  /** Assign or remove an employee as an approver (admin or HR). */
  setManagerAssignment(employeeId: string, assigned: boolean, notes: string | null): Promise<PosResult<true>>;
  /** Badge for an employee approver, or for an admin user with no employee record. */
  issueBadge(holder: { employeeId: string } | { userId: string }, label: string | null, validDays: number): Promise<PosResult<IssuedBadge>>;
  revokeBadge(badgeId: string, reason: string): Promise<PosResult<true>>;
  listBadges(): Promise<PosResult<ManagerBadge[]>>;
  approvalTrail(limit: number): Promise<PosResult<ApprovalTrailRow[]>>;
  listPickupOrders(query: string): Promise<PosResult<PickupOrder[]>>;
  collectOrder(orderId: string, notes: string | null): Promise<PosResult<true>>;

  // Orders
  parkCart(cartId: string): Promise<PosResult<true>>;
  listParked(): Promise<PosResult<ParkedCart[]>>;
  resumeCart(cartId: string): Promise<PosResult<PosCart>>;
  listQuotations(): Promise<PosResult<Quotation[]>>;
  createQuotation(cartId: string, validUntil: string | null, notes: string | null): Promise<PosResult<string>>;
  sendQuotation(quotationId: string, channel: "email" | "sms" | "whatsapp", contact: string | null): Promise<PosResult<true>>;
  convertQuotation(quotationId: string): Promise<PosResult<PosCart>>;

  // Returns
  listRecentInvoices(query: string): Promise<PosResult<RecentInvoice[]>>;
  refundInvoice(invoiceId: string, g: Governed): Promise<PosResult<string>>;
  /** Lines of a posted sale with what can still be returned (core charges listed separately). */
  getInvoiceDetail(invoiceId: string): Promise<PosResult<InvoiceDetail>>;
  /** Sales staff draft the return; it does nothing until a manager posts it. */
  createReturnCase(draft: ReturnCaseDraft): Promise<PosResult<string>>;
  /** Manager: post the drafted return (credit note, refund, store credit, replacement or warranty). */
  postReturnCase(caseId: string, manager: ManagerCredentials | null): Promise<PosResult<true>>;
  /** Manager: take back an old core and give back its core charge. */
  postCoreReturn(input: CoreReturn, manager: ManagerCredentials | null): Promise<PosResult<true>>;
  openWarrantyClaim(invoiceId: string, invoiceLineId: string, serialId: string | null, notes: string | null): Promise<PosResult<string>>;
  findWarrantySerial(serial: string): Promise<PosResult<WarrantySerial[]>>;
  listWarrantyClaims(query: string, status: string | null): Promise<PosResult<WarrantyClaim[]>>;
  /** Manager: approve (replacement / credit note / return only) or reject an open claim. */
  decideWarrantyClaim(claim: WarrantyClaim, decision: WarrantyDecision, manager: ManagerCredentials | null): Promise<PosResult<true>>;
  closeWarrantyClaim(claimId: string): Promise<PosResult<true>>;

  // Stock
  listStockAvailability(stockItemId: string): Promise<PosResult<StockAvailability[]>>;

  // Payment resolution letters (manager / finance / admin, signed with the issuer's own signature)
  listLetters(sourceKind: LetterSourceKind | null, sourceId: string | null, query: string): Promise<PosResult<PaymentLetterSummary[]>>;
  issueLetter(sourceKind: LetterSourceKind, sourceId: string, notes: string | null): Promise<PosResult<string>>;
  getLetter(letterId: string): Promise<PosResult<PaymentLetter>>;
  getMySignature(): Promise<PosResult<MySignature>>;
  /** PNG or JPEG; stored privately under the signed-in user's own folder, then registered with its hash. */
  saveMySignature(image: Blob): Promise<PosResult<MySignature>>;
  getBusinessProfile(): Promise<PosResult<BusinessProfile>>;
  /** Admin only (server-enforced). */
  setBusinessProfile(profile: BusinessProfile): Promise<PosResult<BusinessProfile>>;

  // Fulfilment: holds, other-branch pickup, branch transfers and back-orders
  createFulfillment(input: FulfillmentInput): Promise<PosResult<string>>;
  listFulfillment(query: string, status: string | null): Promise<PosResult<FulfillmentRequest[]>>;
  /** approve: warehouse staff send a branch transfer; ready / collect / cancel: sales staff. */
  fulfillmentStep(requestId: string, step: FulfillmentStep, notes: string | null): Promise<PosResult<true>>;
  /** The customer's suspension for failing to settle, if any (credit, holds and back-orders are then refused). */
  customerSuspension(customerId: string): Promise<PosResult<{ reason: string; owing: number } | null>>;
  /** An arrived back-order sold on this sale: when the sale posts, the request gets its invoice and can be handed over. */
  attachFulfillmentToSale(requestId: string, cartId: string): Promise<PosResult<true>>;

  // EPC Browse (Nissan only — make is never chosen)
  listEpcVariants(modelSlug: string): Promise<PosResult<VehicleVariant[]>>;
  listEpcSections(modelSlug: string, variantSlug: string): Promise<PosResult<EpcSection[]>>;
  listEpcDiagrams(modelSlug: string, variantSlug: string, sectionSlug: string): Promise<PosResult<EpcDiagramRef[]>>;
  getEpcDiagram(modelSlug: string, variantSlug: string, sectionSlug: string, diagram: EpcDiagramRef): Promise<PosResult<EpcDiagram>>;

  // Till (cash drawer) — one open till per device and per operator; blind close count.
  getMyTill(deviceId: string): Promise<PosResult<TillSession | null>>;
  openTill(warehouseId: string, deviceId: string, openingFloat: number, currency: PosCurrency): Promise<PosResult<TillSession>>;
  attachCartToTill(cartId: string, sessionId: string): Promise<PosResult<true>>;
  /** Reasons for a governed action (`till_variance`, `cash_out`, `void_cart`, …). */
  listReasons(action: string): Promise<PosResult<ReasonCode[]>>;
  /** Cash in runs as the operator; every cash-out kind needs a manager. */
  recordCashMovement(
    sessionId: string,
    kind: CashMovementKind,
    amount: number,
    reasonCode: string,
    notes: string | null,
    manager: ManagerCredentials | null,
  ): Promise<PosResult<true>>;
  closeTill(sessionId: string, counts: DenominationCount[], varianceReasonCode: string | null, notes: string | null): Promise<PosResult<TillCloseResult>>;
  approveTillVariance(sessionId: string, reasonCode: string, notes: string | null, manager: ManagerCredentials | null): Promise<PosResult<true>>;
  listHandoverOperators(): Promise<PosResult<HandoverOperator[]>>;
  handoverTill(sessionId: string, newOperatorUserId: string, notes: string | null, manager: ManagerCredentials | null): Promise<PosResult<true>>;
  listTillSessions(status: TillSession["status"] | null): Promise<PosResult<TillSession[]>>;

  operatorLabel(): Promise<string>;
  /** Staff portal second login (owner decision D4): re-enter the password before management opens. */
  reauthenticate(password: string): Promise<PosResult<true>>;
}

export type ScanSession = { sessionId: string; pairingCode: string; expiresAt: string };
