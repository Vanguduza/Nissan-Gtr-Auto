package co.zw.nissangtr.delivery.pod

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhonelinkSetup
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.delivery.design.Slopes
import co.zw.nissangtr.delivery.design.SlopesBanner
import co.zw.nissangtr.delivery.design.SlopesCheckRow
import co.zw.nissangtr.delivery.design.SlopesGroup
import co.zw.nissangtr.delivery.design.SlopesIconBadge
import co.zw.nissangtr.delivery.design.SlopesPill
import co.zw.nissangtr.delivery.design.SlopesPrimaryButton
import co.zw.nissangtr.delivery.design.SlopesRow
import co.zw.nissangtr.delivery.design.SlopesSectionHeader
import co.zw.nissangtr.delivery.design.SlopesSegmented
import co.zw.nissangtr.delivery.design.SlopesTextField
import co.zw.nissangtr.delivery.design.SlopesTintedButton
import co.zw.nissangtr.delivery.design.SlopesTone

/**
 * Payment on delivery, above the proof steps. Shows nothing for prepaid stops. The amount due is
 * always the server's; cash and card each record against the delivery invoice.
 */
@Composable
fun DeliveryPaymentContent(
    state: DeliveryPaymentUiState,
    onRetry: () -> Unit,
    onMethod: (CodMethod) -> Unit,
    onAmountChange: (String) -> Unit,
    onCashNotesChange: (String) -> Unit,
    onCollectCash: () -> Unit,
    onSelectTerminal: (String) -> Unit,
    onReloadTerminals: () -> Unit,
    onPair: () -> Unit,
    onCharge: () -> Unit,
    onAskAgain: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenOnAccount: (Boolean) -> Unit = {},
    onOnAccountReasonChange: (String) -> Unit = {},
    onRequestOnAccount: () -> Unit = {},
    onCheckDecision: () -> Unit = {},
) {
    val c = Slopes.colors
    val ctx = state.context
    if (ctx == null) {
        // Unknown is not "nothing due": say so, but don't block a proof that may need to queue offline.
        state.contextError?.let {
            Column(modifier.fillMaxWidth()) {
                SlopesSectionHeader("Payment")
                SlopesBanner(
                    "Could not check whether payment is due. Collect per the delivery note. ($it)",
                    tone = SlopesTone.Warning,
                    icon = Icons.Filled.ReportProblem,
                    action = "Retry",
                    onAction = onRetry,
                )
            }
        }
        return
    }
    if (!CodGate.collects(ctx) && ctx.amountDue <= 0.004) return

    Column(modifier.fillMaxWidth()) {
        SlopesSectionHeader("Payment", action = if (state.busy) null else "Refresh", onAction = onRetry)
        SlopesGroup {
            SlopesRow(
                title = if (ctx.amountDue > 0.004) "${CodGate.money(ctx.amountDue, ctx.currency)} to collect" else "Paid in full",
                subtitle = buildString {
                    append(methodLabel(ctx.method))
                    ctx.documentNumber?.let { append(" · ").append(it) }
                    if (ctx.amountPaid > 0) append(" · paid ").append(CodGate.money(ctx.amountPaid, ctx.currency)).append(" of ").append(CodGate.money(ctx.invoiceTotal, ctx.currency))
                },
                leading = {
                    SlopesIconBadge(
                        if (ctx.amountDue > 0.004) Icons.Filled.Payments else Icons.Filled.CheckCircle,
                        tint = if (ctx.amountDue > 0.004) c.accent else c.success,
                        container = if (ctx.amountDue > 0.004) c.fill else c.success.copy(alpha = 0.14f),
                    )
                },
                chevron = false,
                divider = false,
            )
        }

        if (!CodGate.collects(ctx)) {
            Spacer(Modifier.height(10.dp))
            SlopesBanner(
                "This delivery is not set up for payment at the door. Don't take money — dispatch will follow up.",
                tone = SlopesTone.Info,
            )
            return@Column
        }

        val attempt = state.attempt
        if (CodGate.isUnresolved(attempt)) {
            CardRecovery(state, attempt!!, onAskAgain, onFinish)
        } else if (ctx.amountDue > 0.004) {
            if (state.balanceOnAccount) {
                val a = state.approval!!
                Spacer(Modifier.height(12.dp))
                SlopesBanner(
                    "${CodGate.money(ctx.amountDue, ctx.currency)} stays on the customer's account" +
                        (if (a.basis == "credit_limit") " (within their credit limit)." else " (approved by ${a.decidedByName ?: "dispatch"}).") +
                        " Finish the proof to complete; take more payment only if they offer it.",
                    tone = SlopesTone.Success,
                    icon = Icons.Filled.CheckCircle,
                )
            }
            if (ctx.mayCollectCash && ctx.mayCollectCard) {
                Spacer(Modifier.height(12.dp))
                SlopesSegmented(
                    options = listOf("Cash", "Card"),
                    selected = if (state.method == CodMethod.Card) 1 else 0,
                    onSelect = { onMethod(if (it == 1) CodMethod.Card else CodMethod.Cash) },
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            SlopesTextField(
                value = state.amount,
                onValueChange = onAmountChange,
                label = "Amount (${ctx.currency.uppercase()})",
                placeholder = "%.2f".format(java.util.Locale.US, ctx.amountDue),
                enabled = !state.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            val amount = CodGate.parseAmount(state.amount, ctx.amountDue)
            if (state.amount.isNotBlank() && amount == null) {
                Text(
                    "Up to ${CodGate.money(ctx.amountDue, ctx.currency)} — part payments are fine.",
                    color = c.danger,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            if (state.method == CodMethod.Cash) {
                SlopesTextField(
                    value = state.cashNotes,
                    onValueChange = onCashNotesChange,
                    label = "Note (optional)",
                    placeholder = "e.g. paid by the site manager",
                    enabled = !state.busy,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
                SlopesPrimaryButton(
                    label = state.busyLabel ?: amount?.let { "Cash received · ${CodGate.money(it, ctx.currency)}" } ?: "Enter the cash received",
                    onClick = onCollectCash,
                    enabled = !state.busy && amount != null,
                    icon = Icons.Filled.Payments,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                CardSetup(state, amount, ctx.currency, onSelectTerminal, onReloadTerminals, onPair, onCharge)
            }
            Spacer(Modifier.height(14.dp))
            BalanceOnAccount(state, onOpenOnAccount, onOnAccountReasonChange, onRequestOnAccount, onCheckDecision)
        }

        state.message?.let {
            Spacer(Modifier.height(12.dp))
            SlopesBanner(it, tone = SlopesTone.Success, icon = Icons.Filled.CheckCircle)
        }
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            SlopesBanner(it, tone = SlopesTone.Danger, icon = Icons.Filled.ReportProblem)
        }
    }
}

@Composable
private fun CardSetup(
    state: DeliveryPaymentUiState,
    amount: Double?,
    currency: String,
    onSelectTerminal: (String) -> Unit,
    onReloadTerminals: () -> Unit,
    onPair: () -> Unit,
    onCharge: () -> Unit,
) {
    val c = Slopes.colors
    if (state.terminalsLoaded && state.terminals.isEmpty()) {
        SlopesBanner(
            "No card machine is assigned to this phone. Ask an admin to assign one to device ${state.deviceId}.",
            tone = SlopesTone.Warning,
            icon = Icons.Filled.PhonelinkSetup,
            action = "Check again",
            onAction = onReloadTerminals,
        )
        return
    }
    if (state.terminals.size > 1) {
        SlopesGroup {
            state.terminals.forEachIndexed { i, t ->
                SlopesCheckRow(
                    title = t.label,
                    subtitle = t.acquirer,
                    selected = t.id == state.selectedTerminalId,
                    onClick = { onSelectTerminal(t.id) },
                    divider = i < state.terminals.lastIndex,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    val t = state.selectedTerminal
    // Why the charge button is disabled, in the driver's words.
    val reason = when {
        t == null -> if (state.terminalsLoaded) "Choose a card machine" else "Finding card machines…"
        !state.appInstalled -> "The card machine app for ${t.label} is not on this phone"
        !state.paired -> "Pair this phone with ${t.label} first"
        amount == null -> "Enter the amount to charge"
        else -> null
    }
    if (t != null) {
        SlopesGroup {
            SlopesRow(
                title = t.label,
                subtitle = if (state.paired) t.acquirer else "Phone ID ${state.deviceId}",
                leading = { SlopesIconBadge(Icons.Filled.CreditCard) },
                trailing = { SlopesPill(if (state.paired) "Paired" else "Not paired", if (state.paired) c.success else c.warning) },
                chevron = false,
                divider = false,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (!state.paired) {
            SlopesTintedButton(
                "Pair this phone",
                onPair,
                enabled = !state.busy,
                icon = Icons.Filled.Link,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
        }
    }
    SlopesPrimaryButton(
        label = state.busyLabel ?: reason ?: "Charge card · ${CodGate.money(amount!!, currency)}",
        onClick = onCharge,
        enabled = !state.busy && reason == null,
        icon = Icons.Filled.CreditCard,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

/** A charge that may have taken money: ask the machine or post it — never charge again. */
@Composable
private fun CardRecovery(state: DeliveryPaymentUiState, attempt: co.zw.nissangtr.delivery.rpc.DeliveryCardAttempt, onAskAgain: () -> Unit, onFinish: () -> Unit) {
    val money = CodGate.money(attempt.amount, attempt.currency)
    val approved = attempt.status == "approved"
    Spacer(Modifier.height(12.dp))
    SlopesBanner(
        if (approved) {
            "The card machine approved $money${attempt.cardLast4?.let { " (•••• $it)" } ?: ""} but it is not posted to the invoice yet. Post it before completing."
        } else {
            "We don't know whether the card machine took $money. Ask the machine again — don't charge the card again."
        },
        tone = SlopesTone.Warning,
        icon = Icons.Filled.ReportProblem,
    )
    Spacer(Modifier.height(12.dp))
    Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (approved) {
            SlopesPrimaryButton(
                state.busyLabel ?: "Post card payment",
                onFinish,
                enabled = !state.busy,
                icon = Icons.Filled.CheckCircle,
                modifier = Modifier.weight(1f),
            )
        } else {
            SlopesPrimaryButton(
                state.busyLabel ?: "Ask the card machine",
                onAskAgain,
                enabled = !state.busy,
                icon = Icons.Filled.Sync,
                modifier = Modifier.weight(1f),
            )
        }
    }
    attempt.terminalLabel?.let {
        Text(
            "Machine: $it · ref ${attempt.externalRef ?: attempt.attemptId.take(8)}",
            color = Slopes.colors.secondaryLabel,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
    }
}

private fun methodLabel(method: String): String = when (method) {
    "cash_on_delivery" -> "Cash on delivery"
    "card_on_delivery" -> "Card on delivery"
    "cash_or_card_on_delivery" -> "Cash or card on delivery"
    "prepay" -> "Prepaid / on account"
    else -> method.replace('_', ' ')
}

/**
 * The customer cannot pay the rest: leave it on their account. Approved at once on a trade account
 * with room under its limit; otherwise dispatch decides while the driver waits.
 */
@Composable
private fun BalanceOnAccount(
    state: DeliveryPaymentUiState,
    onOpen: (Boolean) -> Unit,
    onReason: (String) -> Unit,
    onRequest: () -> Unit,
    onCheck: () -> Unit,
) {
    val c = Slopes.colors
    val ctx = state.context ?: return
    val a = state.approval
    when {
        state.balanceOnAccount -> Unit
        a?.status == "pending" -> {
            SlopesBanner(
                "Waiting for dispatch to approve leaving ${CodGate.money(a.amount, a.currency)} on account. This updates by itself.",
                tone = SlopesTone.Warning,
                icon = Icons.Filled.HourglassTop,
                action = if (state.busy) null else "Check now",
                onAction = onCheck,
            )
        }
        state.onAccountOpen -> {
            Text(
                "Leave ${CodGate.money(ctx.amountDue, ctx.currency)} on the customer's account",
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                color = c.label,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(8.dp))
            SlopesTextField(
                value = state.onAccountReason,
                onValueChange = onReason,
                label = "Why can't they pay the rest?",
                placeholder = "e.g. paid what they had, will settle at the branch",
                singleLine = false,
                enabled = !state.busy,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SlopesTintedButton("Cancel", { onOpen(false) }, enabled = !state.busy, modifier = Modifier.weight(1f))
                SlopesPrimaryButton(
                    state.busyLabel ?: "Ask to leave on account",
                    onRequest,
                    enabled = !state.busy && state.onAccountReason.isNotBlank(),
                    modifier = Modifier.weight(1.4f),
                )
            }
            Text(
                "Approved straight away if their trade account has room; otherwise dispatch decides.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
        else -> {
            if (a?.status == "refused") {
                SlopesBanner("Dispatch refused leaving the balance on account: ${a.decisionNote ?: "no reason given"}.", tone = SlopesTone.Danger, icon = Icons.Filled.ReportProblem)
                Spacer(Modifier.height(10.dp))
            }
            SlopesTintedButton(
                "Customer can't pay the rest?",
                { onOpen(true) },
                enabled = !state.busy,
                icon = Icons.Filled.AccountBalanceWallet,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Text(
                "Leave the balance on their account, or report an issue (Refused) and bring the parts back.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = c.secondaryLabel,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
    }
}
