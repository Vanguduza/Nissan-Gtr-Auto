package co.zw.nissangtr.pos.domain.model

data class CartLine(
    val lineId: String,
    val stockItemId: String,
    val oemPartNumber: String,
    val name: String,
    val qty: Double,
    val unitPrice: Money,
    val lineTotal: Money,
    val isCoreCharge: Boolean,
    val imageUrl: String?,
)

/**
 * Server cart as returned after every mutation. Totals are the backend's numbers; the discount row
 * is always present, zero included (CART-08), and there is no tax row (no fiscal tax in scope).
 */
data class CartProjection(
    val cartId: String,
    val currency: CurrencyCode,
    val lines: List<CartLine>,
    val subtotal: Money,
    val discount: Money,
    val total: Money,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    companion object {
        fun empty(currency: CurrencyCode): CartProjection = CartProjection(
            cartId = "",
            currency = currency,
            lines = emptyList(),
            subtotal = Money.zero(currency),
            discount = Money.zero(currency),
            total = Money.zero(currency),
        )
    }
}

data class Operator(val displayName: String, val roleLabel: String)
