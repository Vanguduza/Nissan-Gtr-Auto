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
  /** Void needs an approver (`is_pos_approver`) — runs on the manager's isolated session. */
  voidCart(cartId: string, manager: ManagerCredentials, notes: string | null): Promise<PosResult<true>>;

  // Manager-gated pricing
  applyDiscount(cartId: string, percent: number, manager: ManagerCredentials, notes: string | null): Promise<PosResult<PosCart>>;
  overrideLinePrice(cartId: string, lineId: string, unitPrice: number, manager: ManagerCredentials, notes: string | null): Promise<PosResult<PosCart>>;

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
  checkout(cartId: string, tenders: TenderLine[], contacts: ReceiptContacts): Promise<PosResult<string>>;
  loadReceipt(invoiceId: string): Promise<PosResult<ReceiptDocument>>;
  requestEcocash(invoiceId: string, msisdn: string, amount: number, currency: PosCurrency, customerId: string | null): Promise<PosResult<string>>;

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
  refundInvoice(invoiceId: string, manager: ManagerCredentials, notes: string | null): Promise<PosResult<string>>;

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
  approveTillVariance(sessionId: string, reasonCode: string, notes: string | null, manager: ManagerCredentials): Promise<PosResult<true>>;
  listHandoverOperators(): Promise<PosResult<HandoverOperator[]>>;
  handoverTill(sessionId: string, newOperatorUserId: string, notes: string | null, manager: ManagerCredentials): Promise<PosResult<true>>;
  listTillSessions(status: TillSession["status"] | null): Promise<PosResult<TillSession[]>>;

  operatorLabel(): Promise<string>;
  /** Staff portal second login (owner decision D4): re-enter the password before management opens. */
  reauthenticate(password: string): Promise<PosResult<true>>;
}

export type ScanSession = { sessionId: string; pairingCode: string; expiresAt: string };
