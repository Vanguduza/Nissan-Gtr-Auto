package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.design.icons.Smartphone
import co.zw.nissangtr.pos.design.icons.Trash2
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.domain.model.DigitalProvider
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.PaymentStatus
import co.zw.nissangtr.pos.domain.model.ProviderMethod
import co.zw.nissangtr.pos.domain.model.ReceiptContacts
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.TenderOutcome
import co.zw.nissangtr.pos.domain.model.manual
import co.zw.nissangtr.pos.domain.state.CheckoutIntent
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosField
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

/** What the operator chose on the reserved payment screen. */
private enum class Method { Manual, EcoCash, Paynow, ContiPay, Account }

private val Method.provider: DigitalProvider?
    get() = when (this) {
        Method.EcoCash -> DigitalProvider.EcoCash
        Method.Paynow -> DigitalProvider.Paynow
        Method.ContiPay -> DigitalProvider.ContiPay
        else -> null
    }

private class ManualDraft(tender: Tender, amount: String) {
    var tender by mutableStateOf(tender)
    var amount by mutableStateOf(amount)
}

private fun Money.plainAmount(): String = java.math.BigDecimal.valueOf(minor, 2).toPlainString()

/** "14:05" from the server's ISO timestamp, in this device's zone. */
private fun clockTime(iso: String?): String? = iso?.let {
    runCatching {
        java.time.OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalTime()
            .withSecond(0).withNano(0).toString()
    }.getOrNull()
}

internal fun humanState(raw: String): String = raw.replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * Reserve-first payment (Blueprint §10.6–10.7). The sale is reserved before any money is taken, so the
 * amount due is the server's and the cart is locked. Every way to pay is shown: one that cannot be
 * used now says why instead of disappearing. A digital attempt waits here until it settles, fails or
 * goes Unknown; Unknown blocks a second charge and sends the operator to the recovery screen.
 */
