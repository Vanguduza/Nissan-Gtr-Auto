package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import co.zw.nissangtr.pos.design.icons.ChevronLeft
import co.zw.nissangtr.pos.design.icons.FileText
import co.zw.nissangtr.pos.design.icons.Pause
import co.zw.nissangtr.pos.design.icons.Pin
import co.zw.nissangtr.pos.design.icons.Trash2
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.layout.PosAdaptiveMath
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.Customer
import co.zw.nissangtr.pos.domain.model.CustomerDraft
import co.zw.nissangtr.pos.domain.model.CustomerKind
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.model.QuoteChannel
import co.zw.nissangtr.pos.domain.model.Quotation
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosField
import co.zw.nissangtr.pos.ui.common.PosIcon
import co.zw.nissangtr.pos.ui.common.PosModal
import co.zw.nissangtr.pos.ui.common.PosPanel
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.PosRowEnd
import co.zw.nissangtr.pos.ui.common.PosSegmented
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.formatMoney
import co.zw.nissangtr.pos.ui.common.formatQty
import co.zw.nissangtr.pos.ui.home.CardAction
import co.zw.nissangtr.pos.ui.home.EmptyCard
import co.zw.nissangtr.pos.ui.home.PartCard
import co.zw.nissangtr.pos.ui.home.PosHostActions
import co.zw.nissangtr.pos.ui.home.SoftButton
import java.time.LocalDate

@Composable
private fun ListRow(
    title: String,
    subtitle: String?,
    trailing: String? = null,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    val palette = PosTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(PosTheme.shape.sm)
            .background(if (selected) palette.canvas else palette.surfacePrimary)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            PosText(title, PosTheme.type.labelAction.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary, maxLines = 1)
            subtitle?.let { PosText(it, PosTheme.type.bodySecondary, palette.textMuted, maxLines = 2) }
        }
        trailing?.let { PosText(it, PosTheme.type.numericPrice, palette.textPrimary, maxLines = 1) }
        actions()
    }
}

/** Two panels side by side when there is room, stacked otherwise (adaptive at every width). */
@Composable
private fun TwoPane(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 760.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) { first() }
                Box(Modifier.weight(1f)) { second() }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                first()
                second()
            }
        }
    }
}

// ---------------------------------------------------------------- Quick Sale

