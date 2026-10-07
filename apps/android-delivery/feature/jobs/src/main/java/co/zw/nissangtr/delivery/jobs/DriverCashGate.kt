package co.zw.nissangtr.delivery.jobs

import co.zw.nissangtr.delivery.rpc.DriverCash
import co.zw.nissangtr.delivery.rpc.DriverCashHandin
import co.zw.nissangtr.delivery.rpc.DriverCashHolding
import java.time.Duration
import java.time.Instant
import java.util.Locale

/** Wording and rules for the cash a driver collected on delivery and hands in at the branch. */
object DriverCashGate {
    fun money(amount: Double, currency: String): String = String.format(Locale.US, "%s %.2f", currency.uppercase(), amount)

    /** The driver's own count; null when it is not an amount. Any amount ≥ 0 is allowed — the cashier's count decides. */
    fun parseDeclared(text: String): Double? {
        val v = text.trim().replace(",", ".").toDoubleOrNull() ?: return null
        return (Math.round(v * 100) / 100.0).takeIf { it >= 0 }
    }

    /** Short value for the Account row: what is held, or the hand-in being counted. */
    fun summary(cash: DriverCash?): String = when {
        cash == null -> ""
        cash.holding.isNotEmpty() -> cash.holding.joinToString(" · ") { money(it.amount, it.currency) }
        cash.handins.any { it.status == "submitted" } -> "Being counted"
        else -> "None"
    }

    /** "Held 3 h" style age of the oldest collection still held. */
    fun age(holding: DriverCashHolding, now: Instant = Instant.now()): String? {
        val oldest = holding.oldestAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
        val minutes = Duration.between(oldest, now).toMinutes().coerceAtLeast(0)
        return when {
            minutes < 60 -> "for $minutes min"
            minutes < 48 * 60 -> "for ${minutes / 60} h"
            else -> "for ${minutes / (24 * 60)} days"
        }
    }

    fun statusLabel(h: DriverCashHandin): String = when (h.status) {
        "submitted" -> "Waiting to be counted"
        "received" -> "Counted — matches"
        "variance_pending" -> "Counted ${money(h.receivedAmount ?: 0.0, h.currency)} — manager to review the difference"
        "approved" -> "${difference(h.variance ?: 0.0, h.currency)} signed off by a manager"
        else -> h.status
    }

    /** "USD 5.00 short" / "USD 2.00 over". */
    fun difference(variance: Double, currency: String): String =
        if (variance < 0) "${money(-variance, currency)} short" else "${money(variance, currency)} over"

    /** Why the hand-in button is off, or null when the driver can hand in [currency]. */
    fun blockedReason(cash: DriverCash?, currency: String): String? = when {
        cash == null -> "Loading…"
        cash.waiting(currency) != null -> "Your last ${currency.uppercase()} hand-in is still being counted."
        cash.holding.none { it.currency == currency } -> "No ${currency.uppercase()} cash to hand in."
        else -> null
    }
}