@Composable
fun ReservedPaymentDialog(state: PosState, dispatch: (PosIntent) -> Unit) {
    val co = state.checkout
    val palette = PosTheme.palette
    val type = PosTheme.type
    val total = co?.status?.total ?: state.cart.total
    val currency = total.currency
    var method by rememberSaveable { mutableStateOf(Method.Manual) }
    val manual = remember { mutableStateListOf(ManualDraft(Tender.Cash, total.plainAmount())) }
    var cashGiven by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf(state.customer?.phoneE164 ?: state.customer?.whatsappE164 ?: "") }
    var providerMethod by rememberSaveable { mutableStateOf(ProviderMethod.EcoCash) }
    var email by rememberSaveable { mutableStateOf(state.customer?.email.orEmpty()) }
    var whatsapp by rememberSaveable { mutableStateOf(state.customer?.whatsappE164.orEmpty()) }
    val contacts = ReceiptContacts(email.ifBlank { null }, whatsapp.ifBlank { null })

    val outcome = co?.outcome
    val locked = co == null || co.busy || co.inFlight || outcome == TenderOutcome.Unknown || outcome == TenderOutcome.Approved
    val canGoBack = co == null || !(co.busy || co.inFlight || outcome == TenderOutcome.Unknown)

    PosModal(
        title = stringResource(R.string.pos_payment_title),
        onDismiss = { dispatch(PosSaleIntent.ClosePayment) },
        dismissible = canGoBack,
        width = 680.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().clip(PosTheme.shape.sm).background(palette.canvas).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                PosText(stringResource(R.string.pos_amount_due), type.bodyPrimary, palette.textSecondary)
                val held = when {
                    co == null -> stringResource(R.string.pos_co_reserving)
                    else -> clockTime(co.status.reservationExpiresAtIso)?.let { stringResource(R.string.pos_co_held_until, it) }
                }
                held?.let { PosText(it, type.labelMeta, palette.textMuted) }
            }
            PosText(formatMoney(total), type.numericTotal, palette.textPrimary)
        }

        if (co != null) {
            OutcomeBanner(co.outcome, co.message, co.status, onResolve = { dispatch(CheckoutIntent.OpenRecovery(co.orderId)) })
            co.attempt?.takeIf { co.inFlight }?.let { attempt ->
                Spacer(Modifier.height(12.dp))
                ProviderWaiting(
                    providerLabel = providerLabel(attempt.provider),
                    checkoutUrl = attempt.checkoutUrl,
                    message = co.message,
                    onCheck = { dispatch(CheckoutIntent.CheckNow) },
                )
            }
        }

        // While money is moving (or its result is unknown) the only actions are to wait, check or resolve.
        val choosing = co == null || !(co.inFlight || outcome == TenderOutcome.Unknown || outcome == TenderOutcome.Approved)
        var reason: String? = null
        if (choosing) {
        Spacer(Modifier.height(12.dp))
        PosText(stringResource(R.string.pos_co_choose_method), type.labelMeta.copy(fontWeight = FontWeight.SemiBold), palette.textMuted)
        Spacer(Modifier.height(6.dp))
        val offline = stringResource(R.string.pos_co_unavailable_offline)
        val checking = stringResource(R.string.pos_co_checking_provider)
        val needsCustomer = stringResource(R.string.pos_co_account_needs_customer)
        val cards = listOf(
            Triple(Method.Manual, stringResource(R.string.pos_co_counter_tenders), if (!state.online) offline else null),
            Triple(Method.EcoCash, stringResource(R.string.pos_tender_ecocash), providerReason(state, DigitalProvider.EcoCash, offline, checking)),
            Triple(Method.Paynow, stringResource(R.string.pos_tender_paynow), providerReason(state, DigitalProvider.Paynow, offline, checking)),
            Triple(Method.ContiPay, stringResource(R.string.pos_tender_contipay), providerReason(state, DigitalProvider.ContiPay, offline, checking)),
            Triple(Method.Account, stringResource(R.string.pos_tender_account), when {
                !state.online -> offline
                state.customer == null -> needsCustomer
                else -> null
            }),
        )
        TenderCards(cards, selected = method, enabled = !locked, onSelect = { method = it })

        Spacer(Modifier.height(12.dp))
        reason = cards.first { it.first == method }.third
        when (method) {
            Method.Manual -> ManualTenders(manual, total, cashGiven, { cashGiven = it }, enabled = !locked)
            Method.EcoCash, Method.Paynow, Method.ContiPay -> {
                val provider = method.provider!!
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    if (provider != DigitalProvider.Paynow || providerMethod != ProviderMethod.Visa) {
                        PosField(stringResource(R.string.pos_co_provider_phone), phone, { phone = it }, placeholder = "+26377…", keyboard = KeyboardType.Phone, modifier = Modifier.weight(1f))
                    }
                }
                if (provider != DigitalProvider.EcoCash) {
                    Spacer(Modifier.height(8.dp))
                    PosText(stringResource(R.string.pos_co_provider_method), type.labelMeta, palette.textMuted)
                    val options = ProviderMethod.entries.filter { if (provider == DigitalProvider.Paynow) it.paynow else it.contipay }
                    PosSegmented(
                        options = options,
                        selected = providerMethod.takeIf { it in options } ?: options.first(),
                        label = { it.name },
                        onSelect = { providerMethod = it },
                    )
                }
            }
            Method.Account -> {
                val customer = state.customer
                PosText(
                    if (customer != null) stringResource(R.string.pos_co_account_customer, customer.displayName) else needsCustomer,
                    type.bodyPrimary,
                    if (customer != null) palette.textPrimary else palette.textMuted,
                )
                PosText(stringResource(R.string.pos_co_account_hint), type.bodySecondary, palette.textMuted)
            }
        }
        reason?.let {
            PosText(it, type.bodySecondary, palette.error, modifier = Modifier.padding(top = 6.dp))
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosField(stringResource(R.string.pos_receipt_email), email, { email = it }, keyboard = KeyboardType.Email, modifier = Modifier.weight(1f))
            PosField(stringResource(R.string.pos_receipt_whatsapp), whatsapp, { whatsapp = it }, placeholder = "+26377…", keyboard = KeyboardType.Phone, modifier = Modifier.weight(1f))
        }
        }
        state.feedback?.let {
            PosText(feedbackText(it), type.bodyPrimary, palette.error, modifier = Modifier.padding(top = 10.dp))
        }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_back_to_sale), null, enabled = canGoBack, onClick = { dispatch(PosSaleIntent.ClosePayment) }, modifier = Modifier.widthIn(max = 180.dp))
            val busy = co == null || co.busy || state.paying
            val parsed = manual.map { parseMoney(it.amount, currency) }
            val manualReady = parsed.none { it == null || it.minor <= 0 } && parsed.sumOf { it?.minor ?: 0 } == total.minor
            if (choosing) when (method) {
                Method.Manual -> PosPrimaryButton(
                    label = if (busy && co != null) stringResource(R.string.pos_completing) else stringResource(R.string.pos_complete_sale, formatMoney(total)),
                    enabled = !locked && reason == null && manualReady,
                    onClick = {
                        dispatch(
                            CheckoutIntent.PayManual(
                                tenders = manual.zip(parsed).map { (t, m) -> TenderLine(t.tender, m!!) },
                                cashGiven = parseMoney(cashGiven, currency),
                                contacts = contacts,
                            ),
                        )
                    },
                )
                Method.EcoCash, Method.Paynow, Method.ContiPay -> PosPrimaryButton(
                    label = stringResource(R.string.pos_co_send_request),
                    icon = PosIcons.Smartphone,
                    enabled = !locked && reason == null,
                    onClick = {
                        val provider = method.provider!!
                        dispatch(
                            CheckoutIntent.PayProvider(
                                provider = provider,
                                msisdn = phone.ifBlank { null },
                                method = if (provider == DigitalProvider.EcoCash) null else providerMethod,
                                contacts = contacts,
                            ),
                        )
                    },
                )
                Method.Account -> PosPrimaryButton(
                    label = stringResource(R.string.pos_co_charge_account),
                    enabled = !locked && reason == null,
                    onClick = { dispatch(CheckoutIntent.PayOnAccount(contacts)) },
                )
            }
        }
    }
}

