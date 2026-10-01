package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CompanionSession
import co.zw.nissangtr.pos.domain.model.CompanionStatus
import co.zw.nissangtr.pos.domain.model.ScannerLink

/*
 * Companion phone (D4, W-002): the till shows a six-digit pairing code bound to the open sale; a
 * staff phone claims it and scans parts into that cart with its own camera. The till never opens a
 * browser camera; it watches the session and the cart, and ends the pairing when the sale ends.
 */

sealed interface CompanionIntent : PosSaleIntent {
    /** Open the pairing dialog; creates a session (and a cart if needed) when none is live. */
    data object Open : CompanionIntent
    data object Close : CompanionIntent
    /** Revoke the phone's access to this sale. */
    data object End : CompanionIntent

    // Phone side
    data object OpenScanner : CompanionIntent
    data object CloseScanner : CompanionIntent
    data class Claim(val pairingCode: String) : CompanionIntent
    /** A camera scan while linked: goes to the till's sale, not to this device's search. */
    data class Scanned(val payload: String) : CompanionIntent
    data object LeaveScanner : CompanionIntent
}

sealed interface CompanionEvent : PosSaleEvent {
    data class Created(val session: CompanionSession, val cart: CartProjection?) : CompanionEvent
    data class Polled(val sessionId: String, val status: CompanionStatus, val cart: CartProjection?) : CompanionEvent
    data class Failed(val error: PosError) : CompanionEvent
    data class Claimed(val link: ScannerLink) : CompanionEvent
    data class ScanAdded(val added: String) : CompanionEvent
    data class ScannerFailed(val error: PosError) : CompanionEvent
}

sealed interface CompanionEffect : PosSaleEffect {
    /** [cartId] null: open a server cart first (the phone needs a sale to scan into). */
    data class Create(val cartId: String?) : CompanionEffect
    data class Revoke(val sessionId: String) : CompanionEffect
    /** Poll status and cart while the session is live; the store stops when it is not. */
    data class Watch(val sessionId: String, val cartId: String) : CompanionEffect
    data class ClaimCode(val pairingCode: String) : CompanionEffect
    data class AddFromQr(val cartId: String, val payload: String) : CompanionEffect
}

internal fun reduceCompanionIntent(state: PosState, intent: CompanionIntent): Reduction = when (intent) {
    CompanionIntent.Open -> when {
        state.companion?.live == true -> Reduction(state.copy(companionOpen = true))
        !state.online -> offlineRefusal(state, "online_only")
        state.cart.isLocal -> offlineRefusal(state, "local_cart_online")
        state.companionPairing -> Reduction(state.copy(companionOpen = true))
        else -> Reduction(
            state.copy(companionOpen = true, companionPairing = true, companion = null),
            listOf(CompanionEffect.Create(state.cart.serverCartId)),
        )
    }

    CompanionIntent.Close -> Reduction(state.copy(companionOpen = false))

    CompanionIntent.End -> endCompanion(state.copy(companionOpen = false))

    CompanionIntent.OpenScanner -> Reduction(state.copy(scannerOpen = true))
    CompanionIntent.CloseScanner -> Reduction(state.copy(scannerOpen = false))
    CompanionIntent.LeaveScanner -> Reduction(state.copy(scanner = null, scannerOpen = false))

    is CompanionIntent.Claim -> {
        val code = intent.pairingCode.filter { it.isDigit() }
        when {
            code.length != 6 -> Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("pairing_code", "six_digits"))))
            !state.online -> offlineRefusal(state, "online_only")
            state.scannerClaiming -> Reduction(state)
            else -> Reduction(state.copy(scannerClaiming = true, feedback = null), listOf(CompanionEffect.ClaimCode(code)))
        }
    }

    is CompanionIntent.Scanned -> {
        val link = state.scanner
        val payload = intent.payload.trim()
        when {
            link == null || payload.isEmpty() || link.busy -> Reduction(state)
            !state.online -> offlineRefusal(state, "online_only")
            else -> Reduction(state.copy(scanner = link.copy(busy = true)), listOf(CompanionEffect.AddFromQr(link.cartId, payload)))
        }
    }
}

internal fun reduceCompanionEvent(state: PosState, event: CompanionEvent): Reduction = when (event) {
    is CompanionEvent.Created -> {
        val cart = event.cart ?: state.cart
        Reduction(
            state.copy(companion = event.session, companionPairing = false, cart = cart),
            listOf(CompanionEffect.Watch(event.session.sessionId, event.session.cartId)),
        )
    }

    is CompanionEvent.Polled -> {
        val current = state.companion
        if (current == null || current.sessionId != event.sessionId) {
            Reduction(state)
        } else {
            // A phone scan lands on the server cart; take it unless the till is mid-mutation itself.
            val cart = event.cart?.takeIf { it.cartId == state.cart.cartId && state.cartBusy == 0 && !state.paying } ?: state.cart
            Reduction(state.copy(companion = current.copy(status = event.status), cart = cart))
        }
    }

    is CompanionEvent.Failed -> Reduction(
        state.copy(companionPairing = false, companionOpen = false, feedback = PosFeedback.Failure(event.error)),
    )

    is CompanionEvent.Claimed -> Reduction(state.copy(scanner = event.link, scannerClaiming = false, scannerOpen = true))

    is CompanionEvent.ScanAdded -> Reduction(
        state.copy(scanner = state.scanner?.let { it.copy(busy = false, scans = (listOf(event.added) + it.scans).take(SCAN_HISTORY)) }),
    )

    is CompanionEvent.ScannerFailed -> Reduction(
        state.copy(
            scannerClaiming = false,
            scanner = state.scanner?.copy(busy = false),
            feedback = PosFeedback.Failure(event.error),
        ),
    )
}

private const val SCAN_HISTORY = 5

private fun endCompanion(state: PosState): Reduction {
    val session = state.companion ?: return Reduction(state)
    return Reduction(
        state.copy(companion = null),
        if (session.live) listOf(CompanionEffect.Revoke(session.sessionId)) else emptyList(),
    )
}

/**
 * The pairing belongs to one sale: once the till's cart is no longer that sale (checked out,
 * parked, voided, replaced), the phone loses access. Applied after every reduction.
 */
internal fun companionFollowsCart(reduction: Reduction): Reduction {
    val session = reduction.state.companion ?: return reduction
    if (reduction.state.cart.cartId == session.cartId) return reduction
    val ended = endCompanion(reduction.state.copy(companionOpen = false))
    return Reduction(ended.state, reduction.effects + ended.effects)
}

