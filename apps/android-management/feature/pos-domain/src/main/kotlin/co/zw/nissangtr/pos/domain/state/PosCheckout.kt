package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.DigitalProvider
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.PaymentStatus
import co.zw.nissangtr.pos.domain.model.PickupOrder
import co.zw.nissangtr.pos.domain.model.ProviderAttempt
import co.zw.nissangtr.pos.domain.model.ProviderMethod
import co.zw.nissangtr.pos.domain.model.ProviderStart
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.RecoveryItem
import co.zw.nissangtr.pos.domain.model.RefundFeePolicy
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.TenderOutcome
import co.zw.nissangtr.pos.domain.model.manual

/*
 * Reserve-first checkout (Blueprint §10.5–10.7). Opening payment reserves the sale: stock is held and
 * the sale is locked (only an unreserved sale can change). Money is taken against that order: cash,
 * card/bank and store credit in one idempotent settlement; EcoCash, Paynow and ContiPay through the
 * provider, watched until it settles or fails. Every attempt ends as one outcome; Unknown blocks a
 * second charge and goes to the recovery screen. The amount due is always the server's.
 *
 * The clock and idempotency keys come from the store (events carry them), so this stays pure.
 */

/** How long a digital attempt may stay unanswered before it is Unknown (§10.7). */
const val PROVIDER_WAIT_MS: Long = 3 * 60_000

/** A reserved checkout: the server's order and the digital attempt in flight, if any. */
data class CheckoutSession(
    val orderId: String,
    val cartId: String,
    val requestId: String,
    val status: PaymentStatus,
    /** Kept after a dropped answer so the retry cannot take the money twice; cleared after a definite refusal. */
    val paymentRequestId: String? = null,
    val attempt: ProviderAttempt? = null,
    val outcome: TenderOutcome? = null,
    val message: String? = null,
    val busy: Boolean = false,
) {
    val inFlight: Boolean get() = attempt != null && outcome == null
}

sealed interface CheckoutIntent : PosSaleIntent {
    data class PayManual(val tenders: List<TenderLine>, val cashGiven: Money?, val contacts: ReceiptContacts) : CheckoutIntent
    data class PayProvider(val provider: DigitalProvider, val msisdn: String?, val method: ProviderMethod?, val contacts: ReceiptContacts) : CheckoutIntent
    data class PayOnAccount(val contacts: ReceiptContacts) : CheckoutIntent
    data object CheckNow : CheckoutIntent
    /** Back to the sale: releases the stock and unlocks editing. */
    data object Cancel : CheckoutIntent
    /** The dedicated recovery screen; [orderId] null lists everything to resolve. */
    data class OpenRecovery(val orderId: String?) : CheckoutIntent
    data object RefreshRecovery : CheckoutIntent
    data class Release(val orderId: String) : CheckoutIntent
    data class LoadPickups(val query: String) : CheckoutIntent
    /** Handed over to the customer; [newSale] also starts the next sale (receipt button). */
    data class Collect(val orderId: String, val newSale: Boolean) : CheckoutIntent
}

sealed interface CheckoutEvent : PosSaleEvent {
    data class Enabled(val enabled: Boolean) : CheckoutEvent
    data class Prepared(val orderId: String, val cartId: String, val requestId: String, val status: PaymentStatus) : CheckoutEvent
    data class PrepareFailed(val error: PosError) : CheckoutEvent
    data class ProvidersLoaded(val providers: Map<DigitalProvider, String?>) : CheckoutEvent
    /** The receipt follows as [PosSaleEvent.CheckoutDone]; this records which order it came from. */
    data class Finished(val orderId: String?) : CheckoutEvent
    data class SettleFailed(val error: PosError, val paymentRequestId: String, val network: Boolean) : CheckoutEvent
    data class ProviderStarted(val provider: DigitalProvider, val start: ProviderStart, val atMs: Long) : CheckoutEvent
    data class ProviderFailed(val error: PosError) : CheckoutEvent
    data class Polled(val status: PaymentStatus, val nowMs: Long, val reservationExpired: Boolean) : CheckoutEvent
    data object Cancelled : CheckoutEvent
    data class CancelFailed(val error: PosError) : CheckoutEvent
    data class RecoveryLoaded(val items: List<RecoveryItem>) : CheckoutEvent
    data class RecoveryStatusLoaded(val status: PaymentStatus) : CheckoutEvent
    data class Released(val orderId: String) : CheckoutEvent
    data class PickupsLoaded(val pickups: List<PickupOrder>) : CheckoutEvent
    data class Collected(val orderId: String, val newSale: Boolean) : CheckoutEvent
    data class Failed(val error: PosError) : CheckoutEvent
}

