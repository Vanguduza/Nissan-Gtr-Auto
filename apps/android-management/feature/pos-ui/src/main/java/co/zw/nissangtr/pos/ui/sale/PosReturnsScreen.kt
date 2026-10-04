package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CoreResolution
import co.zw.nissangtr.pos.domain.model.CoreReturnInput
import co.zw.nissangtr.pos.domain.model.InvoiceDetail
import co.zw.nissangtr.pos.domain.model.InvoiceLine
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ReasonCode
import co.zw.nissangtr.pos.domain.model.ReturnCondition
import co.zw.nissangtr.pos.domain.model.ReturnDraft
import co.zw.nissangtr.pos.domain.model.ReturnLine
import co.zw.nissangtr.pos.domain.model.ReturnResolution
import co.zw.nissangtr.pos.domain.model.WarrantyClaim
import co.zw.nissangtr.pos.domain.model.WarrantyDecision
import co.zw.nissangtr.pos.domain.model.blockedFor
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.ReturnsIntent
import co.zw.nissangtr.pos.domain.state.terminalReason
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosField
import co.zw.nissangtr.pos.ui.common.PosModal
import co.zw.nissangtr.pos.ui.common.PosPanel
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.PosRowEnd
import co.zw.nissangtr.pos.ui.common.PosSegmented
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.formatMoney
import co.zw.nissangtr.pos.ui.common.formatQty
import co.zw.nissangtr.pos.ui.home.EmptyCard
import co.zw.nissangtr.pos.ui.home.SoftButton

/*
 * Returns (Blueprint §10, phase 6): Sales → open the sale → take back what can still come back, old
 * cores and warranty claims; Warranty claims → decide and close. An approver posts through the
 * approval dialog (badge, password or own sign-in).
 */

private enum class ReturnsTab { Sales, Warranty }

@Composable
internal fun returnResolutionLabel(r: ReturnResolution): Int = when (r) {
    ReturnResolution.CashRefund -> R.string.pos_rt_res_cash
    ReturnResolution.CreditNote -> R.string.pos_rt_res_credit
    ReturnResolution.StoreCredit -> R.string.pos_rt_res_store
    ReturnResolution.Replacement -> R.string.pos_rt_res_swap
    ReturnResolution.Warranty -> R.string.pos_rt_res_warranty
}

private fun resolutionHint(r: ReturnResolution): Int = when (r) {
    ReturnResolution.CashRefund -> R.string.pos_rt_res_cash_hint
    ReturnResolution.CreditNote -> R.string.pos_rt_res_credit_hint
    ReturnResolution.StoreCredit -> R.string.pos_rt_res_store_hint
    ReturnResolution.Replacement -> R.string.pos_rt_res_swap_hint
    ReturnResolution.Warranty -> R.string.pos_rt_res_warranty_hint
}

private fun blockText(code: String): Int = when (code) {
    "named_customer" -> R.string.pos_rt_err_named_customer
    "till_closed" -> R.string.pos_rt_err_till_closed
    "nothing_paid" -> R.string.pos_rt_err_nothing_paid
    else -> R.string.pos_rt_err_one_part
}

private fun conditionLabel(c: ReturnCondition): Int = when (c) {
    ReturnCondition.Sealed -> R.string.pos_rt_cond_sealed
    ReturnCondition.Unopened -> R.string.pos_rt_cond_unopened
    ReturnCondition.Opened -> R.string.pos_rt_cond_opened
    ReturnCondition.Damaged -> R.string.pos_rt_cond_damaged
    ReturnCondition.Defective -> R.string.pos_rt_cond_defective
}

@Composable
fun ReturnsScreen(state: PosState, dispatch: (PosIntent) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(ReturnsTab.Sales) }
    PosPanel(stringResource(R.string.pos_returns_title)) {
        PosSegmented(
            ReturnsTab.entries,
            tab,
            label = { stringResource(if (it == ReturnsTab.Sales) R.string.pos_rt_tab_sales else R.string.pos_rt_tab_warranty) },
            onSelect = { tab = it },
        )
        Spacer(Modifier.height(12.dp))
        when {
            tab == ReturnsTab.Warranty -> WarrantyClaims(state, dispatch)
            state.returnSale != null -> SaleReturn(state, dispatch)
            else -> SaleSearch(state, dispatch)
        }
    }
}

