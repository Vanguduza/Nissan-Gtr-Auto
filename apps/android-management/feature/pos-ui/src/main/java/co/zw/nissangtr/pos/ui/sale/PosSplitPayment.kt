package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.RefundFeePolicy
import co.zw.nissangtr.pos.domain.model.SplitLeg
import co.zw.nissangtr.pos.domain.model.SplitRecoveryItem
import co.zw.nissangtr.pos.domain.model.SplitRefund
import co.zw.nissangtr.pos.domain.model.SplitRefundStep
import co.zw.nissangtr.pos.domain.model.SplitSession
import co.zw.nissangtr.pos.domain.model.SplitTender
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.SplitIntent
import co.zw.nissangtr.pos.domain.state.terminalReason
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosField
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.PosRowEnd
import co.zw.nissangtr.pos.ui.common.PosSegmented
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.formatMoney
import co.zw.nissangtr.pos.ui.common.formatQty
import co.zw.nissangtr.pos.ui.home.EmptyCard
import co.zw.nissangtr.pos.ui.home.SoftButton

private fun Money.plain(): String = java.math.BigDecimal.valueOf(minor, 2).toPlainString()

@Composable
private fun tenderName(raw: String): String = when (raw) {
    "cash" -> stringResource(R.string.pos_tender_cash)
    "bank" -> stringResource(R.string.pos_tender_bank)
    "store_credit" -> stringResource(R.string.pos_tender_store_credit)
    "ecocash" -> stringResource(R.string.pos_tender_ecocash)
    "paynow" -> stringResource(R.string.pos_tender_paynow)
    "contipay" -> stringResource(R.string.pos_tender_contipay)
    "card_terminal" -> stringResource(R.string.pos_ct_machine)
    else -> humanState(raw)
}

@Composable
private fun legStatus(raw: String): String = stringResource(
    when (raw) {
        "captured" -> R.string.pos_split_leg_received
        "held" -> R.string.pos_split_leg_held
        "pending" -> R.string.pos_split_leg_pending
        "unknown" -> R.string.pos_split_leg_unknown
        "allocated" -> R.string.pos_split_leg_applied
        "refund_review" -> R.string.pos_split_leg_refund_review
        "refund_pending" -> R.string.pos_split_leg_refund_pending
        "refunded" -> R.string.pos_split_leg_refunded
        "failed" -> R.string.pos_split_leg_failed
        "cancelled" -> R.string.pos_split_leg_cancelled
        else -> R.string.pos_split_leg_planned
    },
)

@Composable
internal fun splitStatus(raw: String): String = stringResource(
    when (raw) {
        "partially_captured" -> R.string.pos_split_status_part_paid
        "leg_pending" -> R.string.pos_split_status_pending
        "fully_committed", "finalizing" -> R.string.pos_split_status_posting
        "settled" -> R.string.pos_split_status_settled
        "finalization_failed" -> R.string.pos_split_status_unposted
        "refund_review" -> R.string.pos_split_status_refund_review
        "refund_pending" -> R.string.pos_split_status_refund_pending
        "refunded" -> R.string.pos_split_status_refunded
        "cancelled" -> R.string.pos_split_leg_cancelled
        else -> R.string.pos_split_status_open
    },
)

@Composable
private fun refundStatus(raw: String): String = stringResource(
    when (raw) {
        "pending" -> R.string.pos_split_refund_approved
        "settled" -> R.string.pos_split_refund_paid
        "failed" -> R.string.pos_split_leg_failed
        "cancelled" -> R.string.pos_split_leg_cancelled
        else -> R.string.pos_split_refund_to_approve
    },
)

/** Total, received and still due: every figure the server's. */
@Composable
private fun SplitSummary(session: SplitSession) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    Row(
        Modifier.fillMaxWidth().clip(PosTheme.shape.sm).background(palette.canvas).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            PosText(stringResource(R.string.pos_split_title, splitStatus(session.status)), type.bodyPrimary.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary)
            PosText(
                stringResource(R.string.pos_split_received, formatMoney(session.received)) +
                    (if (session.pending.minor > 0) " · " + stringResource(R.string.pos_split_in_progress, formatMoney(session.pending)) else ""),
                type.labelMeta,
                palette.textMuted,
            )
        }
        // Once it stops taking parts, what matters is what was received (and any refund), not a balance.
        PosText(
            if (session.open) stringResource(R.string.pos_split_due, formatMoney(session.balanceDue)) else formatMoney(session.received),
            type.numericTotal,
            palette.textPrimary,
        )
    }
}