sealed interface CheckoutEffect : PosSaleEffect {
    data object Init : CheckoutEffect
    /** [requestId] null: the store makes a new key for this cart. */
    data class Prepare(val cartId: String, val requestId: String?, val contacts: ReceiptContacts) : CheckoutEffect
    data object LoadProviders : CheckoutEffect
    /** Poll the order while it is open (status, provider answer, expiry). */
    data class Watch(val orderId: String) : CheckoutEffect
    data class Settle(
        val session: CheckoutSession,
        val tenders: List<TenderLine>,
        val cashGiven: Money?,
        val contacts: ReceiptContacts,
        val customerName: String?,
        val vehicleLabel: String?,
        val operatorName: String?,
    ) : CheckoutEffect
    data class StartProvider(val session: CheckoutSession, val provider: DigitalProvider, val msisdn: String?, val method: ProviderMethod?, val contacts: ReceiptContacts) : CheckoutEffect
    /** The provider settled the order: build the receipt from the reserved sale. */
    data class FinishProvider(val orderId: String, val invoiceId: String, val provider: String?, val total: Money,
                              val customerName: String?, val vehicleLabel: String?, val operatorName: String?) : CheckoutEffect
    data class Cancel(val orderId: String, val reason: String) : CheckoutEffect
    data class OnAccount(val cartId: String, val reservedOrderId: String?, val contacts: ReceiptContacts,
                         val customerName: String?, val vehicleLabel: String?, val operatorName: String?) : CheckoutEffect
    data object LoadRecovery : CheckoutEffect
    data class LoadRecoveryStatus(val orderId: String) : CheckoutEffect
    data class Release(val orderId: String) : CheckoutEffect
    data class LoadPickups(val query: String?) : CheckoutEffect
    data class Collect(val orderId: String, val newSale: Boolean) : CheckoutEffect
}

private fun fail(state: PosState, error: PosError) = Reduction(state.copy(feedback = PosFeedback.Failure(error)))

/** A reserved sale cannot change (§10.5). */
internal fun checkoutLocked(state: PosState): Reduction = fail(state, PosError.BusinessRule("cart_reserved", ""))

/** Open payment: reserve first (or reopen the existing reservation). */
internal fun openReservedPayment(state: PosState): Reduction = when {
    state.checkout != null -> Reduction(state.copy(paymentOpen = true))
    state.reserving -> Reduction(state)
    else -> Reduction(
        state.copy(paymentOpen = true, reserving = true, feedback = null),
        listOf(CheckoutEffect.Prepare(state.cart.cartId, null, ReceiptContacts(null, null))),
    )
}

/** Close the payment screen: back to the sale releases the stock; never while money is in flight. */
internal fun closeReservedPayment(state: PosState): Reduction {
    val co = state.checkout ?: return Reduction(state.copy(paymentOpen = false, reserving = false))
    val sp = state.split
    if (sp != null) {
        // Money received cannot be undone by going back: that is Cancel part payments (refunds).
        return if (sp.hasMoney || state.splitBusy || !sp.cancellable) Reduction(state)
        else Reduction(
            state.copy(splitBusy = true),
            listOf(SplitEffect.Cancel(sp.sessionId, "Operator returned to the sale", RefundFeePolicy.ManualReview, fromRecovery = false)),
        )
    }
    return when {
        co.inFlight || co.outcome == TenderOutcome.Unknown || co.busy -> Reduction(state)
        else -> Reduction(
            state.copy(checkout = co.copy(busy = true)),
            listOf(CheckoutEffect.Cancel(co.orderId, "Operator returned to the sale")),
        )
    }
}