private fun providerReason(state: PosState, provider: DigitalProvider, offline: String, checking: String): String? = when {
    !state.online -> offline
    else -> state.providers?.let { it[provider] } ?: if (state.providers == null) checking else null
}

@Composable
private fun providerLabel(p: DigitalProvider): String = stringResource(
    when (p) {
        DigitalProvider.EcoCash -> R.string.pos_tender_ecocash
        DigitalProvider.Paynow -> R.string.pos_tender_paynow
        DigitalProvider.ContiPay -> R.string.pos_tender_contipay
    },
)

/** Every way to pay, as cards; an unusable one stays visible with its reason (§10.7). */
@Composable
private fun TenderCards(cards: List<Triple<Method, String, String?>>, selected: Method, enabled: Boolean, onSelect: (Method) -> Unit) {
    val palette = PosTheme.palette
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val perRow = if (maxWidth < 520.dp) 2 else 5
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cards.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (m, label, reason) ->
                        val active = m == selected
                        Column(
                            Modifier
                                .weight(1f)
                                .height(72.dp)
                                .clip(PosTheme.shape.sm)
                                .background(if (active) palette.canvas else palette.surfacePrimary)
                                .border(if (active) 2.dp else 1.dp, if (active) palette.textPrimary else palette.borderSubtle, PosTheme.shape.sm)
                                .clickable(enabled = enabled, role = Role.RadioButton) { onSelect(m) }
                                .padding(10.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            PosText(label, PosTheme.type.labelAction.copy(fontWeight = FontWeight.SemiBold), if (reason == null) palette.textPrimary else palette.textMuted, maxLines = 1)
                            reason?.let { PosText(it, PosTheme.type.labelMeta, palette.textMuted, maxLines = 2) }
                        }
                    }
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Cash, card/bank and store credit settle together; their sum must equal the total. */
@Composable
private fun ManualTenders(drafts: MutableList<ManualDraft>, total: Money, cashGiven: String, onCashGiven: (String) -> Unit, enabled: Boolean) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    val currency = total.currency
    val parsed = drafts.map { parseMoney(it.amount, currency) }
    val remaining = total.minor - parsed.sumOf { it?.minor ?: 0 }
    val options = Tender.entries.filter { it.manual }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val narrow = maxWidth < 520.dp
    Column {
    drafts.forEachIndexed { i, d ->
        val chooser: @Composable () -> Unit = {
            PosSegmented(
                options = options,
                selected = d.tender,
                label = { stringResource(when (it) { Tender.Bank -> R.string.pos_tender_bank; Tender.StoreCredit -> R.string.pos_tender_store_credit; else -> R.string.pos_tender_cash }) },
                onSelect = { if (enabled) d.tender = it },
            )
        }
        if (narrow) Box(Modifier.padding(top = 6.dp)) { chooser() }
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!narrow) chooser()
            PosField(stringResource(R.string.pos_tender_amount), d.amount, { if (enabled) d.amount = it }, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f))
            if (drafts.size > 1) {
                Box(
                    Modifier.size(48.dp).clip(PosTheme.shape.sm).clickable(enabled = enabled, role = Role.Button) { drafts.removeAt(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    co.zw.nissangtr.pos.ui.common.PosIcon(PosIcons.Trash2, tint = palette.textSecondary, size = 18.dp, contentDescription = stringResource(R.string.pos_remove_tender))
                }
            }
        }
    }
    }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        SoftButton(
            label = stringResource(R.string.pos_split_payment),
            icon = PosIcons.Plus,
            enabled = enabled && remaining > 0,
            onClick = { drafts.add(ManualDraft(Tender.Bank, Money(remaining.coerceAtLeast(0), currency).plainAmount())) },
            modifier = Modifier.widthIn(max = 220.dp),
        )
        Spacer(Modifier.width(12.dp))
        val balanced = remaining == 0L && parsed.none { it == null || it.minor <= 0 }
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
    val cash = drafts.zip(parsed).filter { it.first.tender == Tender.Cash }.sumOf { it.second?.minor ?: 0 }
    if (cash > 0) {
        Spacer(Modifier.height(8.dp))
        val given = parseMoney(cashGiven, currency)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            PosField(stringResource(R.string.pos_cash_given), cashGiven, onCashGiven, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f))
            Column(Modifier.weight(1f)) {
                PosText(stringResource(R.string.pos_change_due), type.labelMeta, palette.textMuted)
                PosText(given?.let { (it.minor - cash).takeIf { c -> c >= 0 } }?.let { formatMoney(Money(it, currency)) } ?: "—", type.numericTotal, palette.textPrimary)
            }
        }
    }
}

