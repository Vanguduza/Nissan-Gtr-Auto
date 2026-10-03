package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.ManagerCredentials
import co.zw.nissangtr.pos.domain.model.TerminalAttempt
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.TerminalIntent
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosField
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.PosRowEnd
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.formatMoney
import co.zw.nissangtr.pos.ui.home.SoftButton

/** Why the card machine cannot be used now, in the operator's words. */
@Composable
internal fun terminalReasonText(code: String?): String? = code?.let {
    stringResource(
        when (it) {
            "online_only" -> R.string.pos_co_unavailable_offline
            "terminal_not_set" -> R.string.pos_ct_not_set
            "terminal_app_missing" -> R.string.pos_ct_app_missing
            "terminal_not_paired" -> R.string.pos_ct_not_paired
            else -> R.string.pos_co_checking_provider
        },
    )
}

/** While the customer uses the card machine: the sale waits here; nothing else can be charged. */
@Composable
internal fun TerminalWaiting(attempt: TerminalAttempt?) {
    val palette = PosTheme.palette
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().clip(PosTheme.shape.sm).background(palette.canvas).padding(14.dp)) {
        PosText(
            stringResource(R.string.pos_ct_follow_machine, attempt?.terminalLabel ?: stringResource(R.string.pos_ct_machine)),
            PosTheme.type.bodyPrimary.copy(fontWeight = FontWeight.SemiBold),
            palette.textPrimary,
        )
        PosText(stringResource(R.string.pos_ct_follow_hint), PosTheme.type.bodySecondary, palette.textSecondary)
    }
}

/** After a lost answer: ask the machine again (current sale), before anything else. */
@Composable
internal fun TerminalCheckButton(state: PosState, attempt: TerminalAttempt, dispatch: (PosIntent) -> Unit) {
    SoftButton(
        stringResource(if (state.terminalBusy) R.string.pos_loading else R.string.pos_ct_check_machine),
        null,
        enabled = !state.terminalBusy && state.online,
        onClick = { dispatch(TerminalIntent.CheckOnMachine(attempt)) },
        modifier = Modifier.widthIn(max = 240.dp),
    )
}

/** Settings → Card machine: choose this tablet's machine and pair the tablet with it (admin). */
@Composable
internal fun CardMachineSettingsRow(state: PosState, dispatch: (PosIntent) -> Unit) {
    val palette = PosTheme.palette
    val setup = state.terminalSetup
    var pairing by rememberSaveable { mutableStateOf(false) }
    var adminId by rememberSaveable { mutableStateOf("") }
    var adminPassword by rememberSaveable { mutableStateOf("") }
    val status = when {
        setup == null -> stringResource(R.string.pos_loading)
        setup.terminals.isEmpty() -> stringResource(R.string.pos_ct_none_set_up)
        setup.selected == null -> stringResource(R.string.pos_ct_not_set)
        !setup.appInstalled -> stringResource(R.string.pos_ct_app_missing)
        !setup.paired -> stringResource(R.string.pos_ct_not_paired)
        else -> stringResource(R.string.pos_ct_ready, setup.selected?.label.orEmpty())
    }
    ListRow(stringResource(R.string.pos_ct_title), status) {
        SoftButton(stringResource(R.string.pos_ct_refresh), null, enabled = state.online, onClick = { dispatch(TerminalIntent.LoadSetup) }, modifier = Modifier.widthIn(max = 140.dp))
    }
    setup?.terminals?.forEach { t ->
        ListRow(
            title = t.label,
            subtitle = listOfNotNull(t.acquirer, t.config["package_name"]).joinToString(" · "),
            selected = setup.selected?.id == t.id,
            onClick = { dispatch(TerminalIntent.Select(t.id)) },
        )
    }
    val selected = setup?.selected ?: return
    if (!pairing) {
        PosRowEnd {
            SoftButton(
                stringResource(if (setup.paired) R.string.pos_ct_pair_again else R.string.pos_ct_pair),
                null,
                enabled = state.online && !state.terminalPairing,
                onClick = { pairing = true },
                modifier = Modifier.widthIn(max = 240.dp),
            )
        }
        return
    }
    Column(Modifier.fillMaxWidth().clip(PosTheme.shape.sm).background(palette.canvas).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PosText(stringResource(R.string.pos_ct_pair_hint, selected.label), PosTheme.type.bodySecondary, palette.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            PosField(stringResource(R.string.pos_ct_admin_id), adminId, { adminId = it }, modifier = Modifier.weight(1f))
            PosField(stringResource(R.string.pos_ct_admin_password), adminPassword, { adminPassword = it }, keyboard = KeyboardType.Password, modifier = Modifier.weight(1f))
        }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_split_back), null, enabled = true, onClick = { pairing = false }, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(if (state.terminalPairing) R.string.pos_loading else R.string.pos_ct_pair),
                enabled = !state.terminalPairing,
                onClick = {
                    // Blank admin fields: the signed-in user must be an admin themselves.
                    val admin = if (adminId.isNotBlank() && adminPassword.isNotEmpty()) ManagerCredentials(adminId.trim(), adminPassword, null) else null
                    dispatch(TerminalIntent.Pair(admin))
                    adminPassword = ""
                    pairing = false
                },
            )
        }
    }
}

