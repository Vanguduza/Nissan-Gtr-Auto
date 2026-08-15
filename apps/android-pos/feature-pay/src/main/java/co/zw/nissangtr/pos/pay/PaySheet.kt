package co.zw.nissangtr.pos.pay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.MoneyCents
import co.zw.nissangtr.pos.api.TenderMode
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes
import co.zw.nissangtr.ui.theme.GtrTheme

data class PaySheetUiState(
    val dueCents: Long,
    val currency: String,
    val online: Boolean,
    val drafts: List<TenderDraft>,
    val receiptWhatsapp: String = "",
    val receiptEmail: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val snapshot: AllocationSnapshot
        get() = TenderAllocator.allocate(dueCents, drafts)

    val confirmEnabled: Boolean
        get() = !busy && snapshot.confirmEnabled && drafts.isNotEmpty()
}

@Composable
fun PaySheet(
    state: PaySheetUiState,
    onAddMode: (TenderMode) -> Unit,
    onFillRest: (TenderMode) -> Unit,
    onSplitEqually: (List<TenderMode>) -> Unit,
    onTenderedChange: (index: Int, tenderedCents: Long) -> Unit,
    onReceiptWhatsapp: (String) -> Unit,
    onReceiptEmail: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snap = state.snapshot
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GtrColors.SteelLift, GtrShapes.medium)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Pay",
            style = MaterialTheme.typography.titleLarge,
            color = GtrColors.Chalk,
            fontWeight = FontWeight.Bold,
        )
        MoneyRow("Due", state.dueCents, state.currency, emphasize = true)
        MoneyRow("Remaining", snap.remainingCents, state.currency)
        MoneyRow("Change", snap.changeCents, state.currency)

        Text(
            text = "Modes",
            style = MaterialTheme.typography.labelLarge,
            color = GtrColors.Silver,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TenderMode.entries.forEach { mode ->
                val enabled = mode.enabledWhen(state.online)
                ModeChip(
                    label = mode.rpcValue,
                    selected = state.drafts.any { it.mode == mode },
                    enabled = enabled,
                    needsConnection = !enabled,
                    onClick = { if (enabled) onAddMode(mode) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                val mode = state.drafts.lastOrNull()?.mode ?: TenderMode.CASH
                if (mode.enabledWhen(state.online)) onFillRest(mode)
            }) {
                Text("Fill rest", color = GtrColors.Chalk)
            }
            TextButton(onClick = {
                val modes = state.drafts.map { it.mode }.ifEmpty {
                    listOf(TenderMode.CASH, TenderMode.ECOCASH)
                }.filter { it.enabledWhen(state.online) }
                if (modes.isNotEmpty()) onSplitEqually(modes)
            }) {
                Text("Split equally", color = GtrColors.Chalk)
            }
        }

        state.drafts.forEachIndexed { index, draft ->
            AllocatedRow(
                draft = draft,
                appliedCents = snap.lines.getOrNull(index)?.appliedCents ?: 0L,
                currency = state.currency,
                onTenderedChange = { onTenderedChange(index, it) },
            )
        }

        OutlinedTextField(
            value = state.receiptWhatsapp,
            onValueChange = onReceiptWhatsapp,
            label = { Text("Receipt WhatsApp (E.164)") },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )
        OutlinedTextField(
            value = state.receiptEmail,
            onValueChange = onReceiptEmail,
            label = { Text("Receipt email") },
            modifier = Modifier.fillMaxWidth(),
            colors = fieldColors(),
        )

        state.error?.let {
            Text(text = it, color = GtrColors.Warning, style = MaterialTheme.typography.bodySmall)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                Text("Cancel", color = GtrColors.Silver)
            }
            Button(
                onClick = onConfirm,
                enabled = state.confirmEnabled,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
                shape = GtrShapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = GtrColors.Primary,
                    contentColor = GtrColors.PrimaryInk,
                    disabledContainerColor = GtrColors.SilverDim,
                ),
            ) {
                Text("Confirm", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MoneyRow(
    label: String,
    cents: Long,
    currency: String,
    emphasize: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = GtrColors.Silver, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "$currency ${MoneyCents.centsToMajorString(cents)}",
            color = if (emphasize) GtrColors.Usd else GtrColors.Chalk,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (emphasize) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    needsConnection: Boolean,
    onClick: () -> Unit,
) {
    val border = when {
        selected -> GtrColors.Primary
        else -> GtrColors.SilverDim
    }
    Column(
        modifier = Modifier
            .border(1.dp, border, GtrShapes.small)
            .background(
                if (selected) GtrColors.Steel else GtrColors.SteelLift,
                GtrShapes.small,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = if (enabled) GtrColors.Chalk else GtrColors.SilverDim,
            style = MaterialTheme.typography.labelMedium,
        )
        if (needsConnection) {
            Text(
                text = "needs connection",
                color = GtrColors.Warning,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun AllocatedRow(
    draft: TenderDraft,
    appliedCents: Long,
    currency: String,
    onTenderedChange: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GtrColors.Steel, GtrShapes.small)
            .padding(10.dp),
    ) {
        Text(
            text = draft.mode.rpcValue,
            color = GtrColors.Chalk,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Applied $currency ${MoneyCents.centsToMajorString(appliedCents)}" +
                if (draft.mode == TenderMode.CASH) {
                    " · tendered ${MoneyCents.centsToMajorString(draft.tenderedCents)}"
                } else {
                    ""
                },
            color = GtrColors.Silver,
            style = MaterialTheme.typography.bodySmall,
        )
        if (draft.mode == TenderMode.CASH) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onTenderedChange(appliedCents) }) {
                    Text("Exact", color = GtrColors.Chalk)
                }
                TextButton(onClick = { onTenderedChange(draft.tenderedCents + 100) }) {
                    Text("+1.00", color = GtrColors.Chalk)
                }
            }
        }
        if (draft.mode.isLiveRail && !draft.railSettled) {
            Text("Rail pending settle", color = GtrColors.Warning, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = GtrColors.Chalk,
    unfocusedTextColor = GtrColors.Chalk,
    focusedBorderColor = GtrColors.Primary,
    unfocusedBorderColor = GtrColors.SilverDim,
    focusedLabelColor = GtrColors.Silver,
    unfocusedLabelColor = GtrColors.SilverDim,
    cursorColor = GtrColors.Primary,
)

@Preview(
    name = "PaySheet_SplitCashEcoCash",
    widthDp = 420,
    heightDp = 720,
    showBackground = true,
    backgroundColor = 0xFF12151C,
)
@Composable
fun PaySheet_SplitCashEcoCash() {
    val due = 21200L
    val drafts = TenderAllocator.splitEqually(
        dueCents = due,
        modes = listOf(TenderMode.CASH, TenderMode.ECOCASH),
    ).mapIndexed { i, d ->
        if (i == 0) d.copy(tenderedCents = d.tenderedCents + 500, railSettled = true)
        else d.copy(railSettled = true)
    }
    GtrTheme(darkTheme = true) {
        PaySheet(
            state = PaySheetUiState(
                dueCents = due,
                currency = "USD",
                online = true,
                drafts = drafts,
                receiptWhatsapp = "+263771234567",
                receiptEmail = "buyer@example.com",
            ),
            onAddMode = {},
            onFillRest = {},
            onSplitEqually = {},
            onTenderedChange = { _, _ -> },
            onReceiptWhatsapp = {},
            onReceiptEmail = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