/** One of the five outcomes (§10.7), in words the operator can act on. */
@Composable
private fun OutcomeBanner(outcome: TenderOutcome?, message: String?, status: PaymentStatus, onResolve: () -> Unit) {
    outcome ?: return
    val palette = PosTheme.palette
    val (text, color) = when (outcome) {
        TenderOutcome.Approved -> stringResource(R.string.pos_co_outcome_approved) to palette.success
        TenderOutcome.Declined -> (message?.let { stringResource(R.string.pos_co_outcome_declined, it) } ?: stringResource(R.string.pos_co_outcome_declined_plain)) to palette.error
        TenderOutcome.Cancelled -> stringResource(R.string.pos_co_outcome_cancelled) to palette.textSecondary
        TenderOutcome.Error -> stringResource(R.string.pos_co_outcome_error) to palette.error
        TenderOutcome.Unknown -> stringResource(if (status.capturedUnfinished) R.string.pos_co_outcome_captured else R.string.pos_co_outcome_unknown) to palette.error
    }
    Spacer(Modifier.height(12.dp))
    Row(
        Modifier.fillMaxWidth().clip(PosTheme.shape.sm).border(1.dp, color, PosTheme.shape.sm).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PosText(text, PosTheme.type.bodyPrimary, color, modifier = Modifier.weight(1f))
        if (outcome == TenderOutcome.Unknown) {
            PosPrimaryButton(stringResource(R.string.pos_co_resolve), enabled = true, onClick = onResolve)
        }
    }
}

