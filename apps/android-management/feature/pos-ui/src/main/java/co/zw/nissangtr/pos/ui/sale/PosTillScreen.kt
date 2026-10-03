package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.icons.ArrowDownToLine
import co.zw.nissangtr.pos.design.icons.ArrowUpFromLine
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.icons.ShieldCheck
import co.zw.nissangtr.pos.design.icons.UserRoundCheck
import co.zw.nissangtr.pos.design.icons.Wallet
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.CASH_IN_REASONS
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.DenominationCount
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ReasonAction
import co.zw.nissangtr.pos.domain.model.ReasonCode
import co.zw.nissangtr.pos.domain.model.TillCloseResult
import co.zw.nissangtr.pos.domain.model.TillSession
import co.zw.nissangtr.pos.domain.model.TillStatus
import co.zw.nissangtr.pos.domain.model.denominationsFor
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.TillDialog
import co.zw.nissangtr.pos.domain.state.TillIntent
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosField
import co.zw.nissangtr.pos.ui.common.PosIcon
import co.zw.nissangtr.pos.ui.common.PosModal
import co.zw.nissangtr.pos.ui.common.PosPanel
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.PosRowEnd
import co.zw.nissangtr.pos.ui.common.PosSegmented
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.feedbackText
import co.zw.nissangtr.pos.ui.common.formatMoney
import co.zw.nissangtr.pos.ui.home.EmptyCard
import co.zw.nissangtr.pos.ui.home.SoftButton
import kotlin.math.abs

/*
 * Till destination (delta D-016): open with a float, cash in/out, hand over, and a blind
 * denominated close. Expected cash and the variance are the server's; the count never shows them
 * until it is submitted. Cash out, handover and variance approval ask a manager (ApprovalDialog).
 */

private fun tillMoney(text: String, currency: CurrencyCode): Money? =
    text.trim().replace(",", "").toDoubleOrNull()?.takeIf { it >= 0 }?.let { Money.ofMajor(it, currency) }

/** "US$ 50" for notes, "50c" for coins, the way a cashier names what is in the drawer. */
internal fun denominationLabel(minor: Long, currency: CurrencyCode): String =
    if (minor < 100) "${minor}c" else formatMoney(Money(minor, currency)).removeSuffix(".00")

@Composable
private fun statusLabel(session: TillSession?): String = when (session?.status) {
    TillStatus.Open -> stringResource(R.string.pos_till_status_open, session.currency.code)
    TillStatus.VariancePending -> stringResource(R.string.pos_till_status_variance)
    else -> stringResource(R.string.pos_till_status_closed)
}

/** Short till state for the header operator block. */
@Composable
fun tillHeaderLabel(state: PosState): String? = if (!state.till.enforced || !state.till.loaded) null else statusLabel(state.till.session)