private fun PosState.withSession(transform: CheckoutSession.() -> CheckoutSession) = copy(checkout = checkout?.transform())

private fun expire(state: PosState) = Reduction(
    state.copy(checkout = null, split = null, paymentOpen = false, reserving = false, feedback = PosFeedback.Notice(PosNotice.ReservationExpired)),
)

/** One order: its status and any part payments; the list: single and part payments to resolve. */
private fun recoveryLoads(orderId: String?): List<PosSaleEffect> =
    if (orderId != null) listOf(CheckoutEffect.LoadRecoveryStatus(orderId), SplitEffect.LoadRecoverySession(orderId))
    else listOf(CheckoutEffect.LoadRecovery, SplitEffect.LoadRecovery)

internal fun reduceCheckoutIntent(state: PosState, intent: CheckoutIntent): Reduction {
    val co = state.checkout
    return when (intent) {
        is CheckoutIntent.PayManual -> when {
            co == null || state.split != null || co.busy || co.inFlight || co.outcome == TenderOutcome.Unknown -> Reduction(state)
            !state.online -> offlineRefusal(state, "online_only")
            intent.tenders.isEmpty() || intent.tenders.any { !it.tender.manual || it.amount.minor <= 0 } ->
                fail(state, PosError.Input("tender", "amount"))
            intent.tenders.sumOf { it.amount.minor } != co.status.total.minor -> fail(state, PosError.BusinessRule("tenders_unbalanced", ""))
            else -> Reduction(
                state.withSession { copy(busy = true, message = null) }.copy(feedback = null),
                listOf(
                    CheckoutEffect.Settle(
                        session = co, tenders = intent.tenders, cashGiven = intent.cashGiven, contacts = intent.contacts,
                        customerName = state.customer?.displayName, vehicleLabel = state.vehicle?.label, operatorName = state.operator?.displayName,
                    ),
                ),
            )
        }

        is CheckoutIntent.PayProvider -> {
            val reason = state.providers?.get(intent.provider)
            when {
                co == null || state.split != null || co.busy || co.inFlight || co.outcome == TenderOutcome.Unknown -> Reduction(state)
                !state.online -> offlineRefusal(state, "online_only")
                reason != null -> fail(state, PosError.BusinessRule("provider_unavailable", reason))
                intent.provider == DigitalProvider.EcoCash && intent.msisdn.orEmpty().count { it.isDigit() } < 9 -> fail(state, PosError.Input("phone", "msisdn"))
                intent.provider == DigitalProvider.ContiPay && intent.msisdn.orEmpty().count { it.isDigit() } < 9 -> fail(state, PosError.Input("phone", "msisdn"))
                else -> Reduction(
                    state.withSession { copy(busy = true, outcome = null, message = null) }.copy(feedback = null),
                    listOf(CheckoutEffect.StartProvider(co, intent.provider, intent.msisdn?.trim(), intent.method, intent.contacts)),
                )
            }
        }

        is CheckoutIntent.PayOnAccount -> when {
            state.split != null || co?.inFlight == true || co?.busy == true || co?.outcome == TenderOutcome.Unknown -> Reduction(state)
            !state.online -> offlineRefusal(state, "online_only")
            state.customer == null -> fail(state, PosError.BusinessRule("account_customer_required", ""))
            else -> Reduction(
                state.withSession { copy(busy = true) }.copy(paying = true, feedback = null),
                listOf(
                    CheckoutEffect.OnAccount(
                        state.cart.cartId, co?.orderId, intent.contacts,
                        state.customer.displayName, state.vehicle?.label, state.operator?.displayName,
                    ),
                ),
            )
        }

        CheckoutIntent.CheckNow -> if (co == null) Reduction(state) else Reduction(state, listOf(CheckoutEffect.Watch(co.orderId)))

        CheckoutIntent.Cancel -> closeReservedPayment(state)

        is CheckoutIntent.OpenRecovery -> Reduction(
            state.copy(
                destination = PosDestination.Recovery,
                recoveryOrderId = intent.orderId,
                recoveryStatus = null,
                recoverySplit = null,
                paymentOpen = if (intent.orderId != null && intent.orderId == co?.orderId) false else state.paymentOpen,
            ),
            recoveryLoads(intent.orderId),
        )

        CheckoutIntent.RefreshRecovery -> Reduction(state, recoveryLoads(state.recoveryOrderId))

        is CheckoutIntent.Release -> if (!state.online) offlineRefusal(state, "online_only")
        else Reduction(state, listOf(CheckoutEffect.Release(intent.orderId)))

        is CheckoutIntent.LoadPickups -> Reduction(state, listOf(CheckoutEffect.LoadPickups(intent.query.trim().ifEmpty { null })))

        is CheckoutIntent.Collect -> if (!state.online) offlineRefusal(state, "online_only")
        else Reduction(state, listOf(CheckoutEffect.Collect(intent.orderId, intent.newSale)))
    }
}

