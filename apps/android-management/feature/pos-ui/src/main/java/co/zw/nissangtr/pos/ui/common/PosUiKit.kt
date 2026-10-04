package co.zw.nissangtr.pos.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosNotice
import co.zw.nissangtr.pos.ui.R
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/** Text without Material: the POS never reads MaterialTheme (Blueprint §13). */
@Composable
fun PosText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign = TextAlign.Unspecified,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align),
        overflow = TextOverflow.Ellipsis,
        maxLines = maxLines,
    )
}

@Composable
fun PosIcon(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    contentDescription: String? = null,
) {
    Image(
        imageVector = icon,
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier
            .size(size)
            .let { m -> if (contentDescription != null) m.semantics { this.contentDescription = contentDescription } else m },
    )
}

private val moneyFormat = ThreadLocal.withInitial {
    DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
}

/** Explicit currency on every amount (D-006), same rendering as the web POS. */
fun formatMoney(money: Money): String {
    val symbol = when (money.currency.code) {
        "USD" -> "US$"
        "ZIG" -> "ZiG"
        else -> money.currency.code
    }
    return "$symbol ${moneyFormat.get()!!.format(money.minor / 100.0)}"
}

fun formatQty(qty: Double): String =
    if (qty % 1.0 == 0.0) qty.toLong().toString() else qty.toString()

@Composable
fun feedbackText(feedback: PosFeedback): String = when (feedback) {
    is PosFeedback.Notice -> when (feedback.notice) {
        PosNotice.Pinned -> stringResource(R.string.pos_notice_pinned)
        PosNotice.Unpinned -> stringResource(R.string.pos_notice_unpinned)
        PosNotice.BestSellerHidden -> stringResource(R.string.pos_notice_hidden)
        PosNotice.SaleParked -> stringResource(R.string.pos_notice_parked)
        PosNotice.SaleResumed -> stringResource(R.string.pos_notice_resumed)
        PosNotice.QuoteCreated -> stringResource(R.string.pos_notice_quote_created)
        PosNotice.QuoteSent -> stringResource(R.string.pos_notice_quote_sent)
        PosNotice.QuoteConverted -> stringResource(R.string.pos_notice_quote_converted)
        PosNotice.CustomerSaved -> stringResource(R.string.pos_notice_customer_saved)
        PosNotice.VehicleSaved -> stringResource(R.string.pos_notice_vehicle_saved)
        PosNotice.EcoCashSent -> stringResource(R.string.pos_notice_ecocash)
        PosNotice.Approved -> stringResource(R.string.pos_notice_approved)
        PosNotice.Refunded -> stringResource(R.string.pos_notice_refunded)
        PosNotice.OfflineSaleQueued -> stringResource(R.string.pos_notice_offline_queued)
        PosNotice.OfflineSynced -> stringResource(R.string.pos_notice_offline_synced)
        PosNotice.TillOpened -> stringResource(R.string.pos_notice_till_opened)
        PosNotice.CashRecorded -> stringResource(R.string.pos_notice_cash_recorded)
        PosNotice.TillClosed -> stringResource(R.string.pos_notice_till_closed)
        PosNotice.TillVariancePending -> stringResource(R.string.pos_notice_till_variance)
        PosNotice.TillHandedOver -> stringResource(R.string.pos_notice_till_handed_over)
        PosNotice.PolicySaved -> stringResource(R.string.pos_notice_policy_saved)
        PosNotice.ReservationExpired -> stringResource(R.string.pos_notice_reservation_expired)
        PosNotice.ReservationReleased -> stringResource(R.string.pos_notice_reservation_released)
        PosNotice.Collected -> stringResource(R.string.pos_notice_collected)
        PosNotice.SplitCancelled -> stringResource(R.string.pos_notice_split_cancelled)
        PosNotice.SplitCancelledRefund -> stringResource(R.string.pos_notice_split_cancelled_refund)
        PosNotice.SplitRefundOwed -> stringResource(R.string.pos_notice_split_refund_owed)
        PosNotice.SplitRefundRecorded -> stringResource(R.string.pos_notice_split_refund_recorded)
        PosNotice.TerminalPaired -> stringResource(R.string.pos_notice_ct_paired)
        PosNotice.TerminalReversed -> stringResource(R.string.pos_notice_ct_reversed)
        PosNotice.TerminalFinished -> stringResource(R.string.pos_notice_ct_finished)
        PosNotice.ReturnPosted -> stringResource(R.string.pos_notice_return_posted)
        PosNotice.ReturnCashOut -> stringResource(R.string.pos_notice_return_cash_out)
        PosNotice.ReturnSwap -> stringResource(R.string.pos_notice_return_swap)
        PosNotice.ReturnWarranty -> stringResource(R.string.pos_notice_return_warranty)
        PosNotice.CoreReturned -> stringResource(R.string.pos_notice_core_returned)
        PosNotice.ClaimOpened -> stringResource(R.string.pos_notice_claim_opened)
        PosNotice.ClaimDecided -> stringResource(R.string.pos_notice_claim_decided)
        PosNotice.ClaimClosed -> stringResource(R.string.pos_notice_claim_closed)
        PosNotice.CardRefunded -> stringResource(R.string.pos_notice_card_refunded)
        PosNotice.HeldHere -> stringResource(R.string.pos_notice_held_here)
        PosNotice.HeldElsewhere -> stringResource(R.string.pos_notice_held_elsewhere)
        PosNotice.TransferRequested -> stringResource(R.string.pos_notice_transfer_requested)
        PosNotice.Backordered -> stringResource(R.string.pos_notice_backordered)
        PosNotice.TransferSent -> stringResource(R.string.pos_notice_transfer_sent)
        PosNotice.FulfillmentReady -> stringResource(R.string.pos_notice_fulfillment_ready)
        PosNotice.TransferReceived -> stringResource(R.string.pos_notice_transfer_received)
        PosNotice.HandedOver -> stringResource(R.string.pos_notice_handed_over)
        PosNotice.FulfillmentReleased -> stringResource(R.string.pos_notice_fulfillment_released)
    }
    is PosFeedback.Failure -> errorText(feedback.error)
}