@Composable
private fun attemptStatus(raw: String): String = stringResource(
    when (raw) {
        "approved", "recovery_required" -> R.string.pos_ct_status_charged_unposted
        "unknown", "initiated" -> R.string.pos_ct_status_unknown
        "settled" -> R.string.pos_ct_status_settled
        "reversed" -> R.string.pos_ct_status_reversed
        "declined" -> R.string.pos_co_outcome_declined_plain
        else -> R.string.pos_ct_status_other
    },
)

/**
 * Recovery for card-machine payments (§10.7, §10.11): ask the machine again, finish the sale against
 * an approved charge, or give the money back on the machine — never charge again.
 */
@Composable
internal fun TerminalRecoverySection(state: PosState, orderId: String?, dispatch: (PosIntent) -> Unit) {
    val items = state.terminalRecovery.orEmpty().filter { orderId == null || it.orderId == orderId }
    if (items.isEmpty()) return
    val palette = PosTheme.palette
    Spacer(Modifier.height(10.dp))
    PosText(stringResource(R.string.pos_ct_recovery_heading), PosTheme.type.labelMeta.copy(fontWeight = FontWeight.SemiBold), palette.textMuted)
    items.forEach { item ->
        val charged = item.operation == "purchase" && (item.status == "approved" || item.status == "recovery_required")
        val unknown = item.status == "unknown" || item.status == "initiated"
        ListRow(
            title = "${item.terminalLabel ?: stringResource(R.string.pos_ct_machine)} · ${attemptStatus(item.status)}",
            subtitle = listOfNotNull(
                humanState(item.operation),
                item.cardLast4?.let { "•••• $it" },
                item.transactionId,
                item.message,
                item.updatedAtIso.take(16).replace('T', ' '),
            ).joinToString(" · "),
            trailing = formatMoney(item.amount),
        )
        val attempt = TerminalAttempt(
            item.attemptId, item.operation, item.status, item.amount, item.terminalLabel, item.cardLast4, null,
            item.transactionId, item.message, item.orderId, null, null, null,
        )
        if (charged || unknown) {
            PosRowEnd {
                if (unknown) TerminalCheckButton(state, attempt, dispatch)
                if (charged) {
                    SoftButton(stringResource(R.string.pos_ct_reverse), null, enabled = !state.terminalBusy && state.online, onClick = { dispatch(TerminalIntent.Reverse(item.attemptId)) }, modifier = Modifier.widthIn(max = 260.dp))
                    PosPrimaryButton(stringResource(R.string.pos_ct_finish), enabled = !state.terminalBusy && state.online, onClick = { dispatch(TerminalIntent.Finish(item.attemptId)) })
                }
            }
        }
    }
}