internal fun reduceCheckoutEvent(state: PosState, event: CheckoutEvent): Reduction = when (event) {
    is CheckoutEvent.Enabled -> Reduction(state.copy(reserveCheckout = event.enabled))

    is CheckoutEvent.Prepared -> if (!state.reserving && state.checkout == null) {
        // The operator went back before the reservation answered: release it.
        Reduction(state, listOf(CheckoutEffect.Cancel(event.orderId, "Payment closed before the reservation answered")))
    } else {
        Reduction(
            state.copy(reserving = false, checkout = CheckoutSession(event.orderId, event.cartId, event.requestId, event.status), split = null),
            // A sale already part-paid on this order resumes where it stopped.
            listOf(CheckoutEffect.LoadProviders, CheckoutEffect.Watch(event.orderId), SplitEffect.Find(event.orderId)),
        )
    }

    is CheckoutEvent.PrepareFailed -> Reduction(state.copy(reserving = false, paymentOpen = false, feedback = PosFeedback.Failure(event.error)))

    is CheckoutEvent.ProvidersLoaded -> Reduction(state.copy(providers = event.providers))

    is CheckoutEvent.Finished -> Reduction(state.copy(checkout = null, reserving = false, receiptOrderId = event.orderId))

    is CheckoutEvent.SettleFailed -> {
        // A dropped answer keeps its key (the retry is safe); a definite refusal gets a fresh one.
        val next = state.withSession {
            copy(
                busy = false,
                paymentRequestId = if (event.network) event.paymentRequestId else null,
                outcome = TenderOutcome.Error,
                message = null,
            )
        }.copy(paying = false, feedback = PosFeedback.Failure(event.error))
        if ((event.error as? PosError.BusinessRule)?.detail?.contains("reservation expired", ignoreCase = true) == true) expire(next)
        else Reduction(next)
    }

    is CheckoutEvent.ProviderStarted -> Reduction(
        state.withSession {
            copy(busy = false, attempt = ProviderAttempt(event.provider, event.start.intentId, event.start.checkoutUrl, event.atMs), outcome = null, message = event.start.message)
        },
        listOfNotNull(state.checkout?.let { CheckoutEffect.Watch(it.orderId) }),
    )

    is CheckoutEvent.ProviderFailed -> Reduction(
        state.withSession { copy(busy = false, outcome = TenderOutcome.Error, message = null) }.copy(feedback = PosFeedback.Failure(event.error)),
    )

    is CheckoutEvent.Polled -> reducePolled(state, event)

    CheckoutEvent.Cancelled -> Reduction(state.copy(checkout = null, split = null, paymentOpen = false, reserving = false, paying = false))

    is CheckoutEvent.CancelFailed -> Reduction(state.withSession { copy(busy = false) }.copy(feedback = PosFeedback.Failure(event.error)))

    is CheckoutEvent.RecoveryLoaded -> Reduction(state.copy(recoveryItems = event.items))

    is CheckoutEvent.RecoveryStatusLoaded -> if (event.status.orderId != state.recoveryOrderId) Reduction(state)
    else Reduction(state.copy(recoveryStatus = event.status))

    is CheckoutEvent.Released -> Reduction(
        state.copy(
            checkout = state.checkout?.takeUnless { it.orderId == event.orderId },
            feedback = PosFeedback.Notice(PosNotice.ReservationReleased),
        ),
        listOf(state.recoveryOrderId?.let { CheckoutEffect.LoadRecoveryStatus(it) } ?: CheckoutEffect.LoadRecovery),
    )

    is CheckoutEvent.PickupsLoaded -> Reduction(state.copy(pickups = event.pickups))

    is CheckoutEvent.Collected -> {
        val next = state.copy(
            feedback = PosFeedback.Notice(PosNotice.Collected),
            receiptOrderId = state.receiptOrderId?.takeUnless { it == event.orderId },
            pickups = state.pickups?.filterNot { it.orderId == event.orderId },
        )
        if (event.newSale) reduceSaleIntent(next, PosSaleIntent.NewSale) else Reduction(next)
    }

    is CheckoutEvent.Failed -> Reduction(state.withSession { copy(busy = false) }.copy(paying = false, feedback = PosFeedback.Failure(event.error)))
}

