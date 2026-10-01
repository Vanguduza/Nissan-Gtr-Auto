"use client";

import { ArrowRight, Car, CreditCard, Minus, Plus, Trash2, User, X } from "lucide-react";
import { formatMoney } from "@/lib/pos/money";
import type { PosStore } from "@/lib/pos/use-pos";
import { PartThumb } from "./PartCard";
import styles from "./pos.module.css";

/**
 * Persistent Current Sale pane. Prices are explicitly the UNIT price (delta D-012); there is no
 * tax row — invoices are tax-agnostic and currency comes from the cart (delta D-006).
 */
export function CurrentSale({
  pos,
  onAddCustomer,
  onPay,
}: {
  pos: PosStore;
  onAddCustomer: () => void;
  onPay: () => void;
}) {
  const lines = pos.cart?.lines ?? [];
  const currency = pos.currency;
  const locked = !pos.online;

  return (
    <aside className={styles.cart} aria-label="Current sale">
      <div className={styles.cartHead}>
        <h2 className={styles.cartTitle}>Current Sale</h2>
        <button
          type="button"
          className={`${styles.textLink} ${styles.clear}`}
          disabled={lines.length === 0 || locked}
          onClick={() => void pos.clearSale()}
        >
          <Trash2 size={16} aria-hidden /> Clear
        </button>
      </div>

      {pos.vehicle ? (
        <div className={styles.vehicleChip}>
          <Car size={16} aria-hidden />
          <span>
            {pos.vehicle.modelName} · {pos.vehicle.chassisCode} · {pos.vehicle.engineCode}
          </span>
          <button
            type="button"
            className={`${styles.iconButton} ${styles.statusDismiss}`}
            aria-label="Clear vehicle"
            onClick={pos.clearVehicle}
          >
            <X size={14} aria-hidden />
          </button>
        </div>
      ) : null}

      {lines.length === 0 ? (
        <div className={styles.cartEmpty}>
          Search, scan, choose a Popular Item or browse EPC to add parts.
        </div>
      ) : (
        <div className={styles.lines}>
          {lines.map((line) => (
            <div key={line.id} className={styles.line}>
              <PartThumb src={line.imageUrl} className={styles.lineThumb} />
              <div>
                <div className={styles.partName}>{line.name}</div>
                <div className={styles.partOem}>{line.oemPartNumber}</div>
                <div className={styles.partPrice}>{formatMoney(line.unitPrice, currency)}</div>
              </div>
              <div className={styles.lineRight}>
                <button
                  type="button"
                  className={styles.iconButton}
                  aria-label={`Remove ${line.name}`}
                  disabled={locked}
                  onClick={() => void pos.removeLine(line.id)}
                >
                  <Trash2 size={16} aria-hidden />
                </button>
                <div className={styles.stepper} role="group" aria-label={`Quantity of ${line.name}`}>
                  <button
                    type="button"
                    className={styles.stepperButton}
                    aria-label="Decrease quantity"
                    disabled={locked}
                    onClick={() => void pos.setLineQty(line.id, line.qty - 1)}
                  >
                    <Minus size={14} aria-hidden />
                  </button>
                  <span className={styles.stepperValue}>{line.qty}</span>
                  <button
                    type="button"
                    className={`${styles.stepperButton} ${styles.stepperPlus}`}
                    aria-label="Increase quantity"
                    disabled={locked}
                    onClick={() => void pos.setLineQty(line.id, line.qty + 1)}
                  >
                    <Plus size={14} aria-hidden />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      <button type="button" className={`${styles.softButton} ${styles.addCustomer}`} onClick={onAddCustomer}>
        <User size={16} aria-hidden /> {pos.cart?.customerName ? pos.cart.customerName : "Add Customer"}
      </button>

      <div className={styles.totals}>
        <div className={styles.totalRow}>
          <span>Subtotal</span>
          <strong>{formatMoney(pos.subtotal, currency)}</strong>
        </div>
        <div className={styles.totalRow}>
          <span>Discount</span>
          <span>{formatMoney(0, currency)}</span>
        </div>
      </div>
      <div className={styles.grandTotal}>
        <span className={styles.grandLabel}>Total</span>
        <span className={styles.grandValue}>{formatMoney(pos.subtotal, currency)}</span>
      </div>
      <button type="button" className={styles.pay} disabled={lines.length === 0 || locked} onClick={onPay}>
        <CreditCard size={20} aria-hidden /> Proceed to Payment <ArrowRight size={20} aria-hidden />
      </button>
    </aside>
  );
}
