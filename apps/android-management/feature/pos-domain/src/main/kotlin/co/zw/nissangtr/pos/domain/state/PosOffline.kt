package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.OfflineQueued
import co.zw.nissangtr.pos.domain.model.Receipt

/*
 * Offline restricted mode (Blueprint §10.12). While the till is offline a walk-in cash sale is
 * built in a local cart priced from the last catalogue snapshot. It is the one place the till adds
 * up a total itself; the outbox replays each line with its expected unit price and the server
 * refuses drift as a conflict for review, so a stale price can never post silently.
 *
 * - Cash only, walk-in only. Discount, void, refund, price override, park and quote stay online.
 * - A sale started online is not converted: it waits for the connection (or is cleared).
 * - When the connection returns, an unpaid local cart moves to a server cart and the outbox drains.
 */

/** Marker id of the local (offline) cart; the server never issues it. */
const val LOCAL_CART_ID = "local-offline-cart"

val CartProjection.isLocal: Boolean get() = cartId == LOCAL_CART_ID

internal fun offlineRefusal(state: PosState, reason: String) =
    Reduction(state.copy(feedback = PosFeedback.Failure(PosError.OfflineRestricted(setOf(reason)))))

internal fun localCart(currency: CurrencyCode, lines: List<CartLine>): CartProjection {
    if (lines.isEmpty()) return CartProjection.empty(currency)
    val total = Money(lines.sumOf { it.lineTotal.minor }, currency)
    return CartProjection(LOCAL_CART_ID, currency, lines, total, Money.zero(currency), total)
}

private fun lineTotal(unit: Money, qty: Double) = Money(Math.round(unit.minor * qty), unit.currency)

/** Add one of [part] to the local cart, merging with an existing line. */
internal fun localAdd(state: PosState, part: CatalogPart): Reduction {
    val cart = state.cart
    val price = part.price
    val stockItemId = part.stockItemId
    return when {
        !cart.isEmpty && !cart.isLocal -> offlineRefusal(state, "server_cart")
        part.outOfStock ->
            Reduction(state.copy(feedback = PosFeedback.Failure(PosError.BusinessRule("out_of_stock", part.oemPartNumber))))
        price == null || stockItemId == null ->
            Reduction(state.copy(feedback = PosFeedback.Failure(PosError.BusinessRule("part_not_sellable", part.oemPartNumber))))
        !cart.isEmpty && price.currency != cart.currency -> offlineRefusal(state, "currency")
        else -> {
            val existing = cart.lines.firstOrNull { it.stockItemId == stockItemId }
            val qty = (existing?.qty ?: 0.0) + 1.0
            val stock = part.saleableQty
            if (stock != null && qty > stock) {
                Reduction(state.copy(feedback = PosFeedback.Failure(PosError.BusinessRule("offline_stock", part.oemPartNumber))))
            } else {
                val line = CartLine(
                    lineId = "local-$stockItemId",
                    stockItemId = stockItemId,
                    oemPartNumber = part.oemPartNumber,
                    name = part.name,
                    qty = qty,
                    unitPrice = price,
                    lineTotal = lineTotal(price, qty),
                    isCoreCharge = false,
                    imageUrl = part.imageUrl,
                )
                val lines = if (existing == null) cart.lines + line else cart.lines.map { if (it.lineId == line.lineId) line else it }
                Reduction(state.copy(cart = localCart(price.currency, lines), feedback = null))
            }
        }
    }
}

/** Set a local line's quantity; zero or less removes it. */
internal fun localSetQuantity(state: PosState, lineId: String, qty: Double): Reduction {
    val cart = state.cart
    val lines = if (qty <= 0.0) {
        cart.lines.filterNot { it.lineId == lineId }
    } else {
        cart.lines.map { if (it.lineId == lineId) it.copy(qty = qty, lineTotal = lineTotal(it.unitPrice, qty)) else it }
    }
    return Reduction(state.copy(cart = localCart(cart.currency, lines)))
}

/** Connectivity changes: going online moves an unpaid local cart to the server and drains the outbox. */
internal fun reduceConnectivity(state: PosState, online: Boolean): Reduction {
    if (online == state.online) return Reduction(state)
    if (!online) return Reduction(state.copy(online = false))
    val back = state.copy(online = true)
    return if (state.cart.isLocal && !state.cart.isEmpty && !state.paying) {
        Reduction(
            back.copy(cart = CartProjection.empty(state.cart.currency), cartBusy = back.cartBusy + 1),
            listOf(PosSaleEffect.PromoteLocalCart(state.cart), PosSaleEffect.SyncOffline),
        )
    } else {
        Reduction(back, listOf(PosSaleEffect.SyncOffline))
    }
}

/** The receipt for a queued offline sale; its invoice number arrives with the replay. */
internal fun offlineReceipt(effect: PosSaleEffect.QueueOfflineSale, queued: OfflineQueued): Receipt {
    val cart = effect.cart
    val change = effect.cashGiven?.let { given -> Money((given.minor - cart.total.minor).coerceAtLeast(0), cart.currency) }
    return Receipt(
        invoiceId = queued.clientSaleId,
        documentNumber = "OFFLINE-" + queued.clientSaleId.replace("-", "").take(8).uppercase(),
        lines = cart.lines,
        subtotal = cart.subtotal,
        discount = cart.discount,
        total = cart.total,
        tenders = effect.tenders,
        cashGiven = effect.cashGiven,
        change = change,
        customerName = null,
        vehicleLabel = effect.vehicle?.label,
        operatorName = effect.operatorName,
        issuedAtIso = queued.soldAtIso,
        offline = true,
    )
}

/** The server cart id, or null for no cart or the local offline cart. */
val CartProjection.serverCartId: String? get() = cartId.takeIf { it.isNotEmpty() && !isLocal }
