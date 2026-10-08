package co.zw.nissangtr.pos.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import co.zw.nissangtr.pos.design.icons.Pin
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.icons.ScanBarcode
import co.zw.nissangtr.pos.design.primitives.posFocusRing
import co.zw.nissangtr.pos.design.primitives.posNeuRaised
import co.zw.nissangtr.pos.design.primitives.posNeuRaisedSmall
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.design.theme.PosWindowClass
import co.zw.nissangtr.pos.ui.common.PosModal
import co.zw.nissangtr.pos.ui.common.PosPrimaryButton
import co.zw.nissangtr.pos.ui.common.PosRowEnd
import co.zw.nissangtr.pos.domain.model.Operator
import co.zw.nissangtr.pos.domain.model.VehicleCascade
import co.zw.nissangtr.pos.domain.model.VehicleGeneration
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.ui.R
import co.zw.nissangtr.pos.ui.common.PosIcon
import co.zw.nissangtr.pos.ui.common.PosText
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Header, four zones (Blueprint §6.2): cascade · search · operator · date/time. */
@Composable
fun PosHeader(
    cascade: VehicleCascade,
    searchQuery: String,
    operator: Operator?,
    online: Boolean,
    now: LocalDateTime,
    onPickModel: (VehicleModel) -> Unit,
    onPickGeneration: (VehicleGeneration) -> Unit,
    onPickEngine: (String) -> Unit,
    onPinVehicle: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    /** Offline outbox: sales waiting to replay plus refused replays (§10.12, D-005). */
    queued: Int = 0,
    /** Till state for the operator block (D-016); tapping the block opens the Till screen. */
    till: String? = null,
    onTill: () -> Unit = {},
    /** Approvals pill (shown only while something waits for the operator). */
    approvals: @Composable () -> Unit = {},
) {
    val palette = PosTheme.palette
    val windowClass = PosTheme.geometry.windowClass
    if (windowClass == PosWindowClass.CompactPortrait || windowClass == PosWindowClass.CompactLandscape) {
        Column(modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VehicleButton(cascade, onPickModel, onPickGeneration, onPickEngine, onPinVehicle, Modifier.weight(1f))
                approvals()
                OperatorBlock(operator, compact = true, till = till, onClick = onTill)
            }
            SearchField(searchQuery, onSearchChange, onSearchSubmit, onScan, Modifier.fillMaxWidth())
            SyncStatus(online, queued)
        }
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .padding(horizontal = if (windowClass == PosWindowClass.Expanded) 20.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (windowClass == PosWindowClass.Expanded) 16.dp else 12.dp),
    ) {
        when (windowClass) {
            PosWindowClass.Expanded ->
                CascadeFields(cascade, onPickModel, onPickGeneration, onPickEngine, onPinVehicle, stacked = false)
            else ->
                VehicleButton(cascade, onPickModel, onPickGeneration, onPickEngine, onPinVehicle, Modifier.widthIn(max = 260.dp))
        }

        SearchField(
            query = searchQuery,
            onChange = onSearchChange,
            onSubmit = onSearchSubmit,
            onScan = onScan,
            modifier = Modifier.weight(1f).widthIn(max = 560.dp),
        )

        SyncStatus(online, queued)

        approvals()
        OperatorBlock(operator, compact = windowClass != PosWindowClass.Expanded, till = till, onClick = onTill)
        ClockBlock(now)
    }
}