/** The customer approves on their phone; a hosted page is offered as a QR they scan. */
@Composable
private fun ProviderWaiting(providerLabel: String, checkoutUrl: String?, message: String?, onCheck: () -> Unit) {
    val palette = PosTheme.palette
    Row(
        Modifier.fillMaxWidth().clip(PosTheme.shape.sm).background(palette.canvas).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (checkoutUrl != null) {
            val scan = stringResource(R.string.pos_co_scan_to_pay)
            QrCode(checkoutUrl, Modifier.size(140.dp).semantics { contentDescription = scan })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PosText(stringResource(R.string.pos_co_waiting, providerLabel), PosTheme.type.bodyPrimary.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary)
            if (checkoutUrl != null) PosText(stringResource(R.string.pos_co_scan_to_pay), PosTheme.type.bodySecondary, palette.textSecondary)
            message?.takeIf { it != "no_answer" && it != "captured_unfinished" }?.let { PosText(it, PosTheme.type.bodySecondary, palette.textMuted) }
            SoftButton(stringResource(R.string.pos_co_check_now), null, enabled = true, onClick = onCheck, modifier = Modifier.widthIn(max = 180.dp))
        }
    }
}

/** QR drawn from ZXing's matrix: dark modules on white with a quiet zone, readable at arm's length. */
@Composable
internal fun QrCode(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) {
        runCatching {
            com.google.zxing.qrcode.QRCodeWriter().encode(
                content,
                com.google.zxing.BarcodeFormat.QR_CODE,
                0,
                0,
                mapOf(com.google.zxing.EncodeHintType.MARGIN to 2),
            )
        }.getOrNull()
    } ?: return
    Canvas(modifier.background(Color.White)) {
        val cell = size.minDimension / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell, cell))
        }
    }
}

// ---------------------------------------------------------------- Recovery

/**
 * Payment recovery (§10.7): one order's server status with the only safe next steps, or every
 * order that needs someone. Money captured without a sale is finished by an approver, never charged again.
 */
