package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import co.zw.nissangtr.pos.design.icons.ScanBarcode
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.icons.ShieldCheck
import co.zw.nissangtr.pos.design.icons.Smartphone
import co.zw.nissangtr.pos.design.icons.Trash2
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.returnsFlow
import co.zw.nissangtr.pos.domain.model.GarageVehicle
import co.zw.nissangtr.pos.domain.model.ManagerCredentials
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.Receipt
import co.zw.nissangtr.pos.domain.model.thermalLines
import co.zw.nissangtr.pos.domain.model.receiptRows
import co.zw.nissangtr.pos.domain.model.THERMAL_COLUMNS
import co.zw.nissangtr.pos.domain.model.ReceiptRow
import co.zw.nissangtr.pos.domain.model.ReceiptLabels
import androidx.compose.ui.platform.LocalContext
import android.content.res.Resources
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.ReceiptPaper
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.model.counter
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosField
import co.zw.nissangtr.pos.ui.common.PosIcon
import co.zw.nissangtr.pos.ui.common.PosModal
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.PosRowEnd
import co.zw.nissangtr.pos.ui.common.PosSegmented
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.feedbackText
import co.zw.nissangtr.pos.ui.common.formatMoney
import co.zw.nissangtr.pos.ui.common.formatQty
import co.zw.nissangtr.pos.ui.home.SoftButton

private class TenderDraft(tender: Tender, amount: String) {
    var tender by mutableStateOf(tender)
    var amount by mutableStateOf(amount)
}

/** Parses an operator-typed amount into minor units of [currency]; null when it is not a number. */
internal fun parseMoney(text: String, currency: co.zw.nissangtr.pos.domain.model.CurrencyCode): Money? =
    text.trim().replace(",", "").toBigDecimalOrNull()?.let { Money(it.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong(), currency) }

private fun Money.plain(): String = java.math.BigDecimal.valueOf(minor, 2).toPlainString()

@Composable
private fun tenderLabel(t: Tender): String = stringResource(
    when (t) {
        Tender.Cash -> R.string.pos_tender_cash
        Tender.Bank -> R.string.pos_tender_bank
        Tender.EcoCash -> R.string.pos_tender_ecocash
        Tender.StoreCredit -> R.string.pos_tender_store_credit
        Tender.Paynow -> R.string.pos_tender_paynow
        Tender.ContiPay -> R.string.pos_tender_contipay
        Tender.OnAccount -> R.string.pos_tender_account
    },
)

/**
 * Payment (split tender, cash change, EcoCash push, receipt contacts) and, once posted, the
 * receipt with print. Tenders must equal the server total exactly; change is shown, never posted.
 */
