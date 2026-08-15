package co.zw.nissangtr.pos.api

/**
 * §16.3 / §5.3 add classification — priced/stock/fitment before cart mutation.
 */
enum class LineClass {
    /** Priced + WH2 covers + (FITS | confirmed VERIFY). */
    SELLABLE,
    /** Priced + (OOS or force quote). May add_cart_line; CTA = QUOTE. */
    QUOTE_ONLY,
    /** Unpriced — no line. */
    REJECTED_UNPRICED,
    /** NO_FIT or unconfirmed VERIFY — block silent add. */
    REJECTED_BLOCKED,
}

sealed class AddDecision {
    data class Allow(
        val lineClass: LineClass,
        val item: TillItem,
        /** Banner when [TillItem.supersededBy] present — hydrate that OEM instead. */
        val supersessionBanner: String? = null,
    ) : AddDecision()

    data class Reject(
        val reason: LineClass,
        val message: String,
        val supersessionBanner: String? = null,
    ) : AddDecision()
}

object AddLineRules {

    fun decide(
        item: TillItem,
        latch: VehicleLatch?,
        qty: Int = 1,
        verifyConfirmed: Boolean = false,
        forceQuote: Boolean = false,
        autoConfirmVerify: Boolean = false,
    ): AddDecision {
        val banner = item.supersededBy
            ?.takeIf { it.isNotBlank() }
            ?.let { "Use $it instead" }

        val badge = FitmentRules.badge(item, latch)
        val confirmed = verifyConfirmed ||
            (autoConfirmVerify && badge == FitmentBadge.VERIFY)

        val price = item.unitPrice
        if (price == null || price <= 0.0) {
            return AddDecision.Reject(
                reason = LineClass.REJECTED_UNPRICED,
                message = "Needs price",
                supersessionBanner = banner,
            )
        }
        if (badge == FitmentBadge.NO_FIT) {
            return AddDecision.Reject(
                reason = LineClass.REJECTED_BLOCKED,
                message = "Does not fit latched vehicle",
                supersessionBanner = banner,
            )
        }
        if (badge == FitmentBadge.VERIFY && !confirmed) {
            return AddDecision.Reject(
                reason = LineClass.REJECTED_BLOCKED,
                message = "Confirm fitment before adding",
                supersessionBanner = banner,
            )
        }

        val lineClass = when {
            forceQuote || item.saleableQty < qty -> LineClass.QUOTE_ONLY
            else -> LineClass.SELLABLE
        }
        return AddDecision.Allow(
            lineClass = lineClass,
            item = item,
            supersessionBanner = banner,
        )
    }
}

enum class TicketCta {
    PAY,
    QUOTE,
}

object TicketCtaResolver {
    /**
     * All sellable (or empty) → PAY; any quote-only product line → QUOTE.
     * Core-charge children are ignored.
     */
    fun resolve(lines: List<TicketLine>): TicketCta {
        val product = lines.filter { !it.isCoreCharge }
        if (product.any { it.isQuoteOnly }) return TicketCta.QUOTE
        return TicketCta.PAY
    }
}
