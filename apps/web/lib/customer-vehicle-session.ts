import type { SelectedFitmentVehicle } from "@/lib/vehicle-catalog";

export const CUSTOMER_SELECTED_VEHICLE_KEY = "gtr:selected-vehicle:v2";

export function saveCustomerVehicle(vehicle: SelectedFitmentVehicle): void {
  if (typeof window === "undefined") return;
  window.localStorage.setItem(CUSTOMER_SELECTED_VEHICLE_KEY, JSON.stringify(vehicle));
  window.dispatchEvent(new CustomEvent("gtr:selected-vehicle", { detail: vehicle }));
}

export function loadCustomerVehicle(): SelectedFitmentVehicle | null {
  if (typeof window === "undefined") return null;
  const raw = window.localStorage.getItem(CUSTOMER_SELECTED_VEHICLE_KEY);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as SelectedFitmentVehicle;
    if (!parsed?.generation?.trim() || !parsed?.model?.trim()) return null;
    return parsed;
  } catch {
    return null;
  }
}

export function clearCustomerVehicle(): void {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(CUSTOMER_SELECTED_VEHICLE_KEY);
  window.dispatchEvent(new CustomEvent("gtr:selected-vehicle", { detail: null }));
}