@Composable
fun PaymentDialog(state: PosState, dispatch: (PosIntent) -> Unit, onPrint: (List<ReceiptLine>, ReceiptPaper) -> Unit) {
    val receipt = state.receipt
    if (receipt != null) {
        val orderId = state.receiptOrderId
        ReceiptDialog(
            receipt,
            onPrint = onPrint,
            onNewSale = { dispatch(PosSaleIntent.NewSale) },
            // Reserved sales record the hand-over: now (and start the next sale) or when the customer returns.
            onHandedOver = orderId?.let { id -> { dispatch(co.zw.nissangtr.pos.domain.state.CheckoutIntent.Collect(id, newSale = true)) } },
        )
        return
    }
    if (state.reserveCheckout && (state.checkout != null || state.reserving)) {
        ReservedPaymentDialog(state, dispatch)
        return
    }
    val total = state.cart.total
    val currency = total.currency
    val tenders = remember { mutableStateListOf(TenderDraft(Tender.Cash, total.plain())) }
    var cashGiven by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf(state.customer?.email.orEmpty()) }
    var whatsapp by rememberSaveable { mutableStateOf(state.customer?.whatsappE164.orEmpty()) }
    var ecoPhone by rememberSaveable { mutableStateOf(state.customer?.whatsappE164.orEmpty()) }

    val parsed = tenders.map { parseMoney(it.amount, currency) }
    val paid = parsed.sumOf { it?.minor ?: 0 }
    val remaining = total.minor - paid
    val balanced = remaining == 0L && parsed.none { it == null || it.minor <= 0 }
    val cashTotal = tenders.zip(parsed).filter { it.first.tender == Tender.Cash }.sumOf { it.second?.minor ?: 0 }
    val given = parseMoney(cashGiven, currency)
    val change = given?.let { (it.minor - cashTotal).takeIf { c -> c >= 0 } }
    val palette = PosTheme.palette
    val type = PosTheme.type

    PosModal(
        title = stringResource(R.string.pos_payment_title),
        onDismiss = { dispatch(PosSaleIntent.ClosePayment) },
        dismissible = !state.paying,
        width = 620.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().clip(PosTheme.shape.sm).background(palette.canvas).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosText(stringResource(R.string.pos_amount_due), type.bodyPrimary, palette.textSecondary, modifier = Modifier.weight(1f))
            PosText(formatMoney(total), type.numericTotal, palette.textPrimary)
        }
        if (!state.online) {
            Spacer(Modifier.height(8.dp))
            PosText(stringResource(R.string.pos_error_offline_cash), type.labelMeta.copy(fontWeight = FontWeight.SemiBold), palette.offline)
        }
        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 520.dp
            Column {
                tenders.forEachIndexed { i, t ->
                    val chooser: @Composable () -> Unit = {
                        PosSegmented(
                            options = if (state.online) Tender.entries.filter { it.counter } else listOf(Tender.Cash),
                            selected = t.tender,
                            label = { tenderLabel(it) },
                            onSelect = { t.tender = it },
                        )
                    }
                    val amountAndRemove: @Composable (Modifier) -> Unit = { m ->
                        Row(m, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PosField(
                                label = stringResource(R.string.pos_tender_amount),
                                value = t.amount,
                                onChange = { t.amount = it },
                                keyboard = KeyboardType.Decimal,
                                modifier = Modifier.weight(1f),
                            )
                            if (tenders.size > 1) {
                                Box(
                                    Modifier.size(48.dp).clip(PosTheme.shape.sm).clickable(role = Role.Button) { tenders.removeAt(i) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    PosIcon(PosIcons.Trash2, tint = palette.textSecondary, size = 18.dp, contentDescription = stringResource(R.string.pos_remove_tender))
                                }
                            }
                        }
                    }
                    if (narrow) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            chooser()
                            amountAndRemove(Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            chooser()
                            amountAndRemove(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            SoftButton(
                label = stringResource(R.string.pos_split_payment),
                icon = PosIcons.Plus,
                enabled = remaining > 0,
                onClick = { tenders.add(TenderDraft(Tender.Cash, Money(remaining.coerceAtLeast(0), currency).plain())) },
                modifier = Modifier.widthIn(max = 220.dp),
            )
            Spacer(Modifier.width(12.dp))
            PosText(
                when {
                    balanced -> stringResource(R.string.pos_balanced)
                    remaining > 0 -> stringResource(R.string.pos_remaining, formatMoney(Money(remaining, currency)))
                    else -> stringResource(R.string.pos_over_tendered, formatMoney(Money(-remaining, currency)))
                },
                type.labelAction,
                if (balanced) palette.success else palette.error,
            )
        }
        if (cashTotal > 0) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                PosField(stringResource(R.string.pos_cash_given), cashGiven, { cashGiven = it }, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                Column(Modifier.weight(1f)) {
                    PosText(stringResource(R.string.pos_change_due), type.labelMeta, palette.textMuted)
                    PosText(change?.let { formatMoney(Money(it, currency)) } ?: "—", type.numericTotal, palette.textPrimary)
                }
            }
        }
        if (tenders.any { it.tender == Tender.EcoCash }) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                PosField(stringResource(R.string.pos_ecocash_phone), ecoPhone, { ecoPhone = it }, placeholder = "+26377…", keyboard = KeyboardType.Phone, modifier = Modifier.weight(1f))
                val ecoAmount = tenders.zip(parsed).filter { it.first.tender == Tender.EcoCash }.sumOf { it.second?.minor ?: 0 }
                SoftButton(
                    label = stringResource(R.string.pos_ecocash_send),
                    icon = PosIcons.Smartphone,
                    enabled = ecoAmount > 0 && ecoPhone.isNotBlank(),
                    onClick = { dispatch(PosSaleIntent.RequestEcoCash(ecoPhone, Money(ecoAmount, currency))) },
                    modifier = Modifier.weight(1f),
                )
            }
            state.ecoCashReference?.let {
                PosText(stringResource(R.string.pos_ecocash_sent, it), type.bodySecondary, palette.success, modifier = Modifier.padding(top = 6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosField(stringResource(R.string.pos_receipt_email), email, { email = it }, keyboard = KeyboardType.Email, modifier = Modifier.weight(1f))
            PosField(stringResource(R.string.pos_receipt_whatsapp), whatsapp, { whatsapp = it }, placeholder = "+26377…", keyboard = KeyboardType.Phone, modifier = Modifier.weight(1f))
        }
        state.feedback?.let {
            PosText(feedbackText(it), type.bodyPrimary, palette.error, modifier = Modifier.padding(top = 10.dp))
        }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_back_to_sale), null, enabled = !state.paying, onClick = { dispatch(PosSaleIntent.ClosePayment) }, modifier = Modifier.widthIn(max = 180.dp))
            PosPrimaryButton(
                label = if (state.paying) stringResource(R.string.pos_completing) else stringResource(R.string.pos_complete_sale, formatMoney(total)),
                enabled = balanced && !state.paying,
                onClick = {
                    dispatch(
                        PosSaleIntent.Checkout(
                            tenders = tenders.zip(parsed).map { (t, m) -> TenderLine(t.tender, m!!) },
                            cashGiven = given,
                            contacts = ReceiptContacts(email.ifBlank { null }, whatsapp.ifBlank { null }),
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun ReceiptDialog(
    receipt: Receipt,
    onPrint: (List<ReceiptLine>, ReceiptPaper) -> Unit,
    onNewSale: () -> Unit,
    onHandedOver: (() -> Unit)? = null,
) {
    var paper by rememberSaveable { mutableStateOf(ReceiptPaper.Thermal80) }
    val lines = receiptLines(receipt)
    PosModal(
        title = stringResource(R.string.pos_sale_complete, receipt.documentNumber ?: ""),
        onDismiss = onNewSale,
        width = 720.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PosSegmented(
                options = ReceiptPaper.entries,
                selected = paper,
                label = { stringResource(if (it == ReceiptPaper.Thermal80) R.string.pos_paper_80 else R.string.pos_paper_a4) },
                onSelect = { paper = it },
            )
            Spacer(Modifier.weight(1f))
            SoftButton(stringResource(R.string.pos_print_receipt), PosIcons.Printer, enabled = true, onClick = { onPrint(lines, paper) }, modifier = Modifier.widthIn(max = 200.dp))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ReceiptPreview(receipt, paper)
        }
        PosRowEnd {
            if (onHandedOver != null) {
                SoftButton(stringResource(R.string.pos_co_collect_later), null, enabled = true, onClick = onNewSale, modifier = Modifier.widthIn(max = 240.dp))
                PosPrimaryButton(stringResource(R.string.pos_co_handed_over), enabled = true, onClick = onHandedOver)
            } else {
                PosPrimaryButton(stringResource(R.string.pos_new_sale), enabled = true, onClick = onNewSale)
            }
        }
    }
}

/** What prints, previewed: the same lines the ESC/POS and A4 renderers receive (§6.6). */
@Composable
fun ReceiptPreview(receipt: Receipt, paper: ReceiptPaper) {
    val palette = PosTheme.palette
    val mono = PosTheme.type.monoReference.copy(fontSize = 11.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
    Column(
        Modifier
            // 42 monospace columns at 11 sp plus padding; A4's 80 columns scroll sideways on narrow screens.
            .widthIn(max = if (paper == ReceiptPaper.Thermal80) 360.dp else 640.dp)
            .horizontalScroll(rememberScrollState())
            .fillMaxWidth()
            .border(1.dp, palette.borderSubtle, PosTheme.shape.sm)
            .background(palette.surfacePrimary)
            .padding(18.dp),
    ) {
        // Exactly the printed text: 42 columns on 80 mm paper, 80 on A4.
        val width = if (paper == ReceiptPaper.Thermal80) THERMAL_COLUMNS else A4_COLUMNS
        receiptLines(receipt).forEach { row ->
            thermalLines(row.left, row.right, width).forEach { text ->
                PosText(text, mono.copy(fontWeight = if (row.strong) FontWeight.Bold else FontWeight.Normal), palette.textPrimary, maxLines = 1)
            }
        }
    }
}

/** One printed row: label left, amount right (shared receipt format v1, `ReceiptRow`). */
typealias ReceiptLine = ReceiptRow

/** Printed labels from string resources (§10.11); `{0}` marks the value, as in the shared spec. */
fun receiptLabels(res: Resources): ReceiptLabels = ReceiptLabels(
    brand = res.getString(R.string.pos_receipt_brand),
    strap = res.getString(R.string.pos_receipt_strap),
    invoice = res.getString(R.string.pos_receipt_invoice, "{0}"),
    servedBy = res.getString(R.string.pos_receipt_served_by, "{0}"),
    customer = res.getString(R.string.pos_receipt_customer, "{0}"),
    vehicle = res.getString(R.string.pos_receipt_vehicle, "{0}"),
    subtotal = res.getString(R.string.pos_subtotal),
    discount = res.getString(R.string.pos_discount),
    total = res.getString(R.string.pos_receipt_total),
    cashGiven = res.getString(R.string.pos_cash_given),
    change = res.getString(R.string.pos_change_due),
    offline = res.getString(R.string.pos_receipt_offline),
    thanks = res.getString(R.string.pos_receipt_thanks),
    tenders = mapOf(
        Tender.Cash to res.getString(R.string.pos_tender_cash),
        Tender.Bank to res.getString(R.string.pos_tender_bank),
        Tender.EcoCash to res.getString(R.string.pos_tender_ecocash),
        Tender.StoreCredit to res.getString(R.string.pos_tender_store_credit),
        Tender.Paynow to res.getString(R.string.pos_tender_paynow),
        Tender.ContiPay to res.getString(R.string.pos_tender_contipay),
        Tender.OnAccount to res.getString(R.string.pos_tender_account),
    ),
)

/** Receipt rows in the shared format, identical for preview, ESC/POS and A4 (and the web POS). */
@Composable
fun receiptLines(r: Receipt): List<ReceiptLine> {
    val res = LocalContext.current.resources
    return remember(r, res) { receiptRows(r, receiptLabels(res)) }
}

private val ApprovalRequest.titleRes: Int
    get() = when (this) {
        is ApprovalRequest.Discount -> R.string.pos_approve_discount
        is ApprovalRequest.PriceOverride -> R.string.pos_approve_override
        ApprovalRequest.VoidSale -> R.string.pos_approve_void
        is ApprovalRequest.Refund -> R.string.pos_approve_refund
        is ApprovalRequest.CashOut -> R.string.pos_approve_cash_out
        is ApprovalRequest.TillVariance -> R.string.pos_approve_till_variance
        is ApprovalRequest.Handover -> R.string.pos_approve_handover
        is ApprovalRequest.RepairPaidOrder -> R.string.pos_approve_repair
        is ApprovalRequest.SplitRefund -> when (step) {
            is co.zw.nissangtr.pos.domain.model.SplitRefundStep.Approve -> R.string.pos_approve_split_refund
            is co.zw.nissangtr.pos.domain.model.SplitRefundStep.Complete -> R.string.pos_approve_split_refund_paid
            is co.zw.nissangtr.pos.domain.model.SplitRefundStep.Fail -> R.string.pos_approve_split_refund_failed
        }
        is ApprovalRequest.ReturnPost -> R.string.pos_approve_return
        is ApprovalRequest.CoreReturn -> R.string.pos_approve_core
        is ApprovalRequest.WarrantyDecide -> R.string.pos_approve_warranty
        is ApprovalRequest.CardRefund -> R.string.pos_approve_card_refund
        is ApprovalRequest.CardRefundFinish -> R.string.pos_approve_card_refund_finish
    }

/**
 * Governed action (§10.10). Sale actions choose a configured reason; the approval policy decides
 * whether a manager signs in for this one action (owner decision D4: the cashier stays signed in).
 * Drawer actions chose their reason before and always need a manager.
 */
@Composable
fun ApprovalDialog(state: PosState, dispatch: (PosIntent) -> Unit) {
    val request = state.approval ?: return
    var id by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var reasonCode by rememberSaveable(request) { mutableStateOf<String?>(null) }
    val palette = PosTheme.palette
    val governed = request !is ApprovalRequest.TillAction && !request.returnsFlow
    val reasons = state.approvalReasons
    val reason = reasons?.firstOrNull { it.code == reasonCode }
    val needsManager = state.approvalNeedsManager
    var usePassword by rememberSaveable(request) { mutableStateOf(false) }
    val loading = governed && reasons == null
    // Reason (and its note) come first; the badge or password then approves exactly that.
    val reasonReady = !loading && (!governed || reasons.isNullOrEmpty() || reason != null) && (reason?.requiresNotes != true || notes.isNotBlank())
    val ready = !state.approving && reasonReady && (!needsManager || (usePassword && id.isNotBlank() && password.isNotEmpty()))
    // Automatic badge read: once the reason is set, the front camera opens for the manager's badge
    // (once per reason; a reason that needs a note waits for the Scan button after the note).
    var autoScannedFor by rememberSaveable(request) { mutableStateOf<String?>(null) }
    val scanKey = reason?.code ?: "drawer"
    LaunchedEffect(request, scanKey, needsManager, usePassword, reasonReady) {
        if (needsManager && !usePassword && reasonReady && reason?.requiresNotes != true && autoScannedFor != scanKey &&
            !state.badgeScanning && !state.approving
        ) {
            autoScannedFor = scanKey
            dispatch(co.zw.nissangtr.pos.domain.state.GovernanceIntent.ScanBadge(reason, notes.ifBlank { null }))
        }
    }
    PosModal(stringResource(request.titleRes), onDismiss = { dispatch(PosSaleIntent.CancelApproval) }, dismissible = !state.approving) {
        PosText(
            when (request) {
                is ApprovalRequest.Discount -> stringResource(R.string.pos_approve_discount_detail, formatQty(request.percent))
                is ApprovalRequest.PriceOverride -> stringResource(R.string.pos_approve_override_detail, request.unitPrice.toString())
                ApprovalRequest.VoidSale -> stringResource(R.string.pos_approve_void_detail)
                is ApprovalRequest.Refund -> stringResource(R.string.pos_approve_refund_detail, request.invoice.documentNumber ?: "", formatMoney(request.invoice.total))
                is ApprovalRequest.CashOut -> stringResource(R.string.pos_approve_cash_out_detail, formatMoney(request.amount), request.reason.label)
                is ApprovalRequest.TillVariance -> stringResource(
                    R.string.pos_approve_till_variance_detail,
                    if (request.variance.minor < 0) stringResource(R.string.pos_till_short, formatMoney(request.variance.copy(minor = -request.variance.minor)))
                    else stringResource(R.string.pos_till_over, formatMoney(request.variance)),
                    request.reason.label,
                )
                is ApprovalRequest.Handover -> stringResource(R.string.pos_approve_handover_detail, request.to.fullName)
                is ApprovalRequest.RepairPaidOrder -> stringResource(R.string.pos_approve_repair_detail, request.orderId.take(8))
                is ApprovalRequest.SplitRefund -> stringResource(R.string.pos_approve_split_refund_detail, formatMoney(request.amount))
                is ApprovalRequest.ReturnPost -> stringResource(
                    R.string.pos_approve_return_detail,
                    request.documentNumber.orEmpty(),
                    formatMoney(request.amount),
                    stringResource(returnResolutionLabel(request.resolution)),
                )
                is ApprovalRequest.CoreReturn -> stringResource(R.string.pos_approve_core_detail, request.documentNumber.orEmpty(), formatMoney(request.amount))
                is ApprovalRequest.WarrantyDecide -> stringResource(
                    R.string.pos_approve_warranty_detail,
                    request.claim.documentNumber.orEmpty(),
                    request.claim.oemPartNumber.orEmpty(),
                    when (val d = request.decision) {
                        is co.zw.nissangtr.pos.domain.model.WarrantyDecision.Reject -> stringResource(R.string.pos_wc_rejected_because, d.reason)
                        is co.zw.nissangtr.pos.domain.model.WarrantyDecision.Replace -> stringResource(R.string.pos_wc_replace)
                        is co.zw.nissangtr.pos.domain.model.WarrantyDecision.Credit -> stringResource(R.string.pos_wc_credit)
                        co.zw.nissangtr.pos.domain.model.WarrantyDecision.TakeBack -> stringResource(R.string.pos_wc_takeback)
                    },
                )
                is ApprovalRequest.CardRefund -> stringResource(R.string.pos_approve_card_refund_detail, formatMoney(request.amount), request.documentNumber.orEmpty())
                is ApprovalRequest.CardRefundFinish -> stringResource(R.string.pos_approve_card_refund_finish_detail, formatMoney(request.amount))
            },
            PosTheme.type.bodyPrimary,
            palette.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        if (governed) {
            PosText(stringResource(R.string.pos_till_reason), PosTheme.type.labelMeta.copy(fontWeight = FontWeight.SemiBold), palette.textMuted)
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                when {
                    reasons == null -> PosText(stringResource(R.string.pos_loading), PosTheme.type.bodySecondary, palette.textMuted)
                    reasons.isEmpty() -> PosText(stringResource(R.string.pos_till_no_reasons), PosTheme.type.bodySecondary, palette.textMuted)
                    else -> reasons.forEach { r ->
                        ListRow(
                            title = r.label,
                            subtitle = if (r.requiresNotes) stringResource(R.string.pos_till_reason_needs_notes) else null,
                            selected = r.code == reasonCode,
                            onClick = { reasonCode = r.code },
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (!loading) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosIcon(PosIcons.ShieldCheck, tint = palette.textMuted, size = 16.dp)
                PosText(
                    stringResource(
                        when {
                            needsManager && !usePassword -> R.string.pos_badge_hint
                            needsManager -> R.string.pos_manager_hint
                            state.selfApprover -> R.string.pos_self_approver
                            else -> R.string.pos_within_limit
                        },
                    ),
                    PosTheme.type.bodySecondary,
                    palette.textMuted,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
        PosField(
            stringResource(if (reason?.requiresNotes == true) R.string.pos_till_notes_required else R.string.pos_notes_optional),
            notes,
            { notes = it },
        )
        Spacer(Modifier.height(10.dp))
        if (needsManager && usePassword) {
            PosField(stringResource(R.string.pos_manager_id), id, { id = it })
            Spacer(Modifier.height(10.dp))
            PosField(stringResource(R.string.pos_manager_password), password, { password = it }, keyboard = KeyboardType.Password, password = true)
            Spacer(Modifier.height(6.dp))
            SoftButton(stringResource(R.string.pos_badge_use_scan), PosIcons.ScanBarcode, enabled = !state.approving, onClick = { usePassword = false })
        } else if (needsManager) {
            PosPrimaryButton(
                stringResource(if (state.badgeScanning || state.approving) R.string.pos_badge_scanning else R.string.pos_badge_scan),
                enabled = reasonReady && !state.badgeScanning && !state.approving,
                icon = PosIcons.ScanBarcode,
                onClick = { dispatch(co.zw.nissangtr.pos.domain.state.GovernanceIntent.ScanBadge(reason, notes.ifBlank { null })) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            SoftButton(stringResource(R.string.pos_badge_or_password), null, enabled = !state.badgeScanning && !state.approving, onClick = { usePassword = true })
        }
        state.feedback?.let { PosText(feedbackText(it), PosTheme.type.bodyPrimary, palette.error, modifier = Modifier.padding(top = 10.dp)) }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, enabled = !state.approving, onClick = { dispatch(PosSaleIntent.CancelApproval) }, modifier = Modifier.widthIn(max = 140.dp))
            if (!needsManager || usePassword) PosPrimaryButton(
                stringResource(
                    when {
                        state.approving -> R.string.pos_approving
                        needsManager -> R.string.pos_approve
                        else -> R.string.pos_confirm
                    },
                ),
                enabled = ready,
                onClick = {
                    dispatch(
                        PosSaleIntent.SubmitApproval(
                            credentials = if (needsManager && usePassword) ManagerCredentials(id, password, null) else null,
                            reason = reason,
                            notes = notes.ifBlank { null },
                        ),
                    )
                },
            )
        }
    }
}

/** Several vehicles in the customer's garage: ask which one this sale is for. */
@Composable
fun GarageChooserDialog(state: PosState, dispatch: (PosIntent) -> Unit) {
    if (!state.garagePrompt) return
    val palette = PosTheme.palette
    PosModal(stringResource(R.string.pos_which_vehicle), onDismiss = { dispatch(PosSaleIntent.ChooseGarageVehicle(null)) }) {
        state.garage.filter { it.selection() != null }.forEach { v: GarageVehicle ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(PosTheme.shape.sm)
                    .background(palette.canvas)
                    .clickable(role = Role.Button) { dispatch(PosSaleIntent.ChooseGarageVehicle(v)) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PosIcon(PosIcons.Car, tint = palette.textSecondary, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                PosText(v.label, PosTheme.type.labelAction, palette.textPrimary, modifier = Modifier.weight(1f))
                if (v.isPrimary) PosText(stringResource(R.string.pos_primary), PosTheme.type.labelMeta, palette.success)
            }
        }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_no_vehicle), null, enabled = true, onClick = { dispatch(PosSaleIntent.ChooseGarageVehicle(null)) }, modifier = Modifier.widthIn(max = 220.dp))
        }
    }
}

/** Characters per line on the A4 document printer. */
const val A4_COLUMNS = 80