/** The provider's answer, mapped to exactly one outcome (§10.7). */
private fun reducePolled(state: PosState, event: CheckoutEvent.Polled): Reduction {
    val co = state.checkout?.takeIf { it.orderId == event.status.orderId } ?: return Reduction(state)
    val st = event.status
    val updated = co.copy(status = st)
    return when {
        // A settle or receipt is already under way: just keep the latest status.
        co.busy || co.outcome == TenderOutcome.Approved -> Reduction(state.copy(checkout = updated))
        st.settled && st.salesInvoiceId != null -> Reduction(
            state.copy(checkout = updated.copy(outcome = TenderOutcome.Approved, busy = true)),
            listOf(
                CheckoutEffect.FinishProvider(
                    st.orderId, st.salesInvoiceId, st.settledProvider ?: co.attempt?.provider?.rpcValue, st.total,
                    state.customer?.displayName, state.vehicle?.label, state.operator?.displayName,
                ),
            ),
        )
        st.capturedUnfinished -> Reduction(
            state.copy(checkout = updated.copy(outcome = TenderOutcome.Unknown, message = "captured_unfinished")),
        )
        st.state == "payment_failed" && co.attempt != null -> {
            val cancelled = listOfNotNull(st.providerStatus, st.providerFailure).any { it.contains("cancel", ignoreCase = true) }
            Reduction(
                state.copy(
                    checkout = updated.copy(attempt = null, outcome = if (cancelled) TenderOutcome.Cancelled else TenderOutcome.Declined, message = st.providerFailure),
                ),
            )
        }
        st.state == "payment_expired" || st.state == "cancelled" -> expire(state)
        co.attempt == null && event.reservationExpired -> expire(state)
        co.inFlight && event.nowMs - co.attempt!!.startedAtMs > PROVIDER_WAIT_MS -> Reduction(
            state.copy(checkout = updated.copy(outcome = TenderOutcome.Unknown, message = "no_answer")),
        )
        else -> Reduction(state.copy(checkout = updated))
    }
}