@Composable
private fun <T> CascadeField(
    label: String,
    value: String?,
    placeholder: String,
    enabled: Boolean,
    options: List<T>,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
    width: Int,
) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    var open by remember { mutableStateOf(false) }
    val dropOffset = with(LocalDensity.current) { 44.dp.roundToPx() }
    Column(Modifier.width(width.dp)) {
        PosText(label, type.labelMeta, palette.textMuted, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .posNeuRaisedSmall()
                    .clip(PosTheme.shape.sm)
                    .background(palette.surfacePrimary)
                    .alpha(if (enabled) 1f else 0.5f)
                    .posFocusRing()
                    .clickable(enabled = enabled, role = Role.DropdownList) { open = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PosText(
                    text = value ?: placeholder,
                    style = type.bodyPrimary,
                    color = if (value != null) palette.textPrimary else palette.textMuted,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                PosIcon(PosIcons.ChevronDown, tint = palette.textMuted, size = 16.dp)
            }
            if (open) {
                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(0, dropOffset),
                    onDismissRequest = { open = false },
                    properties = PopupProperties(focusable = true),
                ) {
                    Column(
                        Modifier
                            .width((width + 60).dp)
                            .heightIn(max = 320.dp)
                            .posNeuRaised()
                            .clip(PosTheme.shape.md)
                            .background(palette.surfaceElevated)
                            .border(1.dp, palette.borderSubtle, PosTheme.shape.md)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 6.dp),
                    ) {
                        options.forEach { option ->
                            PosText(
                                text = optionLabel(option),
                                style = type.bodyPrimary,
                                color = palette.textPrimary,
                                maxLines = 1,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) {
                                        open = false
                                        onPick(option)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    val label = stringResource(R.string.pos_search_label)
    Row(
        modifier = modifier
            .height(52.dp)
            .posNeuRaised()
            .clip(PosTheme.shape.md)
            .background(palette.surfacePrimary)
            .padding(start = 18.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PosIcon(PosIcons.Search, tint = palette.textSecondary, size = 20.dp)
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                PosText(stringResource(R.string.pos_search_placeholder), type.bodyPrimary, palette.textMuted, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = type.bodyPrimary.copy(color = palette.textPrimary),
                cursorBrush = SolidColor(palette.brandRed),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(PosTheme.shape.sm)
                .posFocusRing()
                .clickable(role = Role.Button, onClick = onScan),
            contentAlignment = Alignment.Center,
        ) {
            PosIcon(PosIcons.ScanBarcode, tint = palette.textSecondary, size = 20.dp, contentDescription = stringResource(R.string.pos_scan))
        }
    }
}

@Composable
private fun OperatorBlock(operator: Operator?, compact: Boolean = false, till: String? = null, onClick: () -> Unit = {}) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    if (operator == null) return
    Row(
        Modifier.clip(PosTheme.shape.sm).clickable(role = Role.Button, onClickLabel = till, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(palette.navBackground),
            contentAlignment = Alignment.Center,
        ) {
            PosText(
                text = operator.displayName.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase(Locale.ROOT),
                style = type.labelAction.copy(fontWeight = FontWeight.Bold),
                color = palette.textOnBrand,
            )
        }
        if (!compact) {
            Column {
                PosText(operator.displayName, type.labelAction.copy(fontWeight = FontWeight.SemiBold), palette.textPrimary, maxLines = 1)
                PosText(till ?: operator.roleLabel, type.labelMeta, palette.textMuted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ClockBlock(now: LocalDateTime) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    Column(horizontalAlignment = Alignment.End) {
        PosText(now.format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)), type.labelMeta, palette.textMuted, maxLines = 1)
        PosText(now.format(DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)), type.heading3.copy(fontWeight = FontWeight.Bold), palette.textPrimary, maxLines = 1)
    }
}

@Composable
private fun CascadeFields(
    cascade: VehicleCascade,
    onPickModel: (VehicleModel) -> Unit,
    onPickGeneration: (VehicleGeneration) -> Unit,
    onPickEngine: (String) -> Unit,
    onPinVehicle: () -> Unit,
    stacked: Boolean,
) {
    val palette = PosTheme.palette
    val content: @Composable () -> Unit = {
            CascadeField(
                label = stringResource(R.string.pos_cascade_model),
                value = cascade.model?.name,
                placeholder = stringResource(R.string.pos_cascade_select_model),
                enabled = cascade.models.isNotEmpty(),
                options = cascade.models,
                optionLabel = { it.name },
                onPick = onPickModel,
                width = 120,
            )
            CascadeField(
                label = stringResource(R.string.pos_cascade_generation),
                value = cascade.generation?.label,
                placeholder = stringResource(R.string.pos_cascade_generation),
                enabled = cascade.generationEnabled && cascade.generations.isNotEmpty(),
                options = cascade.generations,
                optionLabel = { it.label },
                onPick = onPickGeneration,
                width = 112,
            )
            CascadeField(
                label = stringResource(R.string.pos_cascade_engine),
                value = cascade.engine,
                placeholder = stringResource(R.string.pos_cascade_engine),
                enabled = cascade.engineEnabled && cascade.engines.isNotEmpty(),
                options = cascade.engines,
                optionLabel = { it },
                onPick = onPickEngine,
                width = 112,
            )
            val canPin = cascade.selection() != null
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(PosTheme.shape.sm)
                    .alpha(if (canPin) 1f else 0.4f)
                    .posFocusRing()
                    .clickable(enabled = canPin, role = Role.Button, onClick = onPinVehicle),
                contentAlignment = Alignment.Center,
            ) {
                PosIcon(PosIcons.Pin, tint = palette.textSecondary, size = 18.dp, contentDescription = stringResource(R.string.pos_cascade_pin_vehicle))
            }
            }
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) { content() }
    }
}

/** Medium / Compact: one vehicle field that opens the cascade as a focus dialog (§8.4). */
@Composable
private fun VehicleButton(
    cascade: VehicleCascade,
    onPickModel: (VehicleModel) -> Unit,
    onPickGeneration: (VehicleGeneration) -> Unit,
    onPickEngine: (String) -> Unit,
    onPinVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = PosTheme.palette
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(48.dp)
            .posNeuRaisedSmall()
            .clip(PosTheme.shape.md)
            .background(palette.surfacePrimary)
            .posFocusRing(PosTheme.shape.md)
            .clickable(role = Role.Button) { open = true }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PosIcon(PosIcons.Car, tint = palette.textPrimary, size = 18.dp)
        PosText(
            cascade.selection()?.label ?: stringResource(R.string.pos_select_vehicle),
            PosTheme.type.labelAction.copy(fontWeight = FontWeight.SemiBold),
            palette.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
        )
        PosIcon(PosIcons.ChevronDown, tint = palette.textMuted, size = 16.dp)
    }
    if (open) {
        PosModal(stringResource(R.string.pos_choose_vehicle), onDismiss = { open = false }) {
            CascadeFields(cascade, onPickModel, onPickGeneration, onPickEngine, onPinVehicle, stacked = true)
            PosRowEnd {
                PosPrimaryButton(stringResource(R.string.pos_done), enabled = true, onClick = { open = false })
            }
        }
    }
}

/** Sync state in the context zone: offline restricted mode, and sales still waiting to replay. */
@Composable
private fun SyncStatus(online: Boolean, queued: Int) {
    val text = when {
        !online && queued > 0 -> stringResource(R.string.pos_offline_queued, queued)
        !online -> stringResource(R.string.pos_offline)
        queued > 0 -> pluralStringResource(R.plurals.pos_to_sync, queued, queued)
        else -> return
    }
    PosText(text, PosTheme.type.labelMeta.copy(fontWeight = FontWeight.SemiBold), PosTheme.palette.offline, maxLines = 2)
}