@Composable
private fun SplitLegs(session: SplitSession) {
    if (session.legs.isEmpty()) {
        EmptyCard(stringResource(R.string.pos_split_no_parts))
        return
    }
    session.legs.forEach { l: SplitLeg ->
        ListRow(
            title = "${l.sequenceNo}. ${tenderName(l.tender)} · ${legStatus(l.status)}",
            subtitle = listOfNotNull(
                l.reference,
                l.statusDetail,
                l.refundRequired?.takeIf { it.minor > 0 }?.let { stringResource(R.string.pos_split_to_refund, formatMoney(it)) },
            ).joinToString(" · ").ifBlank { null },
            trailing = formatMoney(l.amount),
        )
    }
}

/**
 * Part payments inside Payment (Blueprint §10.5, §9.4): one part per step, starting at the server's
 * remaining balance. Cash and card/bank are received at once; store credit is held until the sale
 * posts, which happens on its own when the parts cover it. If the customer cannot pay the rest, the
 * governed reduced basket (§10.8) keeps only what is paid for; cancelling turns money into refunds.
 */
@Composable
internal fun SplitPanel(state: PosState, session: SplitSession, dispatch: (PosIntent) -> Unit) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    var tender by rememberSaveable { mutableStateOf(SplitTender.Cash) }
    var amount by rememberSaveable { mutableStateOf(session.availableToAllocate.plain()) }
    var reference by rememberSaveable { mutableStateOf("") }
    var cashGiven by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("take") }
    // Each step starts at the server's balance (§9.4).
    LaunchedEffect(session.availableToAllocate.minor, session.legs.size) {
        amount = session.availableToAllocate.plain()
        reference = ""
        cashGiven = ""
    }
    val currency = session.total.currency
    val value = parseMoney(amount, currency)

    Spacer(Modifier.height(12.dp))
    SplitSummary(session)
    Spacer(Modifier.height(6.dp))
    SplitLegs(session)

    if (state.terminalBusy && state.terminalAttempt?.splitLegId != null) TerminalWaiting(state.terminalAttempt)
    if (!session.open) return

    when (mode) {
        "reduce" -> ReduceBasket(state, session, dispatch, onBack = { mode = "take" })
        "cancel" -> CancelParts(session, state.splitBusy, onCancel = { mode = "take" }) { reason, policy -> dispatch(SplitIntent.Cancel(reason, policy)) }
        else -> if (session.availableToAllocate.minor > 0) {
            Spacer(Modifier.height(10.dp))
            PosText(stringResource(R.string.pos_split_next_part), type.labelMeta.copy(fontWeight = FontWeight.SemiBold), palette.textMuted)
            Spacer(Modifier.height(6.dp))
            val noCustomer = stringResource(R.string.pos_split_store_credit_customer)
            val notForParts = stringResource(R.string.pos_split_provider_unavailable)
            SplitTenderCards(
                options = listOf(
                    Triple(SplitTender.Cash, tenderName("cash"), null),
                    Triple(SplitTender.Bank, tenderName("bank"), null),
                    Triple(SplitTender.StoreCredit, tenderName("store_credit"), if (state.customer == null) noCustomer else null),
                    Triple(SplitTender.CardTerminal, tenderName("card_terminal"), terminalReasonText(state.terminalReason())),
                ),
                disabled = listOf(tenderName("ecocash"), tenderName("paynow"), tenderName("contipay")).map { it to (notForParts) },
                selected = tender,
                onSelect = { tender = it },
            )
            Spacer(Modifier.height(8.dp))
            val amountField: @Composable (Modifier) -> Unit = { m ->
                PosField(stringResource(R.string.pos_split_amount, formatMoney(session.availableToAllocate)), amount, { amount = it }, keyboard = KeyboardType.Decimal, modifier = m)
            }
            val detailField: @Composable (Modifier) -> Unit = { m ->
                when (tender) {
                    SplitTender.Bank -> PosField(stringResource(R.string.pos_split_reference), reference, { reference = it }, modifier = m)
                    SplitTender.Cash -> {
                        val given = parseMoney(cashGiven, currency)
                        val change = if (given != null && value != null) given.minor - value.minor else null
                        PosField(
                            stringResource(R.string.pos_cash_given) + (change?.takeIf { it >= 0 }?.let { " · " + stringResource(R.string.pos_split_change, formatMoney(Money(it, currency))) } ?: ""),
                            cashGiven,
                            { cashGiven = it },
                            keyboard = KeyboardType.Decimal,
                            modifier = m,
                        )
                    }
                    SplitTender.StoreCredit, SplitTender.CardTerminal -> Unit
                }
            }
            val canTake = !state.splitBusy && value != null && value.minor > 0 && value.minor <= session.availableToAllocate.minor &&
                (tender != SplitTender.Bank || reference.isNotBlank()) && (tender != SplitTender.StoreCredit || state.customer != null) &&
                (tender != SplitTender.CardTerminal || (state.terminalReason() == null && !state.terminalBusy))
            val take: @Composable (Modifier) -> Unit = { m ->
                PosPrimaryButton(
                    label = if (state.splitBusy) stringResource(R.string.pos_completing) else stringResource(R.string.pos_split_take, value?.let { formatMoney(it) } ?: ""),
                    enabled = canTake,
                    onClick = {
                        if (tender == SplitTender.CardTerminal) dispatch(co.zw.nissangtr.pos.domain.state.TerminalIntent.PayPart(value!!))
                        else dispatch(SplitIntent.AddPart(tender, value!!, reference.ifBlank { null }))
                    },
                    modifier = m,
                )
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 520.dp) {
                    // Phone (§9.4): one column, the primary action full width in the lower third.
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        amountField(Modifier.fillMaxWidth())
                        detailField(Modifier.fillMaxWidth())
                        take(Modifier.fillMaxWidth())
                        if (session.received.minor > 0) {
                            SoftButton(stringResource(R.string.pos_split_cant_pay), null, enabled = !state.splitBusy, onClick = { mode = "reduce" }, modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                            amountField(Modifier.weight(1f))
                            if (tender == SplitTender.StoreCredit || tender == SplitTender.CardTerminal) Spacer(Modifier.weight(1f)) else detailField(Modifier.weight(1f))
                        }
                        PosRowEnd {
                            if (session.received.minor > 0) {
                                SoftButton(stringResource(R.string.pos_split_cant_pay), null, enabled = !state.splitBusy, onClick = { mode = "reduce" }, modifier = Modifier.widthIn(max = 260.dp))
                            }
                            take(Modifier)
                        }
                    }
                }
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PosText(stringResource(R.string.pos_split_stopping), type.bodySecondary, palette.textMuted)
                Spacer(Modifier.width(6.dp))
                PosText(
                    stringResource(R.string.pos_split_cancel),
                    type.labelAction,
                    palette.brandRed,
                    modifier = Modifier.clickable(role = Role.Button) { mode = "cancel" },
                )
            }
        }
    }
}

