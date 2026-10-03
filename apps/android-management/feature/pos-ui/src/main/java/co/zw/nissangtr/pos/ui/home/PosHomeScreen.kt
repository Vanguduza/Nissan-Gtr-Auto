package co.zw.nissangtr.pos.ui.home

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.zw.nissangtr.pos.design.icons.CircleAlert
import co.zw.nissangtr.pos.design.icons.CircleCheck
import co.zw.nissangtr.pos.design.icons.Pin
import co.zw.nissangtr.pos.design.icons.PinOff
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.layout.PosAdaptiveMath
import co.zw.nissangtr.pos.design.layout.PosScaffold
import co.zw.nissangtr.pos.design.primitives.posNeuRaised
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.state.PosDestination
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosIcon
import co.zw.nissangtr.pos.ui.common.PosText
import co.zw.nissangtr.pos.ui.common.feedbackText
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import co.zw.nissangtr.pos.design.icons.ChevronUp
import co.zw.nissangtr.pos.design.theme.PosWindowClass
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.ReceiptPaper
import co.zw.nissangtr.pos.domain.state.PosSaleIntent
import co.zw.nissangtr.pos.ui.sale.CompanionDialog
import co.zw.nissangtr.pos.ui.sale.ScannerDialog
import co.zw.nissangtr.pos.domain.state.isLocal
import co.zw.nissangtr.pos.domain.state.CompanionIntent
import co.zw.nissangtr.pos.ui.common.LocalPosDialogDepth
import co.zw.nissangtr.pos.ui.common.PosModal
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.formatMoney
import co.zw.nissangtr.pos.ui.common.posBehindDialog
import co.zw.nissangtr.pos.ui.common.rememberPosDialogDepth
import co.zw.nissangtr.pos.ui.sale.ApprovalDialog
import co.zw.nissangtr.pos.ui.sale.CustomerScreen
import co.zw.nissangtr.pos.ui.sale.EpcScreen
import co.zw.nissangtr.pos.ui.sale.GarageChooserDialog
import co.zw.nissangtr.pos.ui.sale.OrdersScreen
import co.zw.nissangtr.pos.ui.sale.PaymentDialog
import co.zw.nissangtr.pos.ui.sale.QuickSaleScreen
import co.zw.nissangtr.pos.ui.sale.ReceiptLine
import co.zw.nissangtr.pos.ui.sale.ReturnsScreen
import co.zw.nissangtr.pos.ui.sale.SettingsScreen
import java.time.LocalDateTime

/** Host actions for destinations and flows that live outside this screen (payment, customer, park). */
data class PosHostActions(
    /** Camera / companion scan through the QR bridge (Bridge-First); the code arrives as a search. */
    val onScan: () -> Unit,
    /** Print the receipt lines: ESC/POS for 80 mm, the document printer for A4. */
    val onPrint: (List<ReceiptLine>, ReceiptPaper) -> Unit,
    /** Settings → Staff portal: second login, then management (owner decision D4). */
    val onStaffPortal: (() -> Unit)? = null,
    /** Kiosk & device maintenance, for device administrators only. */
    val onKioskSettings: (() -> Unit)? = null,
    /** Leave the POS for the module hub (non-kiosk builds). */
    val onExitToHub: (() -> Unit)? = null,
    /** Settings → Offline catalogue: the downloadable full catalogue on this device. */
    val offlineCatalog: OfflineCatalogControl? = null,
)

enum class OfflineCatalogPhase { None, Downloading, Paused, Ready }

/** What the Settings row shows and does for the downloadable full catalogue (owned by the host). */
data class OfflineCatalogControl(
    val phase: OfflineCatalogPhase,
    val release: String?,
    val doneBytes: Long,
    val totalBytes: Long,
    /** Already formatted for the till's locale. */
    val downloadedAt: String?,
    val message: String?,
    val onDownload: () -> Unit,
    val onPause: () -> Unit,
    val onRemove: () -> Unit,
)

/**
 * Expanded POS shell (Blueprint §6, Phase 4): rail · header with cascade · discovery canvas ·
 * Current Sale pane. Pure function of [state]; every action is a [PosIntent] or a host action.
 */
