package co.zw.nissangtr.pos.ui.sale

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.BranchStock
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.FulfillmentDraft
import co.zw.nissangtr.pos.domain.model.FulfillmentKind
import co.zw.nissangtr.pos.domain.model.FulfillmentRequest
import co.zw.nissangtr.pos.domain.model.FulfillmentStep
import co.zw.nissangtr.pos.domain.state.FulfillmentIntent
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.holdBlocked
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosPanel
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.PosSegmented
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.errorText
import co.zw.nissangtr.pos.ui.common.formatQty
import co.zw.nissangtr.pos.ui.home.EmptyCard
import co.zw.nissangtr.pos.ui.home.SoftButton

/*
 * Fulfilment (phase 7): "Get it for the customer" under Stock by branch, and Orders → Collections &
 * transfers with each request's next step.
 */

/** The branch this till sells from. */
internal val PosState.hereWarehouseId: String? get() = till.session?.warehouseId

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FulfillmentActions(state: PosState, part: CatalogPart, rows: List<BranchStock>, dispatch: (PosIntent) -> Unit) {
    val itemId = part.stockItemId ?: return
    val palette = PosTheme.palette
    var qty by rememberSaveable(itemId) { mutableStateOf(1.0) }
    val here = state.hereWarehouseId
    val holdWhy = state.holdBlocked(itemId)?.let { if (it == "fulfillment_no_sale") R.string.pos_ff_err_no_sale else R.string.pos_ff_err_not_in_sale }
    val freeHere = rows.firstOrNull { it.warehouseId == here }?.available ?: 0.0
    val enabled = state.online && !state.fulfillmentBusy
    val create = { kind: FulfillmentKind, src: String?, dest: String? ->
        dispatch(FulfillmentIntent.Create(FulfillmentDraft(kind, itemId, qty, src, dest, null, null)))
    }
    Spacer(Modifier.height(12.dp))
    Column(Modifier.fillMaxWidth().clip(PosTheme.shape.md).background(palette.canvas).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PosText(stringResource(R.string.pos_ff_title), PosTheme.type.bodyPrimary.copy(fontWeight = FontWeight.Bold), palette.textPrimary, modifier = Modifier.weight(1f))
            PosText(stringResource(R.string.pos_ff_qty), PosTheme.type.bodySecondary, palette.textMuted, modifier = Modifier.padding(end = 8.dp))
            SoftButton("−", null, enabled = qty > 1.0, onClick = { qty -= 1.0 }, modifier = Modifier.width(44.dp))
            PosText(formatQty(qty), PosTheme.type.bodyPrimary.copy(fontWeight = FontWeight.Bold), palette.textPrimary, modifier = Modifier.padding(horizontal = 10.dp))
            SoftButton("+", null, enabled = true, onClick = { qty += 1.0 }, modifier = Modifier.width(44.dp))
        }
        rows.forEach { r ->
            val enough = r.available >= qty
            if (r.warehouseId == here) {
                ListRow(
                    title = stringResource(R.string.pos_ff_hold_here),
                    subtitle = when {
                        !enough -> stringResource(R.string.pos_ff_only_free, formatQty(r.available))
                        holdWhy != null -> stringResource(holdWhy)
                        else -> stringResource(R.string.pos_ff_hold_here_hint)
                    },
                ) {
                    SoftButton(stringResource(R.string.pos_ff_hold), PosIcons.Package, enabled = enabled && enough && holdWhy == null, onClick = { create(FulfillmentKind.CustomerCollection, r.warehouseId, null) }, modifier = Modifier.width(120.dp))
                }
            } else {
                ListRow(
                    title = r.name.ifBlank { r.code },
                    subtitle = if (enough) stringResource(R.string.pos_ff_free, formatQty(r.available)) else stringResource(R.string.pos_ff_only_free, formatQty(r.available)),
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SoftButton(stringResource(R.string.pos_ff_collect_there), null, enabled = enabled && enough && holdWhy == null, onClick = { create(FulfillmentKind.AlternatePickup, r.warehouseId, null) }, modifier = Modifier.width(150.dp))
                        SoftButton(stringResource(R.string.pos_ff_bring_here), null, enabled = enabled && enough && here != null, onClick = { create(FulfillmentKind.BranchTransfer, r.warehouseId, here) }, modifier = Modifier.width(140.dp))
                    }
                }
            }
        }
        ListRow(
            title = stringResource(R.string.pos_ff_backorder),
            subtitle = stringResource(if (freeHere >= qty) R.string.pos_ff_backorder_stock_here else R.string.pos_ff_backorder_hint),
        ) {
            SoftButton(stringResource(R.string.pos_ff_backorder), null, enabled = enabled && freeHere < qty, onClick = { create(FulfillmentKind.Backorder, null, here) }, modifier = Modifier.width(150.dp))
        }
    }
}