@Composable
private fun SplitTenderCards(
    options: List<Triple<SplitTender, String, String?>>,
    disabled: List<Pair<String, String>>,
    selected: SplitTender,
    onSelect: (SplitTender) -> Unit,
) {
    val palette = PosTheme.palette
    data class Card(val tender: SplitTender?, val label: String, val reason: String?)
    val cards = options.map { Card(it.first, it.second, it.third) } + disabled.map { Card(null, it.first, it.second) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth < 520.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        val active = c.tender != null && c.tender == selected
                        val usable = c.tender != null && c.reason == null
                        Column(
                            Modifier
                                .weight(1f)
                                .height(68.dp)
                                .clip(PosTheme.shape.sm)
                                .background(if (active) palette.canvas else palette.surfacePrimary)
                                .border(if (active) 2.dp else 1.dp, if (active) palette.textPrimary else palette.borderSubtle, PosTheme.shape.sm)
                                .clickable(enabled = usable, role = Role.RadioButton) { c.tender?.let(onSelect) }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            PosText(c.label, PosTheme.type.labelAction.copy(fontWeight = FontWeight.SemiBold), if (usable) palette.textPrimary else palette.textMuted, maxLines = 1)
                            c.reason?.let { PosText(it, PosTheme.type.labelMeta, palette.textMuted, maxLines = 2) }
                        }
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/**
 * Reduced basket (Blueprint §10.8): explicit, from the part-paid state only. The operator chooses
 * what the customer keeps; the server works out the legal total (never more than received),
 * re-reserves the stock and posts. Anything received over the new total is refunded.
 */
@Composable
private fun ReduceBasket(state: PosState, session: SplitSession, dispatch: (PosIntent) -> Unit, onBack: () -> Unit) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    val keep = remember { mutableStateMapOf<String, Double>().apply { state.cart.lines.forEach { put(it.lineId, it.qty) } } }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var notes by rememberSaveable { mutableStateOf("") }
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().clip(PosTheme.shape.sm).background(palette.canvas).padding(14.dp)) {
        PosText(stringResource(R.string.pos_split_reduce_title), type.bodyPrimary.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary)
        PosText(stringResource(R.string.pos_split_reduce_hint, formatMoney(session.received)), type.bodySecondary, palette.textMuted)
        Spacer(Modifier.height(8.dp))
        state.cart.lines.forEach { l ->
            val q = keep[l.lineId] ?: 0.0
            ListRow(
                title = l.name,
                subtitle = "${l.oemPartNumber} · ${formatMoney(l.unitPrice)} · " + stringResource(R.string.pos_split_on_sale, formatQty(l.qty)),
                trailing = stringResource(R.string.pos_split_keep, formatQty(q)),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SoftButton("−", null, enabled = q > 0, onClick = { keep[l.lineId] = (q - 1).coerceAtLeast(0.0) }, modifier = Modifier.size(48.dp))
                    SoftButton("+", null, enabled = q < l.qty, onClick = { keep[l.lineId] = (q + 1).coerceAtMost(l.qty) }, modifier = Modifier.size(48.dp))
                }
            }
        }
        PosField(stringResource(R.string.pos_split_notes), notes, { notes = it })
        Row(
            Modifier.padding(top = 10.dp).clickable(role = Role.Checkbox) { confirmed = !confirmed },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
            PosText(stringResource(R.string.pos_split_customer_agreed), type.bodyPrimary.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary)
        }
        val items = state.cart.lines.mapNotNull { l -> keep[l.lineId]?.takeIf { it > 0 }?.let { l.lineId to it } }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_split_back), null, enabled = true, onClick = onBack, modifier = Modifier.widthIn(max = 160.dp))
            PosPrimaryButton(
                stringResource(R.string.pos_split_finish_items),
                enabled = confirmed && items.isNotEmpty() && !state.splitBusy,
                onClick = { dispatch(SplitIntent.ReduceBasket(items, customerConfirmed = true, notes = notes)) },
            )
        }
    }
}