/** ARCH-10: every PosError has an operator-facing resource; engineering text never reaches the screen. */
@Composable
fun errorText(error: PosError): String = when (error) {
    is PosError.Transient -> stringResource(R.string.pos_error_transient)
    is PosError.Input -> stringResource(R.string.pos_error_input, error.field)
    is PosError.BusinessRule -> when (error.rule) {
        "part_not_sellable" -> stringResource(R.string.pos_error_not_sellable)
        "tenders_unbalanced" -> stringResource(R.string.pos_error_unbalanced)
        "cart_not_empty" -> stringResource(R.string.pos_error_cart_not_empty)
        "offline_stock" -> stringResource(R.string.pos_error_offline_stock, error.detail)
        "catalog_publishing" -> stringResource(R.string.pos_catalog_publishing)
        "catalog_not_connected" -> stringResource(R.string.pos_catalog_not_connected)
        "catalog_unavailable" -> stringResource(R.string.pos_catalog_unavailable)
        "out_of_stock" -> stringResource(R.string.pos_error_out_of_stock, error.detail)
        "insufficient_stock" -> stringResource(R.string.pos_error_insufficient_stock)
        "till_required" -> stringResource(R.string.pos_error_till_required)
        "cart_reserved" -> stringResource(R.string.pos_co_locked)
        "account_customer_required" -> stringResource(R.string.pos_co_account_needs_customer)
        "provider_unavailable" -> error.detail.ifBlank { stringResource(R.string.pos_error_rule) }
        "reserve_unavailable" -> stringResource(R.string.pos_co_reserve_unavailable)
        "split_unavailable" -> stringResource(R.string.pos_co_reserve_unavailable)
        "split_nothing_received" -> stringResource(R.string.pos_split_nothing_received)
        "terminal_not_set" -> stringResource(R.string.pos_ct_not_set)
        "terminal_app_missing" -> stringResource(R.string.pos_ct_app_missing)
        "terminal_not_paired" -> stringResource(R.string.pos_ct_not_paired)
        "terminal_loading", "terminal_unavailable" -> stringResource(R.string.pos_ct_none_set_up)
        "terminal_unposted" -> stringResource(R.string.pos_ct_unposted_detail, error.detail)
        "return_named_customer" -> stringResource(R.string.pos_rt_err_named_customer)
        "return_till_closed" -> stringResource(R.string.pos_rt_err_till_closed)
        "return_nothing_paid" -> stringResource(R.string.pos_rt_err_nothing_paid)
        "return_one_part" -> stringResource(R.string.pos_rt_err_one_part)
        "return_partly_returned" -> stringResource(R.string.pos_rt_err_partly_returned)
        "fulfillment_no_sale" -> stringResource(R.string.pos_ff_err_no_sale)
        "fulfillment_not_in_sale" -> stringResource(R.string.pos_ff_err_not_in_sale)
        "fulfillment_branch" -> stringResource(R.string.pos_ff_err_branch)
        "card_refund_declined", "card_refund_cancelled", "card_refund_failed" ->
            stringResource(R.string.pos_rt_err_card_refund, error.detail.ifBlank { error.rule.removePrefix("card_refund_") })
        else -> if (error.detail.isNotBlank()) stringResource(R.string.pos_error_rule_detail, error.detail) else stringResource(R.string.pos_error_rule)
    }
    is PosError.PaymentUnknown -> stringResource(R.string.pos_error_payment_unknown)
    is PosError.HardwareUnavailable -> if (error.device == "camera") stringResource(R.string.pos_error_camera) else stringResource(R.string.pos_error_hardware, error.device)
    is PosError.OfflineRestricted -> stringResource(
        when {
            "server_cart" in error.blocked -> R.string.pos_error_offline_server_cart
            "walk_in_only" in error.blocked -> R.string.pos_error_offline_walk_in
            "non_cash" in error.blocked -> R.string.pos_error_offline_cash
            "manager_approval" in error.blocked -> R.string.pos_error_offline_approval
            "no_outbox" in error.blocked -> R.string.pos_error_offline_no_outbox
            else -> R.string.pos_error_offline
        },
    )
}