@Composable
fun PosHomeScreen(
    state: PosState,
    now: LocalDateTime,
    dispatch: (PosIntent) -> Unit,
    host: PosHostActions,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val dialogDepth = rememberPosDialogDepth()
    val geometry = PosTheme.geometry
    val compact = geometry.windowClass == PosWindowClass.CompactPortrait || geometry.windowClass == PosWindowClass.CompactLandscape
    var cartSheet by rememberSaveable { mutableStateOf(false) }
    val tap: () -> Unit = { if (state.hapticsEnabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
    val cartPane: @Composable () -> Unit = {
        PosCartPane(
            cart = state.cart,
            busy = state.cartBusy > 0,
            customerName = state.customer?.displayName,
            onQty = { line, qty -> tap(); dispatch(PosIntent.SetQuantity(line.lineId, qty)) },
            onRemove = { dispatch(PosIntent.RemoveLine(it.lineId)) },
            onClear = { dispatch(PosSaleIntent.RequestApproval(ApprovalRequest.VoidSale)) },
            onAddCustomer = { cartSheet = false; dispatch(PosIntent.Navigate(PosDestination.Customer)) },
            onPay = { cartSheet = false; dispatch(PosSaleIntent.OpenPayment) },
            onPark = { cartSheet = false; dispatch(PosSaleIntent.Park) },
            onPhone = if (state.online && !state.cart.isLocal) ({ cartSheet = false; dispatch(CompanionIntent.Open) }) else null,
            phoneLive = state.companion?.live == true,
        )
    }
    CompositionLocalProvider(LocalPosDialogDepth provides dialogDepth) {
    BoxWithConstraints(modifier) {
    // Side cart only while rail + 320 dp cart + a usable canvas fit (§3.5); otherwise a sale bar + sheet.
    val cartAsSheet = !geometry.isCartPersistent || maxWidth < 900.dp
    PosScaffold(
        modifier = Modifier.posBehindDialog(dialogDepth.intValue),
        rail = if (compact && geometry.isBottomNav) null else {
            { PosRail(active = state.destination, onSelect = { dispatch(PosIntent.Navigate(it)) }) }
        },
        bottomBar = { PosBottomNav(active = state.destination, onSelect = { dispatch(PosIntent.Navigate(it)) }) },
        header = {
            PosHeader(
                cascade = state.cascade,
                searchQuery = state.searchQuery,
                operator = state.operator,
                online = state.online,
                now = now,
                onPickModel = { dispatch(PosIntent.PickModel(it)) },
                onPickGeneration = { dispatch(PosIntent.PickGeneration(it)) },
                onPickEngine = { dispatch(PosIntent.PickEngine(it)) },
                onPinVehicle = { state.cascade.selection()?.let { dispatch(PosIntent.Pin(PopularPin.forVehicle(it))) } },
                onSearchChange = { dispatch(PosIntent.EditSearch(it)) },
                onSearchSubmit = { dispatch(PosIntent.SubmitSearch) },
                queued = state.offlineQueue.pending + state.offlineQueue.conflicts,
                till = co.zw.nissangtr.pos.ui.sale.tillHeaderLabel(state),
                onTill = { dispatch(PosIntent.Navigate(PosDestination.Till)) },
                onScan = host.onScan,
            )
        },
        cartPane = if (cartAsSheet) null else cartPane,
    ) { _ ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
        // §3.6: the hero yields first — clamp(160, available * 0.34, 280); 240 dp at the canonical frame.
        val heroHeight = (maxHeight * 0.34f).coerceIn(160.dp, 280.dp)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = PosTheme.geometry.canvasGutter, end = PosTheme.geometry.canvasGutter, bottom = if (cartAsSheet) 96.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.feedback?.let { FeedbackBanner(it, onDismiss = { dispatch(PosIntent.DismissFeedback) }) }
            state.vehicle?.let { vehicle ->
                VehicleChip(vehicle.label, onClear = { dispatch(PosIntent.ClearVehicle) })
            }
            when (state.destination) {
                PosDestination.Home -> {
                    PosHero(height = if (compact) 112.dp else heroHeight, identityStrip = compact)
                    PosCategoryRow(
                        onOpen = { dispatch(PosIntent.OpenCategory(it)) },
                        onPin = { dispatch(PosIntent.Pin(PopularPin.forCategory(it))) },
                    )
                    PosPopularRow(
                        items = state.popularRow,
                        isPinned = state::isPinned,
                        addEnabled = state.cartBusy == 0,
                        onAdd = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            dispatch(PosIntent.AddPart(it))
                        },
                        onFind = { dispatch(PosIntent.SearchFor(it.searchQuery)) },
                        onPin = { dispatch(PosIntent.Pin(it)) },
                        onUnpin = { dispatch(PosIntent.Unpin(it)) },
                        onHide = { dispatch(PosIntent.HideBestSeller(it)) },
                        onActivate = { dispatch(PosIntent.ActivatePin(it)) },
                    )
                    PosRecentSearches(
                        recent = state.recentSearches,
                        onOpen = { dispatch(PosIntent.SearchFor(it)) },
                        onClear = { dispatch(PosIntent.ClearRecentSearches) },
                    )
                }
                PosDestination.SearchSpares -> SearchResults(state, dispatch)
                PosDestination.QuickSale -> QuickSaleScreen(state, dispatch)
                PosDestination.Customer -> CustomerScreen(state, dispatch)
                PosDestination.Orders -> OrdersScreen(state, dispatch)
                PosDestination.Returns -> ReturnsScreen(state, dispatch)
                PosDestination.EpcBrowse -> EpcScreen(state, dispatch)
                PosDestination.Till -> co.zw.nissangtr.pos.ui.sale.TillScreen(state, dispatch)
                PosDestination.Settings -> SettingsScreen(state, dispatch, host)
            }
        }
        if (cartAsSheet) {
            SaleBar(
                state = state,
                onOpen = { cartSheet = true },
                onPay = { dispatch(PosSaleIntent.OpenPayment) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        }
    }

    if (cartAsSheet && cartSheet) {
        PosModal(stringResource(R.string.pos_cart_title), onDismiss = { cartSheet = false }) {
            Box(Modifier.heightIn(min = 360.dp, max = 640.dp)) { cartPane() }
        }
    }
    if (state.paymentOpen || state.receipt != null) PaymentDialog(state, dispatch, host.onPrint)
    ApprovalDialog(state, dispatch)
    co.zw.nissangtr.pos.ui.sale.TillDialogs(state, dispatch)
    GarageChooserDialog(state, dispatch)
    if (state.companionOpen) CompanionDialog(state, dispatch, now)
    if (state.scannerOpen) ScannerDialog(state, dispatch, host.onScan)
    }
    }
}

/** Sale summary bar: the cart as a bar that opens a sheet when there is no room for the pane. */
@Composable
private fun SaleBar(state: PosState, onOpen: () -> Unit, onPay: () -> Unit, modifier: Modifier = Modifier) {
    val palette = PosTheme.palette
    Row(
        modifier
            .fillMaxWidth()
            .padding(12.dp)
            .posNeuRaised()
            .clip(PosTheme.shape.lg)
            .background(palette.surfacePrimary)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SoftButton(
            pluralStringResource(R.plurals.pos_items, state.cart.lines.size, state.cart.lines.size),
            PosIcons.ChevronUp,
            enabled = true,
            onClick = onOpen,
            modifier = Modifier.widthIn(max = 116.dp),
        )
        PosText(formatMoney(state.cart.total), PosTheme.type.numericPrice.copy(fontSize = 17.sp), palette.textPrimary, maxLines = 1, modifier = Modifier.weight(1f), align = TextAlign.End)
        PosPrimaryButton(stringResource(R.string.pos_pay), enabled = !state.cart.isEmpty && state.cartBusy == 0, onClick = onPay)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchResults(state: PosState, dispatch: (PosIntent) -> Unit) {
    val results = state.searchResults
    Column(Modifier.fillMaxWidth()) {
        SectionHead(
            state.vehicle?.let { stringResource(R.string.pos_search_for_vehicle, it.label) } ?: stringResource(R.string.pos_search_title),
        ) {
            if (results != null) {
                PosText(stringResource(R.string.pos_results_count, results.size), PosTheme.type.bodySecondary, PosTheme.palette.textMuted)
            }
        }
        when {
            state.searching -> EmptyCard(stringResource(R.string.pos_searching))
            results == null -> EmptyCard(stringResource(R.string.pos_search_hint))
            results.isEmpty() -> EmptyCard(stringResource(R.string.pos_search_none))
            else -> BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                val grid = PosAdaptiveMath.deriveLazyRow(maxWidth, minItemWidth = 180.dp, maxItemWidth = 260.dp, gap = 12.dp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    results.forEach { part ->
                        val pin = PopularPin.forPart(part)
                        val pinned = state.isPinned(pin)
                        PartCard(
                            part = part,
                            width = grid.itemWidth,
                            pinnedBadge = false,
                            addEnabled = state.cartBusy == 0,
                            onAdd = { dispatch(PosIntent.AddPart(part)) },
                            menu = listOf(
                                CardAction(
                                    if (pinned) PosIcons.PinOff else PosIcons.Pin,
                                    if (pinned) R.string.pos_unpin else R.string.pos_pin,
                                ) { dispatch(if (pinned) PosIntent.Unpin(pin) else PosIntent.Pin(pin)) },
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VehicleChip(label: String, onClear: () -> Unit) {
    val palette = PosTheme.palette
    Row(
        Modifier
            .clip(PosTheme.shape.pill)
            .background(palette.navBackground)
            .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PosIcon(PosIcons.Car, tint = palette.textOnBrand, size = 16.dp)
        PosText(label, PosTheme.type.labelAction, palette.textOnBrand, maxLines = 1)
        Box(
            Modifier.clip(PosTheme.shape.pill).clickable(role = Role.Button, onClick = onClear).padding(4.dp),
        ) {
            PosIcon(PosIcons.X, tint = palette.textOnBrand, size = 14.dp, contentDescription = stringResource(R.string.pos_cascade_clear_vehicle))
        }
    }
}

@Composable
private fun FeedbackBanner(feedback: PosFeedback, onDismiss: () -> Unit) {
    val palette = PosTheme.palette
    val isError = feedback is PosFeedback.Failure
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(feedback) {
        if (isError) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .posNeuRaised()
            .clip(PosTheme.shape.md)
            .background(palette.surfacePrimary)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosIcon(if (isError) PosIcons.CircleAlert else PosIcons.CircleCheck, tint = if (isError) palette.error else palette.success, size = 18.dp)
        Spacer(Modifier.width(10.dp))
        PosText(feedbackText(feedback), PosTheme.type.bodyPrimary, palette.textPrimary, modifier = Modifier.weight(1f))
        Box(Modifier.clip(PosTheme.shape.sm).clickable(role = Role.Button, onClick = onDismiss).padding(6.dp)) {
            PosIcon(PosIcons.X, tint = palette.textSecondary, size = 14.dp, contentDescription = stringResource(R.string.pos_dismiss))
        }
    }
}
