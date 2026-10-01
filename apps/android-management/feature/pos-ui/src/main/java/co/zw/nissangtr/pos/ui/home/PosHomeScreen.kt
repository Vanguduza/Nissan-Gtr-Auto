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
import java.time.LocalDateTime

/** Host actions for destinations and flows that live outside this screen (payment, customer, park). */
data class PosHostActions(
    val onPay: () -> Unit,
    val onAddCustomer: () -> Unit,
    val onPark: () -> Unit,
    val onScan: () -> Unit,
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
    PosScaffold(
        modifier = modifier,
        rail = { PosRail(active = state.destination, onSelect = { dispatch(PosIntent.Navigate(it)) }) },
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
                onScan = host.onScan,
            )
        },
        cartPane = {
            PosCartPane(
                cart = state.cart,
                busy = state.cartBusy > 0,
                onQty = { line, qty -> dispatch(PosIntent.SetQuantity(line.lineId, qty)) },
                onRemove = { dispatch(PosIntent.RemoveLine(it.lineId)) },
                onAddCustomer = host.onAddCustomer,
                onPay = host.onPay,
                onPark = host.onPark,
            )
        },
    ) { _ ->
        BoxWithConstraints {
        // §3.6: the hero yields first — clamp(160, available * 0.34, 280); 240 dp at the canonical frame.
        val heroHeight = (maxHeight * 0.34f).coerceIn(160.dp, 280.dp)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = PosTheme.geometry.canvasGutter, end = PosTheme.geometry.canvasGutter, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.feedback?.let { FeedbackBanner(it, onDismiss = { dispatch(PosIntent.DismissFeedback) }) }
            state.vehicle?.let { vehicle ->
                VehicleChip(vehicle.label, onClear = { dispatch(PosIntent.ClearVehicle) })
            }
            when (state.destination) {
                PosDestination.Home -> {
                    PosHero(height = heroHeight)
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
                else -> EmptyCard(stringResource(R.string.pos_destination_pending, stringResource(RailItems.first { it.destination == state.destination }.label)))
            }
        }
        }
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
