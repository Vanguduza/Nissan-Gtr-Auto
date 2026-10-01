package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.background
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
import co.zw.nissangtr.pos.domain.model.GarageVehicle
import co.zw.nissangtr.pos.domain.model.ManagerCredentials
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.Receipt
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.ReceiptPaper
import co.zw.nissangtr.pos.domain.model.Tender
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
        ReceiptDialog(receipt, onPrint = onPrint, onNewSale = { dispatch(PosSaleIntent.NewSale) })
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
                            options = if (state.online) Tender.entries else listOf(Tender.Cash),
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
private fun ReceiptDialog(receipt: Receipt, onPrint: (List<ReceiptLine>, ReceiptPaper) -> Unit, onNewSale: () -> Unit) {
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
            PosPrimaryButton(stringResource(R.string.pos_new_sale), enabled = true, onClick = onNewSale)
        }
    }
}

/** What prints, previewed: the same lines the ESC/POS and A4 renderers receive (§6.6). */
@Composable
fun ReceiptPreview(receipt: Receipt, paper: ReceiptPaper) {
    val palette = PosTheme.palette
    val mono = PosTheme.type.monoReference.copy(fontSize = 12.sp, lineHeight = 17.sp, fontFamily = FontFamily.Monospace)
    Column(
        Modifier
            .widthIn(max = if (paper == ReceiptPaper.Thermal80) 320.dp else 620.dp)
            .fillMaxWidth()
            .border(1.dp, palette.borderSubtle, PosTheme.shape.sm)
            .background(palette.surfacePrimary)
            .padding(18.dp),
    ) {
        receiptLines(receipt).forEach { (left, right, strong) ->
            Row(Modifier.fillMaxWidth()) {
                PosText(left, mono.copy(fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal), palette.textPrimary, modifier = Modifier.weight(1f))
                if (right.isNotEmpty()) PosText(right, mono.copy(fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal), palette.textPrimary, align = TextAlign.End)
            }
        }
    }
}

/** One printed row: label left, amount right. Shared by the preview and the printer hand-off. */
data class ReceiptLine(val left: String, val right: String = "", val strong: Boolean = false)

/** Receipt content from string resources (§10.11), identical for preview, ESC/POS and A4. */
@Composable
fun receiptLines(r: Receipt): List<ReceiptLine> {
    val subtotal = stringResource(R.string.pos_subtotal)
    val discount = stringResource(R.string.pos_discount)
    val total = stringResource(R.string.pos_receipt_total)
    val cashGiven = stringResource(R.string.pos_cash_given)
    val change = stringResource(R.string.pos_change_due)
    val brand = stringResource(R.string.pos_receipt_brand)
    val strap = stringResource(R.string.pos_receipt_strap)
    val invoice = stringResource(R.string.pos_receipt_invoice, r.documentNumber ?: r.invoiceId.take(8))
    val servedBy = r.operatorName?.let { stringResource(R.string.pos_receipt_served_by, it) }
    val customer = r.customerName?.let { stringResource(R.string.pos_receipt_customer, it) }
    val vehicle = r.vehicleLabel?.let { stringResource(R.string.pos_receipt_vehicle, it) }
    val thanks = stringResource(R.string.pos_receipt_thanks)
    val offline = stringResource(R.string.pos_receipt_offline)
    val tenderNames = Tender.entries.associateWith { tenderLabel(it) }
    return buildList {
        add(ReceiptLine(brand, strong = true))
        add(ReceiptLine(strap))
        add(ReceiptLine(""))
        add(ReceiptLine(invoice, strong = true))
        add(ReceiptLine(r.issuedAtIso.replace('T', ' ').take(16)))
        servedBy?.let { add(ReceiptLine(it)) }
        customer?.let { add(ReceiptLine(it)) }
        vehicle?.let { add(ReceiptLine(it)) }
        add(ReceiptLine(""))
        r.lines.forEach { l ->
            add(ReceiptLine(l.name))
            add(ReceiptLine("  ${l.oemPartNumber}  ${formatQty(l.qty)} × ${formatMoney(l.unitPrice)}", formatMoney(l.lineTotal)))
        }
        add(ReceiptLine(""))
        add(ReceiptLine(subtotal, formatMoney(r.subtotal)))
        add(ReceiptLine(discount, formatMoney(r.discount)))
        add(ReceiptLine(total, formatMoney(r.total), strong = true))
        r.tenders.forEach { add(ReceiptLine(tenderNames.getValue(it.tender), formatMoney(it.amount))) }
        r.cashGiven?.let { add(ReceiptLine(cashGiven, formatMoney(it))) }
        r.change?.let { add(ReceiptLine(change, formatMoney(it), strong = true)) }
        add(ReceiptLine(""))
        if (r.offline) add(ReceiptLine(offline, strong = true))
        add(ReceiptLine(thanks))
    }
}

private val ApprovalRequest.titleRes: Int
    get() = when (this) {
        is ApprovalRequest.Discount -> R.string.pos_approve_discount
        is ApprovalRequest.PriceOverride -> R.string.pos_approve_override
        ApprovalRequest.VoidSale -> R.string.pos_approve_void
        is ApprovalRequest.Refund -> R.string.pos_approve_refund
    }

/** Manager signs in for this one action only (owner decision D4); the cashier stays signed in. */
@Composable
fun ApprovalDialog(state: PosState, dispatch: (PosIntent) -> Unit) {
    val request = state.approval ?: return
    var id by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val palette = PosTheme.palette
    PosModal(stringResource(request.titleRes), onDismiss = { dispatch(PosSaleIntent.CancelApproval) }, dismissible = !state.approving) {
        PosText(
            when (request) {
                is ApprovalRequest.Discount -> stringResource(R.string.pos_approve_discount_detail, formatQty(request.percent))
                is ApprovalRequest.PriceOverride -> stringResource(R.string.pos_approve_override_detail, request.unitPrice.toString())
                ApprovalRequest.VoidSale -> stringResource(R.string.pos_approve_void_detail)
                is ApprovalRequest.Refund -> stringResource(R.string.pos_approve_refund_detail, request.invoice.documentNumber ?: "", formatMoney(request.invoice.total))
            },
            PosTheme.type.bodyPrimary,
            palette.textSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosIcon(PosIcons.ShieldCheck, tint = palette.textMuted, size = 16.dp)
            PosText(stringResource(R.string.pos_manager_hint), PosTheme.type.bodySecondary, palette.textMuted)
        }
        Spacer(Modifier.height(12.dp))
        PosField(stringResource(R.string.pos_manager_id), id, { id = it })
        Spacer(Modifier.height(10.dp))
        PosField(stringResource(R.string.pos_manager_password), password, { password = it }, keyboard = KeyboardType.Password, password = true)
        Spacer(Modifier.height(10.dp))
        PosField(stringResource(R.string.pos_notes_optional), notes, { notes = it })
        state.feedback?.let { PosText(feedbackText(it), PosTheme.type.bodyPrimary, palette.error, modifier = Modifier.padding(top = 10.dp)) }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, enabled = !state.approving, onClick = { dispatch(PosSaleIntent.CancelApproval) }, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(if (state.approving) R.string.pos_approving else R.string.pos_approve),
                enabled = !state.approving && id.isNotBlank() && password.isNotEmpty(),
                onClick = { dispatch(PosSaleIntent.SubmitApproval(ManagerCredentials(id, password, notes.ifBlank { null }))) },
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