@Composable
private fun CancelParts(session: SplitSession, busy: Boolean, onCancel: () -> Unit, onConfirm: (String, RefundFeePolicy) -> Unit) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    var reason by rememberSaveable { mutableStateOf("") }
    var policy by rememberSaveable { mutableStateOf(RefundFeePolicy.ManualReview) }
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().clip(PosTheme.shape.sm).background(palette.canvas).padding(14.dp)) {
        PosText(stringResource(R.string.pos_split_cancel), type.bodyPrimary.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary)
        PosText(
            if (session.received.minor > 0) stringResource(R.string.pos_split_cancel_refund, formatMoney(session.received)) else stringResource(R.string.pos_split_cancel_nothing),
            type.bodySecondary,
            palette.textMuted,
        )
        Spacer(Modifier.height(8.dp))
        PosField(stringResource(R.string.pos_split_reason), reason, { reason = it })
        if (session.received.minor > 0) {
            Spacer(Modifier.height(8.dp))
            FeePolicyChooser(policy, includeLater = true) { policy = it }
        }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_split_keep_sale), null, enabled = true, onClick = onCancel, modifier = Modifier.widthIn(max = 180.dp))
            PosPrimaryButton(stringResource(R.string.pos_split_cancel), enabled = reason.isNotBlank() && !busy, onClick = { onConfirm(reason, policy) })
        }
    }
}