@Composable
fun RecoveryScreen(state: PosState, dispatch: (PosIntent) -> Unit) {
    val orderId = state.recoveryOrderId
    if (orderId == null) {
        PosPanel(stringResource(R.string.pos_recovery_all)) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                SoftButton(stringResource(R.string.pos_recovery_refresh), null, enabled = state.online, onClick = { dispatch(CheckoutIntent.RefreshRecovery) }, modifier = Modifier.widthIn(max = 180.dp))
            }
            val items = state.recoveryItems
            when {
                items == null -> EmptyCard(stringResource(R.string.pos_loading))
                items.isEmpty() -> EmptyCard(stringResource(R.string.pos_recovery_none))
                else -> items.forEach { item ->
                    ListRow(
                        title = humanState(item.state),
                        subtitle = listOfNotNull(
                            item.activeProvider?.let { humanState(it) },
                            item.updatedAtIso.take(16).replace('T', ' '),
                            item.openExceptions.takeIf { it > 0 }?.let { stringResource(R.string.pos_recovery_open_issues, it) },
                        ).joinToString(" · "),
                        trailing = formatMoney(item.total),
                        onClick = { dispatch(CheckoutIntent.OpenRecovery(item.orderId)) },
                    )
                }
            }
        }
        return
    }
    val st = state.recoveryStatus
    PosPanel(stringResource(R.string.pos_recovery_title)) {
        val palette = PosTheme.palette
        if (st == null) {
            EmptyCard(stringResource(R.string.pos_loading))
        } else {
            val facts: List<Pair<String, String>> = listOfNotNull(
                stringResource(R.string.pos_recovery_state) to humanState(st.state),
                stringResource(R.string.pos_recovery_total) to formatMoney(st.total),
                (st.settledProvider ?: st.activeProvider)?.let { stringResource(R.string.pos_recovery_provider) to humanState(it) },
                st.providerStatus?.let { stringResource(R.string.pos_recovery_provider_status) to it },
                st.providerFailure?.let { stringResource(R.string.pos_recovery_failure) to it },
                st.reference?.let { stringResource(R.string.pos_recovery_reference) to it },
                st.salesInvoiceId?.let { stringResource(R.string.pos_recovery_invoice) to it.take(8) },
                clockTime(st.reservationExpiresAtIso)?.let { stringResource(R.string.pos_recovery_expires) to it },
            )
            facts.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    PosText(k, PosTheme.type.bodySecondary, palette.textMuted, modifier = Modifier.width(160.dp))
                    PosText(v, PosTheme.type.bodyPrimary, palette.textPrimary, modifier = Modifier.weight(1f))
                }
            }
            if (st.exceptions.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                PosText(stringResource(R.string.pos_recovery_exceptions), PosTheme.type.labelMeta.copy(fontWeight = FontWeight.SemiBold), palette.textMuted)
                st.exceptions.forEach { e ->
                    ListRow(
                        title = humanState(e.code),
                        subtitle = listOfNotNull(e.detail, e.resolution?.let { "✓ $it" }).joinToString(" · ").ifBlank { null },
                        trailing = e.createdAtIso.take(16).replace('T', ' '),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val guidance = when {
                st.settled -> R.string.pos_recovery_settled
                st.capturedUnfinished -> R.string.pos_co_outcome_captured
                st.inFlight -> R.string.pos_recovery_in_flight
                else -> R.string.pos_recovery_releasable
            }
            PosText(stringResource(guidance), PosTheme.type.bodyPrimary, if (st.capturedUnfinished) palette.error else palette.textSecondary)
        }
        state.feedback?.let { PosText(feedbackText(it), PosTheme.type.bodyPrimary, palette.error, modifier = Modifier.padding(top = 8.dp)) }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_recovery_back), null, enabled = true, onClick = { dispatch(CheckoutIntent.OpenRecovery(null)) }, modifier = Modifier.widthIn(max = 180.dp))
            SoftButton(stringResource(R.string.pos_recovery_refresh), null, enabled = state.online, onClick = { dispatch(CheckoutIntent.RefreshRecovery) }, modifier = Modifier.widthIn(max = 180.dp))
            when {
                st == null || st.settled -> Unit
                st.capturedUnfinished -> PosPrimaryButton(
                    stringResource(R.string.pos_recovery_repair),
                    enabled = state.online && !state.approving,
                    onClick = { dispatch(PosSaleIntent.RequestApproval(co.zw.nissangtr.pos.domain.model.ApprovalRequest.RepairPaidOrder(orderId))) },
                )
                !st.inFlight -> PosPrimaryButton(
                    stringResource(R.string.pos_recovery_release),
                    enabled = state.online,
                    onClick = { dispatch(CheckoutIntent.Release(orderId)) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------- Pickup

/** Paid (or on account) and waiting for the customer to collect (Orders). */
@Composable
fun PickupPanel(state: PosState, dispatch: (PosIntent) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    PosPanel(stringResource(R.string.pos_pickups)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            PosField(stringResource(R.string.pos_pickup_search), query, { query = it }, modifier = Modifier.weight(1f))
            SoftButton(stringResource(R.string.pos_search), PosIcons.Search, enabled = true, onClick = { dispatch(CheckoutIntent.LoadPickups(query)) }, modifier = Modifier.widthIn(max = 140.dp))
        }
        val pickups = state.pickups
        when {
            pickups == null -> EmptyCard(stringResource(R.string.pos_loading))
            pickups.isEmpty() -> EmptyCard(stringResource(R.string.pos_pickups_none))
            else -> pickups.forEach { p ->
                ListRow(
                    title = p.documentNumber ?: p.orderId.take(8),
                    subtitle = listOfNotNull(p.customerName, humanState(p.state), p.settledProvider?.let { humanState(it) }).joinToString(" · "),
                    trailing = formatMoney(p.total),
                ) {
                    SoftButton(stringResource(R.string.pos_collect), null, enabled = state.online, onClick = { dispatch(CheckoutIntent.Collect(p.orderId, newSale = false)) }, modifier = Modifier.widthIn(max = 140.dp))
                }
            }
        }
        SoftButton(stringResource(R.string.pos_payments_to_resolve), null, enabled = true, onClick = { dispatch(CheckoutIntent.OpenRecovery(null)) }, modifier = Modifier.widthIn(max = 240.dp))
    }
}
