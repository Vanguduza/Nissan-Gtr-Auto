import type {
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
  openCart(currency: PosCurrency): Promise<PosResult<PosCart>>;
  loadCart(cartId: string): Promise<PosResult<PosCart>>;
  addPart(cartId: string, part: PosPart, qty: number): Promise<PosResult<PosCart>>;
  setLineQty(cartId: string, lineId: string, qty: number): Promise<PosResult<PosCart>>;
  removeLine(cartId: string, lineId: string): Promise<PosResult<PosCart>>;
  setCartVehicle(cartId: string, vehicle: SelectedVehicle | null): Promise<PosResult<PosCart>>;
  voidCart(cartId: string): Promise<PosResult<true>>;

  operatorLabel(): Promise<string>;
  /** Staff portal second login (owner decision D4): re-enter the password before management opens. */
  reauthenticate(password: string): Promise<PosResult<true>>;
}