@Composable
fun TillScreen(state: PosState, dispatch: (PosIntent) -> Unit) {
    val till = state.till
    TwoPane(
        first = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                till.closeResult?.let { CloseResultPanel(it, dispatch) }
                val session = till.session
                when {
                    !till.loaded -> PosPanel(stringResource(R.string.pos_nav_till)) { EmptyCard(stringResource(R.string.pos_loading)) }
                    session == null || session.status == TillStatus.Closed -> OpenTillPanel(state, dispatch)
                    session.status == TillStatus.VariancePending -> VariancePanel(state, session, dispatch)
                    else -> OpenSessionPanel(state, session, dispatch)
                }
            }
        },
        second = {
            PosPanel(stringResource(R.string.pos_till_recent)) {
                val history = till.history
                when {
                    history == null -> EmptyCard(stringResource(R.string.pos_loading))
                    history.isEmpty() -> EmptyCard(stringResource(R.string.pos_till_none_yet))
                    else -> history.forEach { s ->
                        ListRow(
                            title = s.openedAtIso.take(16).replace('T', ' '),
                            subtitle = statusLabel(s) + " · " + stringResource(R.string.pos_till_float, formatMoney(s.openingFloat)) +
                                (s.variance?.takeIf { it.minor != 0L }?.let { " · " + varianceText(it) } ?: ""),
                            trailing = s.countedCash?.let { formatMoney(it) },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun varianceText(variance: Money): String = when {
    variance.minor < 0 -> stringResource(R.string.pos_till_short, formatMoney(Money(abs(variance.minor), variance.currency)))
    variance.minor > 0 -> stringResource(R.string.pos_till_over, formatMoney(variance))
    else -> stringResource(R.string.pos_till_balanced)
}

@Composable
private fun OpenTillPanel(state: PosState, dispatch: (PosIntent) -> Unit) {
    var float by rememberSaveable { mutableStateOf("") }
    var currencyCode by rememberSaveable { mutableStateOf(state.currency.code) }
    val currency = CurrencyCode(currencyCode)
    val amount = tillMoney(float, currency)
    PosPanel(stringResource(R.string.pos_till_open_title)) {
        PosText(stringResource(R.string.pos_till_open_hint), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
        Spacer(Modifier.height(12.dp))
        PosSegmented(
            options = listOf(CurrencyCode.USD, CurrencyCode.ZIG),
            selected = currency,
            label = { it.code },
            onSelect = { currencyCode = it.code },
        )
        Spacer(Modifier.height(10.dp))
        PosField(stringResource(R.string.pos_till_opening_float), float, { float = it }, keyboard = KeyboardType.Decimal, placeholder = "0.00")
        PosRowEnd {
            PosPrimaryButton(
                stringResource(if (state.till.busy) R.string.pos_till_opening else R.string.pos_till_open),
                enabled = !state.till.busy && amount != null && state.online,
                icon = PosIcons.Wallet,
                onClick = { amount?.let { dispatch(TillIntent.Open(it)) } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OpenSessionPanel(state: PosState, session: TillSession, dispatch: (PosIntent) -> Unit) {
    val palette = PosTheme.palette
    PosPanel(statusLabel(session)) {
        PosText(
            stringResource(R.string.pos_till_opened_at, session.openedAtIso.take(16).replace('T', ' ')) + " · " +
                stringResource(R.string.pos_till_float, formatMoney(session.openingFloat)),
            PosTheme.type.bodySecondary,
            palette.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val enabled = !state.till.busy && state.online
            SoftButton(stringResource(R.string.pos_till_cash_in), PosIcons.ArrowDownToLine, enabled, { dispatch(TillIntent.ShowDialog(TillDialog.CashIn)) }, Modifier.width(170.dp))
            SoftButton(stringResource(R.string.pos_till_cash_out), PosIcons.ArrowUpFromLine, enabled, { dispatch(TillIntent.ShowDialog(TillDialog.CashOut)) }, Modifier.width(170.dp))
            SoftButton(stringResource(R.string.pos_till_handover), PosIcons.UserRoundCheck, enabled, { dispatch(TillIntent.ShowDialog(TillDialog.Handover)) }, Modifier.width(170.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PosIcon(PosIcons.ShieldCheck, tint = palette.textMuted, size = 16.dp)
            PosText(stringResource(R.string.pos_till_manager_note), PosTheme.type.bodySecondary, palette.textMuted)
        }
        PosRowEnd {
            PosPrimaryButton(
                stringResource(R.string.pos_till_close),
                enabled = !state.till.busy && state.online && state.cart.isEmpty,
                onClick = { dispatch(TillIntent.ShowDialog(TillDialog.Close)) },
            )
        }
        if (!state.cart.isEmpty) PosText(stringResource(R.string.pos_till_finish_sale_first), PosTheme.type.bodySecondary, palette.textMuted)
    }
}

@Composable
private fun ReasonPicker(reasons: List<ReasonCode>?, selected: ReasonCode?, onSelect: (ReasonCode) -> Unit) {
    when {
        reasons == null -> EmptyCard(stringResource(R.string.pos_loading))
        reasons.isEmpty() -> EmptyCard(stringResource(R.string.pos_till_no_reasons))
        else -> reasons.forEach { r ->
            ListRow(
                title = r.label,
                subtitle = if (r.requiresNotes) stringResource(R.string.pos_till_reason_needs_notes) else null,
                selected = selected?.code == r.code,
                onClick = { onSelect(r) },
            )
        }
    }
}

@Composable
private fun VariancePanel(state: PosState, session: TillSession, dispatch: (PosIntent) -> Unit) {
    var reasonCode by rememberSaveable { mutableStateOf<String?>(session.varianceReasonCode) }
    val reasons = state.till.reasons[ReasonAction.TILL_VARIANCE]
    val reason = reasons?.firstOrNull { it.code == reasonCode }
    PosPanel(stringResource(R.string.pos_till_status_variance)) {
        val variance = state.till.closeResult?.variance ?: session.variance
        variance?.let { PosText(varianceText(it), PosTheme.type.heading3.copy(fontWeight = FontWeight.Bold), PosTheme.palette.error) }
        session.countedCash?.let { counted ->
            PosText(
                stringResource(R.string.pos_till_counted_expected, formatMoney(counted), session.expectedCash?.let { formatMoney(it) } ?: "—"),
                PosTheme.type.bodySecondary,
                PosTheme.palette.textMuted,
            )
        }
        Spacer(Modifier.height(10.dp))
        PosText(stringResource(R.string.pos_till_variance_hint), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
        Spacer(Modifier.height(8.dp))
        ReasonPicker(reasons, reason) { reasonCode = it.code }
        PosRowEnd {
            PosPrimaryButton(
                stringResource(R.string.pos_till_manager_approval),
                enabled = reason != null && state.online && !state.approving,
                icon = PosIcons.ShieldCheck,
                onClick = { reason?.let { dispatch(TillIntent.ApproveVariance(it)) } },
            )
        }
    }
}

@Composable
private fun CloseResultPanel(result: TillCloseResult, dispatch: (PosIntent) -> Unit) {
    PosPanel(stringResource(if (result.status == TillStatus.Closed) R.string.pos_till_closed_title else R.string.pos_till_status_variance)) {
        PosText(
            stringResource(R.string.pos_till_counted_expected, formatMoney(result.counted), formatMoney(result.expected)),
            PosTheme.type.bodyPrimary,
            PosTheme.palette.textPrimary,
        )
        PosText(varianceText(result.variance), PosTheme.type.labelAction.copy(fontWeight = FontWeight.SemiBold),
            if (result.variance.minor == 0L) PosTheme.palette.success else PosTheme.palette.error)
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_dismiss), null, true, { dispatch(TillIntent.DismissCloseResult) }, Modifier.widthIn(max = 140.dp))
        }
    }
}

// ---------------------------------------------------------------- dialogs

@Composable
fun TillDialogs(state: PosState, dispatch: (PosIntent) -> Unit) {
    val session = state.till.session ?: return
    val close = { dispatch(TillIntent.ShowDialog(null)) }
    when (state.till.dialog) {
        TillDialog.CashIn -> CashMoveDialog(state, session.currency, CASH_IN_REASONS, cashIn = true, onDismiss = close) { amount, reason, notes ->
            dispatch(TillIntent.CashIn(amount, reason, notes))
        }
        TillDialog.CashOut -> CashMoveDialog(state, session.currency, state.till.reasons[ReasonAction.CASH_OUT], cashIn = false, onDismiss = close) { amount, reason, notes ->
            dispatch(TillIntent.CashOut(amount, reason, notes))
        }
        TillDialog.Handover -> PosModal(stringResource(R.string.pos_till_handover), onDismiss = close) {
            PosText(stringResource(R.string.pos_till_handover_hint), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
            Spacer(Modifier.height(8.dp))
            val operators = state.till.operators?.filter { it.userId != session.operatorUserId }
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                when {
                    operators == null -> EmptyCard(stringResource(R.string.pos_loading))
                    operators.isEmpty() -> EmptyCard(stringResource(R.string.pos_till_no_operators))
                    else -> operators.forEach { op ->
                        ListRow(op.fullName, op.employeeCode, onClick = { dispatch(TillIntent.Handover(op)) })
                    }
                }
            }
        }
        TillDialog.Close -> CloseTillDialog(state, session, onDismiss = close, dispatch = dispatch)
        null -> Unit
    }
}

@Composable
private fun CashMoveDialog(
    state: PosState,
    currency: CurrencyCode,
    reasons: List<ReasonCode>?,
    cashIn: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (Money, ReasonCode, String?) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf("") }
    var reasonCode by rememberSaveable { mutableStateOf<String?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    val amount = tillMoney(amountText, currency)?.takeIf { it.minor > 0 }
    val reason = reasons?.firstOrNull { it.code == reasonCode }
    val ready = amount != null && reason != null && (!reason.requiresNotes || notes.isNotBlank()) && !state.till.busy
    PosModal(stringResource(if (cashIn) R.string.pos_till_cash_in else R.string.pos_till_cash_out), onDismiss = onDismiss, dismissible = !state.till.busy) {
        PosField(stringResource(R.string.pos_till_amount, currency.code), amountText, { amountText = it }, keyboard = KeyboardType.Decimal, placeholder = "0.00")
        Spacer(Modifier.height(10.dp))
        PosText(stringResource(R.string.pos_till_reason), PosTheme.type.labelMeta.copy(fontWeight = FontWeight.SemiBold), PosTheme.palette.textMuted)
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            ReasonPicker(reasons, reason) { reasonCode = it.code }
        }
        Spacer(Modifier.height(8.dp))
        PosField(
            stringResource(if (reason?.requiresNotes == true) R.string.pos_till_notes_required else R.string.pos_notes_optional),
            notes,
            { notes = it },
        )
        if (!cashIn) {
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PosIcon(PosIcons.ShieldCheck, tint = PosTheme.palette.textMuted, size = 16.dp)
                PosText(stringResource(R.string.pos_till_cash_out_manager), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
            }
        }
        state.feedback?.let { PosText(feedbackText(it), PosTheme.type.bodyPrimary, PosTheme.palette.error, modifier = Modifier.padding(top = 10.dp)) }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, !state.till.busy, onDismiss, Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(if (cashIn) R.string.pos_till_record else R.string.pos_till_ask_manager),
                enabled = ready,
                onClick = { if (amount != null && reason != null) onSubmit(amount, reason, notes.ifBlank { null }) },
            )
        }
    }
}

@Composable
private fun CloseTillDialog(state: PosState, session: TillSession, onDismiss: () -> Unit, dispatch: (PosIntent) -> Unit) {
    val denominations = remember(session.currency) { denominationsFor(session.currency) }
    val qty = remember(session.id) { mutableStateMapOf<Long, String>() }
    var reasonCode by rememberSaveable { mutableStateOf<String?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    val counts = denominations.map { d -> DenominationCount(d, qty[d]?.toIntOrNull()?.coerceAtLeast(0) ?: 0) }
    val counted = Money(counts.sumOf { it.amountMinor }, session.currency)
    val needsReason = state.till.needsVarianceReason
    val reasons = state.till.reasons[ReasonAction.TILL_VARIANCE]
    val reason = reasons?.firstOrNull { it.code == reasonCode }
    val ready = !state.till.busy && (!needsReason || (reason != null && (!reason.requiresNotes || notes.isNotBlank())))
    PosModal(stringResource(R.string.pos_till_close), onDismiss = onDismiss, dismissible = !state.till.busy, width = 560.dp) {
        PosText(stringResource(R.string.pos_till_blind_hint), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
        Spacer(Modifier.height(10.dp))
        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            denominations.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { d ->
                        PosField(
                            denominationLabel(d, session.currency),
                            qty[d] ?: "",
                            { v -> qty[d] = v.filter { it.isDigit() }.take(5) },
                            modifier = Modifier.weight(1f),
                            keyboard = KeyboardType.Number,
                            placeholder = "0",
                            enabled = !needsReason,
                        )
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PosText(stringResource(R.string.pos_till_counted, formatMoney(counted)), PosTheme.type.heading3.copy(fontWeight = FontWeight.Bold), PosTheme.palette.textPrimary)
        if (needsReason) {
            Spacer(Modifier.height(10.dp))
            PosText(stringResource(R.string.pos_till_count_out), PosTheme.type.bodyPrimary, PosTheme.palette.error)
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                ReasonPicker(reasons, reason) { reasonCode = it.code }
            }
            PosField(
                stringResource(if (reason?.requiresNotes == true) R.string.pos_till_notes_required else R.string.pos_notes_optional),
                notes,
                { notes = it },
            )
        }
        state.feedback?.let { PosText(feedbackText(it), PosTheme.type.bodyPrimary, PosTheme.palette.error, modifier = Modifier.padding(top = 10.dp)) }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, !state.till.busy, onDismiss, Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(if (state.till.busy) R.string.pos_till_submitting else R.string.pos_till_submit_count),
                enabled = ready,
                onClick = { dispatch(TillIntent.Close(counts, reason, notes.ifBlank { null })) },
            )
        }
    }
}