@Composable
private fun FeePolicyChooser(policy: RefundFeePolicy, includeLater: Boolean, onSelect: (RefundFeePolicy) -> Unit) {
    PosText(stringResource(R.string.pos_split_fees), PosTheme.type.labelMeta, PosTheme.palette.textMuted)
    PosSegmented(
        options = RefundFeePolicy.entries.filter { includeLater || it != RefundFeePolicy.ManualReview },
        selected = policy,
        label = {
            stringResource(
                when (it) {
                    RefundFeePolicy.ManualReview -> R.string.pos_split_fee_later
                    RefundFeePolicy.BusinessAbsorbs -> R.string.pos_split_fee_business
                    RefundFeePolicy.CustomerBears -> R.string.pos_split_fee_customer
                },
            )
        },
        onSelect = onSelect,
    )
}

// ---------------------------------------------------------------- Recovery

@Composable
internal fun SplitRecoveryRows(items: List<SplitRecoveryItem>, onOpen: (String) -> Unit) {
    if (items.isEmpty()) return
    PosText(stringResource(R.string.pos_split_recovery_heading), PosTheme.type.labelMeta.copy(fontWeight = FontWeight.SemiBold), PosTheme.palette.textMuted)
    items.forEach { item ->
        val s = item.session
        ListRow(
            title = "${item.documentNumber ?: stringResource(R.string.pos_split_sale)} · ${splitStatus(s.status)}",
            subtitle = listOfNotNull(
                item.customerName,
                stringResource(R.string.pos_split_received, formatMoney(s.received)),
                stringResource(R.string.pos_split_due, formatMoney(s.balanceDue)).takeIf { s.open },
                stringResource(R.string.pos_split_refund_open).takeIf { s.refunds.any { it.open } },
            ).joinToString(" · "),
            trailing = formatMoney(s.total),
            onClick = { onOpen(s.orderId) },
        )
    }
    Spacer(Modifier.height(10.dp))
}

/** Recovery for a part-paid sale: its parts and refunds, and only the safe next steps. */
@Composable
internal fun SplitRecoveryDetail(state: PosState, session: SplitSession, dispatch: (PosIntent) -> Unit) {
    val palette = PosTheme.palette
    var cancelling by rememberSaveable(session.sessionId) { mutableStateOf(false) }
    Spacer(Modifier.height(12.dp))
    SplitSummary(session)
    Spacer(Modifier.height(6.dp))
    SplitLegs(session)
    session.finalizationError?.let {
        PosText(stringResource(R.string.pos_split_not_posted, it), PosTheme.type.bodyPrimary, palette.error, modifier = Modifier.padding(top = 8.dp))
    }
    if (session.refunds.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        PosText(stringResource(R.string.pos_split_refunds), PosTheme.type.labelMeta.copy(fontWeight = FontWeight.SemiBold), palette.textMuted)
        session.refunds.forEach { RefundRow(state, session, it, dispatch) }
    }
    if (cancelling) {
        CancelParts(session, busy = false, onCancel = { cancelling = false }) { reason, policy ->
            cancelling = false
            dispatch(SplitIntent.CancelFromRecovery(session.sessionId, reason, policy))
        }
    }
    PosRowEnd {
        if (session.cancellable && !cancelling) {
            SoftButton(stringResource(R.string.pos_split_cancel), null, enabled = state.online, onClick = { cancelling = true }, modifier = Modifier.widthIn(max = 220.dp))
        }
        if (session.retryable) {
            PosPrimaryButton(stringResource(R.string.pos_split_post_again), enabled = state.online && !state.splitBusy, onClick = { dispatch(SplitIntent.Retry(session.sessionId)) })
        }
    }
}