@Composable
private fun SaleSearch(state: PosState, dispatch: (PosIntent) -> Unit) {
    var query by rememberSaveable { mutableStateOf(state.invoiceQuery) }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(300)
        if (query != state.invoiceQuery || state.invoices == null) dispatch(PosSaleIntent.LoadInvoices(query))
    }
    PosText(stringResource(R.string.pos_rt_open_hint), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
    Spacer(Modifier.height(8.dp))
    PosField(stringResource(R.string.pos_invoice_search), query, { query = it }, placeholder = stringResource(R.string.pos_invoice_search_hint))
    Spacer(Modifier.height(8.dp))
    val invoices = state.invoices
    when {
        invoices == null -> EmptyCard(stringResource(R.string.pos_loading))
        invoices.isEmpty() -> EmptyCard(stringResource(R.string.pos_no_invoices))
        else -> invoices.forEach { inv ->
            ListRow(
                title = inv.documentNumber ?: inv.id.take(8),
                subtitle = listOfNotNull(inv.customerName, inv.vehicleLabel, inv.postedAt?.take(16)?.replace('T', ' ')).joinToString(" · ").ifBlank { null },
                trailing = formatMoney(inv.total),
                onClick = { dispatch(ReturnsIntent.Open(inv)) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaleReturn(state: PosState, dispatch: (PosIntent) -> Unit) {
    val palette = PosTheme.palette
    val sale = state.returnSale ?: return
    val inv = state.returnInvoice
    // Line id → (qty, condition) chosen to come back.
    val picks = remember(sale.id) { mutableStateMapOf<String, Pair<Double, ReturnCondition>>() }
    var claimLine by remember(sale.id) { mutableStateOf<InvoiceLine?>(null) }
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SoftButton(stringResource(R.string.pos_rt_back), PosIcons.ArrowLeft, enabled = true, onClick = { dispatch(ReturnsIntent.Close) }, modifier = Modifier.width(120.dp))
        if (inv != null) {
            SoftButton(
                stringResource(R.string.pos_rt_refund_whole),
                PosIcons.RefreshCw,
                enabled = inv.untouched && state.online,
                onClick = { dispatch(PosSaleIntent.RequestApproval(ApprovalRequest.Refund(sale))) },
                modifier = Modifier.width(200.dp),
            )
            SoftButton(
                stringResource(R.string.pos_rt_card_refund),
                PosIcons.ShoppingCart,
                enabled = inv.untouched && state.online && state.terminalReason() == null && state.cardRefund == null,
                onClick = { dispatch(ReturnsIntent.CardRefund) },
                modifier = Modifier.width(230.dp),
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    if (inv == null) {
        EmptyCard(stringResource(R.string.pos_loading))
        return
    }
    state.cardRefund?.let { a ->
        Column(Modifier.fillMaxWidth().clip(PosTheme.shape.sm).background(palette.canvas).padding(14.dp)) {
            PosText(stringResource(R.string.pos_rt_card_follow, a.terminalLabel ?: stringResource(R.string.pos_ct_machine)), PosTheme.type.bodyPrimary.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary)
            PosText(formatMoney(a.amount), PosTheme.type.bodySecondary, palette.textSecondary)
        }
        Spacer(Modifier.height(10.dp))
    }
    Facts(
        listOf(
            stringResource(R.string.pos_rt_sale) to (inv.documentNumber ?: sale.documentNumber.orEmpty()),
            stringResource(R.string.pos_rt_customer) to (sale.customerName ?: stringResource(R.string.pos_rt_walk_in)),
            stringResource(R.string.pos_rt_paid) to stringResource(R.string.pos_rt_paid_of, formatMoney(inv.amountPaid), formatMoney(inv.total)),
        ),
    )
    PosText(stringResource(R.string.pos_rt_parts), PosTheme.type.heading2.copy(fontWeight = FontWeight.Bold), palette.textPrimary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    inv.parts.forEach { line ->
        val pick = picks[line.id] ?: (0.0 to ReturnCondition.Opened)
        ListRow(
            title = line.description ?: line.oemPartNumber,
            subtitle = listOfNotNull(
                stringResource(R.string.pos_rt_line_meta, line.oemPartNumber, formatQty(line.qty), formatMoney(line.unitPrice)),
                if (line.returnableQty < line.qty) stringResource(R.string.pos_rt_can_come_back, formatQty(line.returnableQty)) else null,
            ).joinToString(" · "),
            trailing = if (line.returnableQty <= 0.0) stringResource(R.string.pos_rt_returned) else null,
        )
        if (line.returnableQty > 0.0) {
            FlowRow(Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                QtyStepper(pick.first, line.returnableQty) { picks[line.id] = it to pick.second }
                // Scrolls sideways on a phone rather than cutting the last conditions off.
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    PosSegmented(
                        ReturnCondition.entries,
                        pick.second,
                        label = { stringResource(conditionLabel(it)) },
                        onSelect = { picks[line.id] = pick.first to it },
                    )
                }
                SoftButton(stringResource(R.string.pos_rt_warranty_claim), PosIcons.Wrench, enabled = state.online, onClick = { claimLine = line }, modifier = Modifier.width(180.dp))
            }
        }
    }
    val chosen = inv.parts.mapNotNull { l -> picks[l.id]?.takeIf { it.first > 0.0 }?.let { l to it } }
    if (chosen.isNotEmpty()) ReturnForm(state, inv, chosen, dispatch)
    if (inv.cores.isNotEmpty()) {
        PosText(stringResource(R.string.pos_rt_cores), PosTheme.type.heading2.copy(fontWeight = FontWeight.Bold), palette.textPrimary, modifier = Modifier.padding(top = 16.dp))
        PosText(stringResource(R.string.pos_rt_cores_hint), PosTheme.type.bodySecondary, palette.textMuted)
        inv.cores.forEach { CoreRow(state, inv, it, dispatch) }
    }
    claimLine?.let { line -> OpenClaimDialog(state, line, dispatch) { claimLine = null } }
}

@Composable
private fun Facts(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (k, v) ->
            Row {
                PosText(k, PosTheme.type.bodySecondary, PosTheme.palette.textMuted, modifier = Modifier.width(110.dp))
                PosText(v, PosTheme.type.bodyPrimary.copy(fontWeight = FontWeight.SemiBold), PosTheme.palette.textPrimary)
            }
        }
    }
}

@Composable
private fun QtyStepper(qty: Double, max: Double, onChange: (Double) -> Unit) {
    val palette = PosTheme.palette
    Row(
        Modifier.clip(PosTheme.shape.sm).background(palette.canvas).padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoftButton("−", null, enabled = qty > 0.0, onClick = { onChange((qty - 1.0).coerceAtLeast(0.0)) }, modifier = Modifier.width(44.dp))
        PosText(formatQty(qty), PosTheme.type.bodyPrimary.copy(fontWeight = FontWeight.Bold), palette.textPrimary, modifier = Modifier.width(36.dp), align = TextAlign.Center)
        SoftButton("+", null, enabled = qty < max, onClick = { onChange((qty + 1.0).coerceAtMost(max)) }, modifier = Modifier.width(44.dp))
    }
}

@Composable
private fun ReasonChooser(reasons: List<ReasonCode>?, selected: String?, onSelect: (String) -> Unit) {
    PosText(stringResource(R.string.pos_rt_reason), PosTheme.type.labelMeta.copy(fontWeight = FontWeight.SemiBold), PosTheme.palette.textMuted, modifier = Modifier.padding(top = 10.dp))
    when {
        reasons == null -> PosText(stringResource(R.string.pos_loading), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
        reasons.isEmpty() -> PosText(stringResource(R.string.pos_till_no_reasons), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
        else -> reasons.forEach { r ->
            ListRow(
                title = r.label,
                subtitle = if (r.requiresNotes) stringResource(R.string.pos_till_reason_needs_notes) else null,
                selected = r.code == selected,
                onClick = { onSelect(r.code) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReturnForm(state: PosState, inv: InvoiceDetail, chosen: List<Pair<InvoiceLine, Pair<Double, ReturnCondition>>>, dispatch: (PosIntent) -> Unit) {
    val palette = PosTheme.palette
    var resolution by rememberSaveable(inv.id) { mutableStateOf<ReturnResolution?>(null) }
    var reasonCode by rememberSaveable(inv.id) { mutableStateOf<String?>(null) }
    var notes by rememberSaveable(inv.id) { mutableStateOf("") }
    val reasons = state.returnReasons["return_post"]
    val reason = reasons?.firstOrNull { it.code == reasonCode }
    val amount = Money(chosen.sumOf { (l, p) -> l.valueOf(p.first).minor }, inv.total.currency)
    val tillOpen = state.till.canSell
    val blocked = resolution?.blockedFor(inv, chosen.size, tillOpen)
    val ready = resolution != null && blocked == null && reason != null && (!reason.requiresNotes || notes.isNotBlank()) && !state.returnsBusy && state.online
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().clip(PosTheme.shape.md).background(palette.canvas).padding(14.dp)) {
        PosText(
            stringResource(R.string.pos_rt_return_title, formatQty(chosen.sumOf { it.second.first }), formatMoney(amount)),
            PosTheme.type.heading2.copy(fontWeight = FontWeight.Bold),
            palette.textPrimary,
        )
        PosText(stringResource(R.string.pos_rt_value_hint), PosTheme.type.bodySecondary, palette.textMuted)
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cardWidth = if (maxWidth < 520.dp) maxWidth else (maxWidth - 24.dp) / 3
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ReturnResolution.entries.forEach { r ->
                    val why = r.blockedFor(inv, chosen.size, tillOpen)
                    ChoiceCard(
                        title = stringResource(returnResolutionLabel(r)),
                        hint = stringResource(why?.let(::blockText) ?: resolutionHint(r)),
                        selected = resolution == r,
                        enabled = why == null,
                        width = cardWidth,
                        onClick = { resolution = r },
                    )
                }
            }
        }
        ReasonChooser(reasons, reasonCode) { reasonCode = it }
        Spacer(Modifier.height(8.dp))
        PosField(stringResource(if (reason?.requiresNotes == true) R.string.pos_till_notes_required else R.string.pos_notes_optional), notes, { notes = it })
        if (state.returnDraft != null) PosText(stringResource(R.string.pos_rt_draft_kept), PosTheme.type.bodySecondary, palette.textMuted, modifier = Modifier.padding(top = 8.dp))
        PosRowEnd {
            PosPrimaryButton(
                stringResource(if (state.returnsBusy) R.string.pos_loading else R.string.pos_rt_post),
                enabled = ready,
                icon = PosIcons.Package,
                onClick = {
                    val r = resolution ?: return@PosPrimaryButton
                    dispatch(
                        ReturnsIntent.Submit(
                            ReturnDraft(
                                invoiceId = inv.id,
                                resolution = r,
                                reasonCode = reasonCode.orEmpty(),
                                notes = notes.trim().ifEmpty { null },
                                lines = chosen.map { (l, p) -> ReturnLine(l.id, p.first, p.second) },
                                tillSessionId = state.till.session?.id,
                            ),
                            amount,
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun ChoiceCard(title: String, hint: String, selected: Boolean, enabled: Boolean, width: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val palette = PosTheme.palette
    Column(
        Modifier
            .width(width)
            .clip(PosTheme.shape.sm)
            .border(if (selected) 2.dp else 1.dp, if (selected) palette.brandRed else palette.borderSubtle, PosTheme.shape.sm)
            .background(palette.surfacePrimary)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
    ) {
        PosText(
            title,
            PosTheme.type.bodyPrimary.copy(fontWeight = FontWeight.Bold),
            if (!enabled) palette.textMuted else if (selected) palette.brandRed else palette.textPrimary,
        )
        PosText(hint, PosTheme.type.bodySecondary, palette.textMuted)
    }
}

@Composable
private fun CoreRow(state: PosState, inv: InvoiceDetail, line: InvoiceLine, dispatch: (PosIntent) -> Unit) {
    var open by rememberSaveable(line.id) { mutableStateOf(false) }
    var qty by rememberSaveable(line.id) { mutableStateOf(1.0) }
    var resolution by rememberSaveable(line.id) { mutableStateOf(if (inv.customerId != null) CoreResolution.AccountCredit else CoreResolution.CashRefund) }
    var reasonCode by rememberSaveable(line.id) { mutableStateOf<String?>(null) }
    var notes by rememberSaveable(line.id) { mutableStateOf("") }
    val reasons = state.returnReasons["core_return"]
    val reason = reasons?.firstOrNull { it.code == reasonCode }
    val tillOpen = state.till.canSell || inv.tillSessionId != null
    val allowed = { r: CoreResolution -> if (r == CoreResolution.CashRefund) tillOpen else inv.customerId != null }
    val amount = Money(Math.round(line.unitPrice.minor * qty), line.unitPrice.currency)
    ListRow(
        title = line.description ?: line.oemPartNumber,
        subtitle = stringResource(R.string.pos_rt_core_meta, line.oemPartNumber, formatMoney(line.unitPrice), formatQty(line.returnableQty), formatQty(line.qty)),
        trailing = if (line.returnableQty <= 0.0) stringResource(R.string.pos_rt_core_done) else null,
    ) {
        if (line.returnableQty > 0.0 && !open) SoftButton(stringResource(R.string.pos_rt_core_take), PosIcons.RefreshCw, enabled = state.online, onClick = { open = true }, modifier = Modifier.width(180.dp))
    }
    if (!open || line.returnableQty <= 0.0) return
    Column(Modifier.fillMaxWidth().clip(PosTheme.shape.md).background(PosTheme.palette.canvas).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PosText(stringResource(R.string.pos_rt_core_qty), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
            QtyStepper(qty, line.returnableQty) { qty = it.coerceAtLeast(1.0) }
        }
        Spacer(Modifier.height(8.dp))
        PosSegmented(
            CoreResolution.entries,
            resolution,
            label = {
                stringResource(
                    when (it) {
                        CoreResolution.CashRefund -> R.string.pos_rt_core_cash
                        CoreResolution.AccountCredit -> R.string.pos_rt_core_account
                        CoreResolution.StoreCredit -> R.string.pos_rt_core_store
                    },
                )
            },
            onSelect = { if (allowed(it)) resolution = it },
        )
        if (!allowed(resolution)) {
            PosText(
                stringResource(if (resolution == CoreResolution.CashRefund) R.string.pos_rt_err_till_closed else R.string.pos_rt_err_named_customer),
                PosTheme.type.bodySecondary,
                PosTheme.palette.error,
            )
        }
        ReasonChooser(reasons, reasonCode) { reasonCode = it }
        Spacer(Modifier.height(8.dp))
        PosField(stringResource(if (reason?.requiresNotes == true) R.string.pos_till_notes_required else R.string.pos_notes_optional), notes, { notes = it })
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, enabled = true, onClick = { open = false }, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(R.string.pos_rt_core_give, formatMoney(amount)),
                enabled = state.online && allowed(resolution) && reason != null && (!reason.requiresNotes || notes.isNotBlank()),
                onClick = {
                    dispatch(
                        ReturnsIntent.Core(
                            CoreReturnInput(inv.id, line.id, qty, resolution, reasonCode.orEmpty(), state.till.session?.id, notes.trim().ifEmpty { null }),
                            amount,
                        ),
                    )
                    open = false
                },
            )
        }
    }
}

@Composable
private fun OpenClaimDialog(state: PosState, line: InvoiceLine, dispatch: (PosIntent) -> Unit, onDismiss: () -> Unit) {
    var serial by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    val lookup = state.serialLookup?.takeIf { it.serial.equals(serial.trim(), ignoreCase = true) }
    val match = lookup?.match?.takeIf { it.stockItemId == line.stockItemId }
    PosModal(stringResource(R.string.pos_rt_claim_title, line.description ?: line.oemPartNumber), onDismiss = onDismiss, width = 520.dp) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PosField(stringResource(R.string.pos_rt_serial), serial, { serial = it }, modifier = Modifier.weight(1f))
            SoftButton(stringResource(R.string.pos_rt_check), null, enabled = serial.isNotBlank() && state.online, onClick = { dispatch(ReturnsIntent.CheckSerial(serial)) }, modifier = Modifier.width(110.dp))
        }
        lookup?.let {
            PosText(
                when {
                    match != null -> stringResource(R.string.pos_rt_serial_ok, match.serialNumber)
                    it.match != null -> stringResource(R.string.pos_rt_serial_other)
                    else -> stringResource(R.string.pos_rt_serial_none)
                },
                PosTheme.type.bodySecondary,
                if (match != null) PosTheme.palette.success else PosTheme.palette.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        PosField(stringResource(R.string.pos_rt_whats_wrong), notes, { notes = it })
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, enabled = true, onClick = onDismiss, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(R.string.pos_rt_open_claim),
                enabled = notes.isNotBlank() && (serial.isBlank() || match != null) && !state.returnsBusy && state.online,
                icon = PosIcons.Wrench,
                onClick = {
                    dispatch(ReturnsIntent.OpenClaim(line.id, match?.id, notes))
                    onDismiss()
                },
            )
        }
    }
}

private val CLAIM_FILTERS = listOf("open", "approved", "rejected", "closed", null)

@Composable
private fun WarrantyClaims(state: PosState, dispatch: (PosIntent) -> Unit) {
    var query by rememberSaveable { mutableStateOf(state.warrantyQuery) }
    var deciding by remember { mutableStateOf<WarrantyClaim?>(null) }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(300)
        dispatch(ReturnsIntent.LoadClaims(state.warrantyStatus, query))
    }
    PosSegmented(
        CLAIM_FILTERS,
        state.warrantyStatus,
        label = {
            stringResource(
                when (it) {
                    "open" -> R.string.pos_wc_waiting
                    "approved" -> R.string.pos_wc_approved
                    "rejected" -> R.string.pos_wc_rejected
                    "closed" -> R.string.pos_wc_closed
                    else -> R.string.pos_wc_all
                },
            )
        },
        onSelect = { dispatch(ReturnsIntent.LoadClaims(it, query)) },
    )
    Spacer(Modifier.height(8.dp))
    PosField(stringResource(R.string.pos_invoice_search), query, { query = it }, placeholder = stringResource(R.string.pos_wc_search))
    Spacer(Modifier.height(8.dp))
    val claims = state.warrantyClaims
    when {
        claims == null -> EmptyCard(stringResource(R.string.pos_loading))
        claims.isEmpty() -> EmptyCard(stringResource(R.string.pos_wc_none))
        else -> claims.forEach { c ->
            ListRow(
                title = listOfNotNull(c.documentNumber ?: c.id.take(8), c.oemPartNumber, c.serialNumber?.let { "S/N $it" }).joinToString(" · "),
                subtitle = listOfNotNull(
                    claimStatus(c),
                    c.invoiceNumber,
                    c.notes,
                    c.rejectReason?.let { stringResource(R.string.pos_wc_rejected_because, it) },
                    (c.decidedAt ?: c.createdAt).take(16).replace('T', ' '),
                ).joinToString(" · "),
            ) {
                when (c.status) {
                    "open" -> PosPrimaryButton(stringResource(R.string.pos_wc_decide), enabled = state.online, onClick = { deciding = c }, modifier = Modifier.width(130.dp))
                    "approved", "rejected" -> SoftButton(stringResource(R.string.pos_wc_close), null, enabled = state.online, onClick = { dispatch(ReturnsIntent.CloseClaim(c.id)) }, modifier = Modifier.width(150.dp))
                }
            }
        }
    }
    deciding?.let { c -> WarrantyDecideDialog(state, c, dispatch) { deciding = null } }
}

@Composable
private fun claimStatus(c: WarrantyClaim): String = when (c.status) {
    "open" -> stringResource(R.string.pos_wc_status_open)
    "approved" -> listOfNotNull(
        stringResource(R.string.pos_wc_approved),
        when (c.resolution) {
            "replacement" -> stringResource(R.string.pos_wc_res_replacement)
            "credit_note" -> stringResource(R.string.pos_wc_res_credit)
            "return_only" -> stringResource(R.string.pos_wc_res_return_only)
            else -> null
        },
    ).joinToString(" · ")
    "rejected" -> stringResource(R.string.pos_wc_rejected)
    "closed" -> stringResource(R.string.pos_wc_closed)
    else -> c.status
}

private enum class DecisionKind { Replace, Credit, TakeBack, Reject }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WarrantyDecideDialog(state: PosState, claim: WarrantyClaim, dispatch: (PosIntent) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf<DecisionKind?>(null) }
    var qty by remember { mutableStateOf(1.0) }
    var reason by remember { mutableStateOf("") }
    PosModal(stringResource(R.string.pos_wc_decide_title, claim.documentNumber.orEmpty(), claim.oemPartNumber.orEmpty()), onDismiss = onDismiss, width = 620.dp) {
        claim.notes?.let { PosText(it, PosTheme.type.bodySecondary, PosTheme.palette.textMuted) }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val w = if (maxWidth < 480.dp) maxWidth else (maxWidth - 12.dp) / 2
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    Triple(DecisionKind.Replace, R.string.pos_wc_replace, R.string.pos_wc_replace_hint),
                    Triple(DecisionKind.Credit, R.string.pos_wc_credit, R.string.pos_wc_credit_hint),
                    Triple(DecisionKind.TakeBack, R.string.pos_wc_takeback, R.string.pos_wc_takeback_hint),
                    Triple(DecisionKind.Reject, R.string.pos_wc_reject, R.string.pos_wc_reject_hint),
                ).forEach { (k, title, hint) ->
                    val enabled = k != DecisionKind.Replace && k != DecisionKind.Credit || claim.invoiceId != null && claim.stockItemId != null
                    ChoiceCard(stringResource(title), stringResource(hint), kind == k, enabled, w) { kind = k }
                }
            }
        }
        when (kind) {
            DecisionKind.Reject -> {
                Spacer(Modifier.height(10.dp))
                PosField(stringResource(R.string.pos_wc_reject_reason), reason, { reason = it })
            }
            DecisionKind.Replace, DecisionKind.Credit -> Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PosText(stringResource(R.string.pos_wc_qty), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
                QtyStepper(qty, 99.0) { qty = it.coerceAtLeast(1.0) }
            }
            else -> Unit
        }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, enabled = true, onClick = onDismiss, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(R.string.pos_wc_save),
                enabled = kind != null && (kind != DecisionKind.Reject || reason.isNotBlank()) && state.online,
                onClick = {
                    val decision = when (kind) {
                        // The replacement goes out in the part's stock unit (claims are per unit).
                        // Null unit: the server side resolves it from the original sale line.
                        DecisionKind.Replace -> WarrantyDecision.Replace(qty, state.returnInvoice?.lines?.firstOrNull { it.stockItemId == claim.stockItemId }?.uomId)
                        DecisionKind.Credit -> WarrantyDecision.Credit(qty)
                        DecisionKind.TakeBack -> WarrantyDecision.TakeBack
                        DecisionKind.Reject -> WarrantyDecision.Reject(reason)
                        null -> return@PosPrimaryButton
                    }
                    dispatch(ReturnsIntent.Decide(claim, decision))
                    onDismiss()
                },
            )
        }
    }
}

/** Stock of one part at every branch: on the shelf, held for orders, free to sell, on the way. */
@Composable
fun StockByBranchDialog(state: PosState, dispatch: (PosIntent) -> Unit) {
    val part = state.stockPart ?: return
    val palette = PosTheme.palette
    PosModal(stringResource(R.string.pos_stock_title), onDismiss = { dispatch(ReturnsIntent.HideStock) }, width = 560.dp) {
        PosText("${part.name} · ${part.oemPartNumber}", PosTheme.type.bodySecondary, palette.textMuted)
        Spacer(Modifier.height(10.dp))
        val rows = state.branchStock
        when {
            !state.online -> EmptyCard(stringResource(R.string.pos_error_offline))
            rows == null -> EmptyCard(stringResource(R.string.pos_loading))
            rows.isEmpty() -> EmptyCard(stringResource(R.string.pos_stock_none))
            else -> {
                StockRow(
                    stringResource(R.string.pos_stock_branch), null,
                    listOf(R.string.pos_stock_on_hand, R.string.pos_stock_held, R.string.pos_stock_free, R.string.pos_stock_incoming).map { stringResource(it) },
                    header = true,
                )
                rows.forEach { r ->
                    StockRow(
                        r.name.ifBlank { r.code },
                        r.code + if (r.warehouseId == state.till.session?.warehouseId) " · " + stringResource(R.string.pos_stock_here) else "",
                        listOf(formatQty(r.onHand), formatQty(r.reserved), formatQty(r.available), if (r.incoming > 0) formatQty(r.incoming) else "–"),
                        freeInStock = r.available > 0,
                    )
                }
            }
        }
    }
}

@Composable
private fun StockRow(title: String, subtitle: String?, cells: List<String>, header: Boolean = false, freeInStock: Boolean = false) {
    val palette = PosTheme.palette
    val style = if (header) PosTheme.type.labelMeta.copy(fontWeight = FontWeight.SemiBold) else PosTheme.type.bodyPrimary
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.6f)) {
            PosText(title, if (header) style else style.copy(fontWeight = FontWeight.SemiBold), if (header) palette.textMuted else palette.textPrimary)
            subtitle?.let { PosText(it, PosTheme.type.bodySecondary, palette.textMuted) }
        }
        cells.forEachIndexed { i, c ->
            PosText(
                c,
                if (!header && i == 2) style.copy(fontWeight = FontWeight.Bold) else style,
                when {
                    header -> palette.textMuted
                    i == 2 -> if (freeInStock) palette.success else palette.error
                    else -> palette.textPrimary
                },
                modifier = Modifier.weight(1f),
                align = TextAlign.End,
            )
        }
    }
}