private val FILTERS = listOf(null, "reserved", "awaiting_transfer_approval", "requested", "ready", "collected")

@Composable
private fun kindLabel(k: FulfillmentKind) = stringResource(
    when (k) {
        FulfillmentKind.CustomerCollection -> R.string.pos_ff_kind_collection
        FulfillmentKind.AlternatePickup -> R.string.pos_ff_kind_pickup
        FulfillmentKind.BranchTransfer -> R.string.pos_ff_kind_transfer
        FulfillmentKind.Backorder -> R.string.pos_ff_kind_backorder
    },
)

@Composable
private fun statusLabel(s: String) = when (s) {
    "requested" -> stringResource(R.string.pos_ff_st_requested)
    "reserved" -> stringResource(R.string.pos_ff_st_reserved)
    "awaiting_transfer_approval" -> stringResource(R.string.pos_ff_st_transfer)
    "ready" -> stringResource(R.string.pos_ff_st_ready)
    "collected" -> stringResource(R.string.pos_ff_st_collected)
    "cancelled" -> stringResource(R.string.pos_ff_st_cancelled)
    "rejected" -> stringResource(R.string.pos_ff_st_rejected)
    else -> s
}

/** Orders → Collections & transfers. */
@Composable
fun FulfillmentPanel(state: PosState, dispatch: (PosIntent) -> Unit) {
    LaunchedEffect(Unit) { if (state.fulfillment == null) dispatch(FulfillmentIntent.Load(state.fulfillmentStatus, null)) }
    PosPanel(stringResource(R.string.pos_ff_panel)) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            PosSegmented(
                FILTERS,
                state.fulfillmentStatus,
                label = {
                    stringResource(
                        when (it) {
                            "reserved" -> R.string.pos_ff_held
                            "awaiting_transfer_approval" -> R.string.pos_ff_in_transfer
                            "requested" -> R.string.pos_ff_waiting
                            "ready" -> R.string.pos_ff_ready
                            "collected" -> R.string.pos_ff_collected
                            else -> R.string.pos_ff_all
                        },
                    )
                },
                onSelect = { dispatch(FulfillmentIntent.Load(it, state.fulfillmentQuery)) },
            )
        }
        Spacer(Modifier.height(8.dp))
        val rows = state.fulfillment
        when {
            rows == null -> EmptyCard(stringResource(R.string.pos_loading))
            rows.isEmpty() -> EmptyCard(stringResource(R.string.pos_ff_none))
            else -> rows.forEach { f -> FulfillmentRow(state, f, dispatch) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FulfillmentRow(state: PosState, f: FulfillmentRequest, dispatch: (PosIntent) -> Unit) {
    val route = when (f.kind) {
        FulfillmentKind.BranchTransfer -> "${f.sourceName ?: "?"} → ${f.destinationName ?: "?"}"
        FulfillmentKind.Backorder -> f.destinationName
        else -> f.sourceName
    }
    ListRow(
        title = "${f.documentNumber ?: f.id.take(8)} · ${f.description ?: f.oemPartNumber} × ${formatQty(f.qty)}",
        subtitle = listOfNotNull(
            kindLabel(f.kind),
            statusLabel(f.status),
            route,
            if (f.status == "ready" && f.invoiceId == null && f.kind != FulfillmentKind.BranchTransfer && f.kind != FulfillmentKind.Backorder) stringResource(R.string.pos_ff_not_paid) else null,
            if (f.status == "ready" && f.kind == FulfillmentKind.Backorder) stringResource(R.string.pos_ff_arrived_hint) else null,
        ).joinToString(" · "),
    ) {
        val enabled = state.online && !state.fulfillmentBusy
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            f.steps.forEach { step ->
                val label = stringResource(
                    when (step) {
                        FulfillmentStep.SendTransfer -> R.string.pos_ff_send
                        FulfillmentStep.MarkReady -> R.string.pos_ff_mark_ready
                        FulfillmentStep.HandOver -> if (f.kind == FulfillmentKind.BranchTransfer) R.string.pos_ff_received else R.string.pos_ff_handed_over
                        FulfillmentStep.Release -> R.string.pos_ff_release
                    },
                )
                if (step == FulfillmentStep.Release) SoftButton(label, null, enabled = enabled, onClick = { dispatch(FulfillmentIntent.Step(f, step)) }, modifier = Modifier.width(120.dp))
                else PosPrimaryButton(label, enabled = enabled, onClick = { dispatch(FulfillmentIntent.Step(f, step)) }, modifier = Modifier.width(170.dp))
            }
        }
    }
}