/** One refund of a received part: approve (fees), record it paid (reference) or mark it failed — by an approver. */
@Composable
private fun RefundRow(state: PosState, session: SplitSession, refund: SplitRefund, dispatch: (PosIntent) -> Unit) {
    val palette = PosTheme.palette
    var step by rememberSaveable(refund.id) { mutableStateOf<String?>(null) }
    var policy by rememberSaveable(refund.id) { mutableStateOf(RefundFeePolicy.BusinessAbsorbs) }
    var fee by rememberSaveable(refund.id) { mutableStateOf("") }
    var ref by rememberSaveable(refund.id) { mutableStateOf("") }
    var text by rememberSaveable(refund.id) { mutableStateOf("") }
    val leg = session.legs.firstOrNull { it.id == refund.legId }
    ListRow(
        title = stringResource(R.string.pos_split_refund_of, leg?.let { "${tenderName(it.tender)} ${it.sequenceNo}" } ?: "") + " · " + refundStatus(refund.status),
        subtitle = listOfNotNull(
            refund.notes,
            refund.failureReason,
            refund.providerRef,
            refund.netToCustomer?.let { stringResource(R.string.pos_split_customer_gets, formatMoney(it)) },
        ).joinToString(" · ").ifBlank { null },
        trailing = formatMoney(refund.gross),
    )
    if (!refund.open) return
    val request = { s: SplitRefundStep ->
        step = null
        dispatch(PosSaleIntent.RequestApproval(ApprovalRequest.SplitRefund(session.orderId, refund.id, refund.gross, s)))
    }
    when (step) {
        null -> PosRowEnd {
            if (refund.status == "review" || refund.status == "failed") {
                PosPrimaryButton(stringResource(R.string.pos_split_approve_refund), enabled = state.online && !state.approving, onClick = { step = "approve" })
            }
            if (refund.status == "pending") {
                SoftButton(stringResource(R.string.pos_split_refund_failed), null, enabled = state.online, onClick = { step = "fail" }, modifier = Modifier.widthIn(max = 200.dp))
                PosPrimaryButton(stringResource(R.string.pos_split_refund_paid_action), enabled = state.online && !state.approving, onClick = { step = "complete" })
            }
        }
        else -> Column(Modifier.fillMaxWidth().clip(PosTheme.shape.sm).background(palette.canvas).padding(14.dp)) {
            when (step) {
                "approve" -> {
                    FeePolicyChooser(policy, includeLater = false) { policy = it }
                    if (policy == RefundFeePolicy.CustomerBears) {
                        PosField(stringResource(R.string.pos_split_fee_kept), fee, { fee = it }, keyboard = KeyboardType.Decimal)
                    }
                    PosField(stringResource(R.string.pos_split_notes), text, { text = it })
                }
                "complete" -> {
                    PosField(stringResource(R.string.pos_split_refund_reference), ref, { ref = it })
                    PosField(stringResource(R.string.pos_split_notes), text, { text = it })
                }
                else -> PosField(stringResource(R.string.pos_split_refund_what_failed), text, { text = it })
            }
            PosText(stringResource(R.string.pos_split_approver_needed), PosTheme.type.bodySecondary, palette.textMuted, modifier = Modifier.padding(top = 6.dp))
            PosRowEnd {
                SoftButton(stringResource(R.string.pos_split_back), null, enabled = true, onClick = { step = null }, modifier = Modifier.widthIn(max = 160.dp))
                PosPrimaryButton(
                    stringResource(R.string.pos_split_continue),
                    enabled = when (step) {
                        "complete" -> ref.isNotBlank()
                        "fail" -> text.isNotBlank()
                        else -> true
                    },
                    onClick = {
                        request(
                            when (step) {
                                "approve" -> SplitRefundStep.Approve(
                                    policy,
                                    if (policy == RefundFeePolicy.CustomerBears) parseMoney(fee, refund.gross.currency) else null,
                                    text.ifBlank { null },
                                )
                                "complete" -> SplitRefundStep.Complete(ref.trim(), text.ifBlank { null })
                                else -> SplitRefundStep.Fail(text.trim())
                            },
                        )
                    },
                )
            }
        }
    }
    // A signed record of this refund's status for the customer or their bank.
    LettersBlock(state, co.zw.nissangtr.pos.domain.model.LetterSource.splitRefund(refund.id), dispatch)
}