@Composable
fun QuickSaleScreen(state: PosState, dispatch: (PosIntent) -> Unit) {
    var discount by rememberSaveable { mutableStateOf("") }
    var overrideLine by rememberSaveable { mutableStateOf<String?>(null) }
    var overridePrice by rememberSaveable { mutableStateOf("") }
    var quoteOpen by rememberSaveable { mutableStateOf(false) }
    val empty = state.cart.isEmpty
    TwoPane(
        first = {
            PosPanel(stringResource(R.string.pos_quick_sale_adjust)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                    PosField(stringResource(R.string.pos_discount_percent), discount, { discount = it }, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                    SoftButton(
                        stringResource(R.string.pos_apply_discount),
                        null,
                        enabled = !empty && (discount.toDoubleOrNull() ?: 0.0) > 0,
                        onClick = { discount.toDoubleOrNull()?.let { dispatch(PosSaleIntent.RequestApproval(ApprovalRequest.Discount(it))) } },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(16.dp))
                PosText(stringResource(R.string.pos_price_override), PosTheme.type.labelAction.copy(fontWeight = FontWeight.SemiBold), PosTheme.palette.textPrimary)
                state.cart.lines.filterNot { it.isCoreCharge }.forEach { line ->
                    ListRow(
                        title = line.name,
                        subtitle = "${line.oemPartNumber} · ${formatQty(line.qty)} × ${formatMoney(line.unitPrice)}",
                        selected = overrideLine == line.lineId,
                        onClick = { overrideLine = line.lineId },
                    )
                }
                if (empty) EmptyCard(stringResource(R.string.pos_cart_empty))
                overrideLine?.let { lineId ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                        PosField(stringResource(R.string.pos_new_unit_price), overridePrice, { overridePrice = it }, keyboard = KeyboardType.Decimal, modifier = Modifier.weight(1f))
                        SoftButton(
                            stringResource(R.string.pos_apply_price),
                            null,
                            enabled = overridePrice.toDoubleOrNull() != null,
                            onClick = { overridePrice.toDoubleOrNull()?.let { dispatch(PosSaleIntent.RequestApproval(ApprovalRequest.PriceOverride(lineId, it))) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        second = {
            PosPanel(stringResource(R.string.pos_quick_sale_actions)) {
                SoftButton(stringResource(R.string.pos_park_sale), PosIcons.Pause, enabled = !empty, onClick = { dispatch(PosSaleIntent.Park) })
                Spacer(Modifier.height(10.dp))
                SoftButton(stringResource(R.string.pos_create_quote), PosIcons.FileText, enabled = !empty, onClick = { quoteOpen = true })
                Spacer(Modifier.height(10.dp))
                SoftButton(stringResource(R.string.pos_void_sale), PosIcons.Trash2, enabled = !empty, onClick = { dispatch(PosSaleIntent.RequestApproval(ApprovalRequest.VoidSale)) })
                Spacer(Modifier.height(10.dp))
                PosText(stringResource(R.string.pos_manager_actions_hint), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
            }
        },
    )
    if (quoteOpen) QuoteDialog(onDismiss = { quoteOpen = false }, onCreate = { until, notes ->
        quoteOpen = false
        dispatch(PosSaleIntent.CreateQuotation(until, notes))
    })
}

@Composable
private fun QuoteDialog(onDismiss: () -> Unit, onCreate: (String?, String?) -> Unit) {
    var until by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(14).toString()) }
    var notes by rememberSaveable { mutableStateOf("") }
    PosModal(stringResource(R.string.pos_create_quote), onDismiss = onDismiss) {
        PosField(stringResource(R.string.pos_valid_until), until, { until = it }, placeholder = "YYYY-MM-DD")
        Spacer(Modifier.height(10.dp))
        PosField(stringResource(R.string.pos_notes_optional), notes, { notes = it })
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, enabled = true, onClick = onDismiss, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(stringResource(R.string.pos_create_quote), enabled = true, onClick = { onCreate(until.ifBlank { null }, notes.ifBlank { null }) })
        }
    }
}

// ---------------------------------------------------------------- Customer

@Composable
fun CustomerScreen(state: PosState, dispatch: (PosIntent) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var editing by rememberSaveable { mutableStateOf<String?>(null) } // null = closed, "" = new, id = edit
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(250)
        dispatch(PosSaleIntent.SearchCustomers(query))
    }
    TwoPane(
        first = {
            PosPanel(stringResource(R.string.pos_find_customer)) {
                PosField(stringResource(R.string.pos_customer_search), query, { query = it }, placeholder = stringResource(R.string.pos_customer_search_hint))
                Spacer(Modifier.height(8.dp))
                val results = state.customerResults
                when {
                    state.customerSearching -> EmptyCard(stringResource(R.string.pos_searching))
                    results == null -> EmptyCard(stringResource(R.string.pos_customer_search_hint))
                    results.isEmpty() -> EmptyCard(stringResource(R.string.pos_no_customers))
                    else -> results.forEach { c ->
                        ListRow(
                            title = c.displayName,
                            subtitle = listOfNotNull(c.businessName, c.phoneE164, c.email).joinToString(" · ").ifBlank { null },
                            selected = state.customer?.id == c.id,
                            onClick = { dispatch(PosSaleIntent.SelectCustomer(c)) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                SoftButton(stringResource(R.string.pos_new_customer), PosIcons.Plus, enabled = true, onClick = { editing = "" })
            }
        },
        second = {
            val c = state.customer
            PosPanel(stringResource(R.string.pos_this_sale)) {
                if (c == null) {
                    EmptyCard(stringResource(R.string.pos_walk_in))
                } else {
                    ListRow(c.displayName, listOfNotNull(c.businessName, c.phoneE164, c.whatsappE164, c.email).joinToString(" · ").ifBlank { null })
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                        SoftButton(stringResource(R.string.pos_edit), null, enabled = true, onClick = { editing = c.id }, modifier = Modifier.weight(1f))
                        SoftButton(stringResource(R.string.pos_remove_customer), null, enabled = true, onClick = { dispatch(PosSaleIntent.ClearCustomer) }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(14.dp))
                    PosText(stringResource(R.string.pos_garage), PosTheme.type.labelAction.copy(fontWeight = FontWeight.SemiBold), PosTheme.palette.textPrimary)
                    if (state.garage.isEmpty()) EmptyCard(stringResource(R.string.pos_garage_empty))
                    state.garage.forEach { v ->
                        ListRow(
                            title = v.label.ifBlank { v.model ?: "" },
                            subtitle = if (v.isPrimary) stringResource(R.string.pos_primary) else null,
                            selected = state.vehicle?.chassisCode == v.chassisCode && state.vehicle?.engineCode == v.engine,
                            onClick = v.selection()?.let { { dispatch(PosSaleIntent.ChooseGarageVehicle(v)) } },
                        )
                    }
                    if (state.vehicle != null) {
                        Spacer(Modifier.height(8.dp))
                        SoftButton(stringResource(R.string.pos_save_vehicle_garage, state.vehicle!!.label), PosIcons.Car, enabled = true, onClick = { dispatch(PosSaleIntent.SaveVehicleToGarage(primary = state.garage.isEmpty())) })
                    }
                }
            }
        },
    )
    editing?.let { id ->
        CustomerFormDialog(
            existing = state.customerResults?.firstOrNull { it.id == id } ?: state.customer?.takeIf { it.id == id },
            onDismiss = { editing = null },
            onSave = { draft ->
                editing = null
                dispatch(if (id.isEmpty()) PosSaleIntent.CreateCustomer(draft) else PosSaleIntent.UpdateCustomer(id, draft))
            },
        )
    }
}

@Composable
private fun CustomerFormDialog(existing: Customer?, onDismiss: () -> Unit, onSave: (CustomerDraft) -> Unit) {
    var kind by rememberSaveable { mutableStateOf(existing?.kind ?: CustomerKind.Individual) }
    var name by rememberSaveable { mutableStateOf(existing?.displayName.orEmpty()) }
    var business by rememberSaveable { mutableStateOf(existing?.businessName.orEmpty()) }
    var email by rememberSaveable { mutableStateOf(existing?.email.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(existing?.phoneE164.orEmpty()) }
    var whatsapp by rememberSaveable { mutableStateOf(existing?.whatsappE164.orEmpty()) }
    PosModal(stringResource(if (existing == null) R.string.pos_new_customer else R.string.pos_edit_customer), onDismiss = onDismiss, width = 560.dp) {
        PosSegmented(
            CustomerKind.entries,
            kind,
            label = { stringResource(if (it == CustomerKind.Individual) R.string.pos_individual else R.string.pos_business) },
            onSelect = { kind = it },
        )
        Spacer(Modifier.height(12.dp))
        PosField(stringResource(R.string.pos_display_name), name, { name = it })
        if (kind == CustomerKind.Business) {
            Spacer(Modifier.height(10.dp))
            PosField(stringResource(R.string.pos_business_name), business, { business = it })
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PosField(stringResource(R.string.pos_phone), phone, { phone = it }, placeholder = "+26377…", keyboard = KeyboardType.Phone, modifier = Modifier.weight(1f))
            PosField(stringResource(R.string.pos_whatsapp), whatsapp, { whatsapp = it }, placeholder = "+26377…", keyboard = KeyboardType.Phone, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        PosField(stringResource(R.string.pos_email), email, { email = it }, keyboard = KeyboardType.Email)
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, enabled = true, onClick = onDismiss, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(
                stringResource(R.string.pos_save),
                enabled = name.isNotBlank(),
                onClick = { onSave(CustomerDraft(kind, name, business.ifBlank { null }, email.ifBlank { null }, phone.ifBlank { null }, whatsapp.ifBlank { null })) },
            )
        }
    }
}

// ---------------------------------------------------------------- Orders

@Composable
fun OrdersScreen(state: PosState, dispatch: (PosIntent) -> Unit) {
    var sending by rememberSaveable { mutableStateOf<String?>(null) }
    TwoPane(
        first = {
            PosPanel(stringResource(R.string.pos_parked_sales)) {
                val parked = state.parked
                when {
                    parked == null -> EmptyCard(stringResource(R.string.pos_loading))
                    parked.isEmpty() -> EmptyCard(stringResource(R.string.pos_no_parked))
                    else -> parked.forEach { p ->
                        ListRow(
                            title = p.documentNumber ?: p.id.take(8),
                            subtitle = stringResource(R.string.pos_lines_count, p.lineCount) + (p.updatedAt?.let { " · ${it.take(16).replace('T', ' ')}" } ?: ""),
                            trailing = formatMoney(p.total),
                        ) {
                            SoftButton(stringResource(R.string.pos_resume), null, enabled = state.cart.isEmpty, onClick = { dispatch(PosSaleIntent.Resume(p)) }, modifier = Modifier.width(120.dp))
                        }
                    }
                }
                if (!state.cart.isEmpty) PosText(stringResource(R.string.pos_finish_current_first), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
            }
        },
        second = {
            PosPanel(stringResource(R.string.pos_quotations)) {
                val quotes = state.quotations
                when {
                    quotes == null -> EmptyCard(stringResource(R.string.pos_loading))
                    quotes.isEmpty() -> EmptyCard(stringResource(R.string.pos_no_quotes))
                    else -> quotes.forEach { q ->
                        ListRow(
                            title = q.documentNumber ?: q.id.take(8),
                            subtitle = listOfNotNull(q.status, q.validUntil?.let { stringResource(R.string.pos_valid_until_value, it) }, q.sentChannel).joinToString(" · "),
                            trailing = formatMoney(q.total),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                SoftButton(stringResource(R.string.pos_send), null, enabled = true, onClick = { sending = q.id }, modifier = Modifier.width(120.dp))
                                SoftButton(stringResource(R.string.pos_convert), null, enabled = state.cart.isEmpty, onClick = { dispatch(PosSaleIntent.ConvertQuotation(q)) }, modifier = Modifier.width(120.dp))
                            }
                        }
                    }
                }
            }
        },
    )
    sending?.let { id ->
        state.quotations?.firstOrNull { it.id == id }?.let { q -> SendQuoteDialog(q, onDismiss = { sending = null }, onSend = { ch, contact ->
            sending = null
            dispatch(PosSaleIntent.SendQuotation(q, ch, contact))
        }) }
    }
}

@Composable
private fun SendQuoteDialog(quote: Quotation, onDismiss: () -> Unit, onSend: (QuoteChannel, String?) -> Unit) {
    var channel by rememberSaveable { mutableStateOf(QuoteChannel.WhatsApp) }
    var contact by rememberSaveable { mutableStateOf("") }
    PosModal(stringResource(R.string.pos_send_quote, quote.documentNumber ?: ""), onDismiss = onDismiss) {
        PosSegmented(
            QuoteChannel.entries,
            channel,
            label = {
                stringResource(
                    when (it) {
                        QuoteChannel.Print -> R.string.pos_channel_print
                        QuoteChannel.Email -> R.string.pos_email
                        QuoteChannel.WhatsApp -> R.string.pos_whatsapp
                    },
                )
            },
            onSelect = { channel = it },
        )
        if (channel != QuoteChannel.Print) {
            Spacer(Modifier.height(12.dp))
            PosField(
                stringResource(if (channel == QuoteChannel.Email) R.string.pos_email else R.string.pos_whatsapp),
                contact,
                { contact = it },
                keyboard = if (channel == QuoteChannel.Email) KeyboardType.Email else KeyboardType.Phone,
            )
        }
        PosRowEnd {
            SoftButton(stringResource(R.string.pos_cancel), null, enabled = true, onClick = onDismiss, modifier = Modifier.widthIn(max = 140.dp))
            PosPrimaryButton(stringResource(R.string.pos_send), enabled = channel == QuoteChannel.Print || contact.isNotBlank(), onClick = { onSend(channel, contact.ifBlank { null }) })
        }
    }
}

// ---------------------------------------------------------------- Returns

@Composable
fun ReturnsScreen(state: PosState, dispatch: (PosIntent) -> Unit) {
    var query by rememberSaveable { mutableStateOf(state.invoiceQuery) }
    LaunchedEffect(query) {
        kotlinx.coroutines.delay(300)
        if (query != state.invoiceQuery) dispatch(PosSaleIntent.LoadInvoices(query))
    }
    PosPanel(stringResource(R.string.pos_returns_title)) {
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
                ) {
                    SoftButton(stringResource(R.string.pos_refund), null, enabled = true, onClick = { dispatch(PosSaleIntent.RequestApproval(ApprovalRequest.Refund(inv))) }, modifier = Modifier.width(120.dp))
                }
            }
        }
        PosText(stringResource(R.string.pos_refund_hint), PosTheme.type.bodySecondary, PosTheme.palette.textMuted, modifier = Modifier.padding(top = 8.dp))
    }
}

// ---------------------------------------------------------------- EPC Browse

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EpcScreen(state: PosState, dispatch: (PosIntent) -> Unit) {
    val epc = state.epc
    val palette = PosTheme.palette
    PosPanel(stringResource(R.string.pos_nav_epc)) {
        // Breadcrumb + back
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (epc.model != null) {
                Box(Modifier.clip(PosTheme.shape.sm).clickable(role = Role.Button) { dispatch(PosSaleIntent.EpcBack) }.padding(6.dp)) {
                    PosIcon(PosIcons.ChevronLeft, tint = palette.textPrimary, size = 18.dp, contentDescription = stringResource(R.string.pos_back))
                }
            }
            PosText(
                listOfNotNull(epc.model?.name, epc.variant?.label, epc.section?.name, epc.detail?.diagram?.title).joinToString(" › ")
                    .ifBlank { stringResource(R.string.pos_epc_pick_model) },
                PosTheme.type.labelAction,
                palette.textSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        val chips: List<Pair<String, () -> Unit>> = when {
            epc.model == null -> state.cascade.models.map { m -> m.name to { dispatch(PosSaleIntent.EpcPickModel(m)) } }
            epc.variant == null -> epc.variants.orEmpty().map { v -> v.label to { dispatch(PosSaleIntent.EpcPickVariant(v)) } }
            epc.section == null -> epc.sections.orEmpty().map { s -> s.name to { dispatch(PosSaleIntent.EpcPickSection(s)) } }
            epc.detail == null -> epc.diagrams.orEmpty().map { d -> d.title to { dispatch(PosSaleIntent.EpcPickDiagram(d)) } }
            else -> emptyList()
        }
        if (epc.loading) EmptyCard(stringResource(R.string.pos_loading))
        else if (epc.detail == null && chips.isEmpty()) EmptyCard(stringResource(R.string.pos_epc_empty))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            chips.forEach { (label, onClick) ->
                PosText(
                    label,
                    PosTheme.type.labelAction,
                    palette.textPrimary,
                    maxLines = 2,
                    modifier = Modifier
                        .widthIn(min = 120.dp, max = 260.dp)
                        .clip(PosTheme.shape.md)
                        .background(palette.canvas)
                        .clickable(role = Role.Button, onClick = onClick)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            }
        }
        epc.detail?.let { detail ->
            EpcDiagramDetailView(detail, epc.image, epc.activeOem, dispatch)
        }
    }
}

// ---------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(state: PosState, dispatch: (PosIntent) -> Unit, host: PosHostActions) {
    val palette = PosTheme.palette
    PosPanel(stringResource(R.string.pos_nav_settings)) {
        host.onStaffPortal?.let { open ->
            ListRow(stringResource(R.string.pos_staff_portal), stringResource(R.string.pos_staff_portal_hint)) {
                PosPrimaryButton(stringResource(R.string.pos_open), enabled = true, onClick = open)
            }
        }
        ListRow(stringResource(R.string.pos_haptics), stringResource(R.string.pos_haptics_hint)) {
            PosSegmented(
                listOf(true, false),
                state.hapticsEnabled,
                label = { stringResource(if (it) R.string.pos_on else R.string.pos_off) },
                onSelect = { dispatch(PosSaleIntent.SetHaptics(it)) },
            )
        }
        ListRow(stringResource(R.string.pos_companion_title), stringResource(R.string.pos_companion_settings_hint)) {
            SoftButton(
                stringResource(if (state.companion?.live == true) R.string.pos_companion_live else R.string.pos_companion_pair),
                null,
                enabled = state.online,
                onClick = { dispatch(co.zw.nissangtr.pos.domain.state.CompanionIntent.Open) },
                modifier = Modifier.width(160.dp),
            )
        }
        ListRow(stringResource(R.string.pos_scanner_link_title), stringResource(R.string.pos_scanner_link_hint)) {
            SoftButton(
                stringResource(if (state.scanner != null) R.string.pos_scanner_link_live_short else R.string.pos_open),
                null,
                enabled = state.online || state.scanner != null,
                onClick = { dispatch(co.zw.nissangtr.pos.domain.state.CompanionIntent.OpenScanner) },
                modifier = Modifier.width(160.dp),
            )
        }
        ListRow(
            stringResource(R.string.pos_offline_sales),
            stringResource(R.string.pos_offline_sales_hint, state.offlineQueue.pending, state.offlineQueue.conflicts),
        ) {
            SoftButton(
                stringResource(if (state.offlineSyncing) R.string.pos_loading else R.string.pos_sync_now),
                null,
                enabled = state.online && !state.offlineSyncing,
                onClick = { dispatch(PosSaleIntent.SyncOffline) },
                modifier = Modifier.width(140.dp),
            )
        }
        ListRow(stringResource(R.string.pos_scanner), stringResource(R.string.pos_scanner_hint))
        ListRow(stringResource(R.string.pos_receipts_setting), stringResource(R.string.pos_receipts_setting_hint))
        host.onKioskSettings?.let { open ->
            ListRow(stringResource(R.string.pos_kiosk), stringResource(R.string.pos_kiosk_hint)) {
                SoftButton(stringResource(R.string.pos_open), null, enabled = true, onClick = open, modifier = Modifier.width(120.dp))
            }
        }
        host.onExitToHub?.let { exit ->
            ListRow(stringResource(R.string.pos_modules), null) {
                SoftButton(stringResource(R.string.pos_open), null, enabled = true, onClick = exit, modifier = Modifier.width(120.dp))
            }
        }
        state.operator?.let { op ->
            PosText(stringResource(R.string.pos_signed_in_as, op.displayName, op.roleLabel), PosTheme.type.bodySecondary, palette.textMuted, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
