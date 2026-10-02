package co.zw.nissangtr.delivery.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------------------------
// Titles and headers
// ---------------------------------------------------------------------------------------------

/** Bold large title with a quiet subtitle and round tinted actions on the right (Slopes sheet header). */
@Composable
fun SlopesLargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Slopes.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(top = 2.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = c.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = c.secondaryLabel,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, start = 8.dp),
            content = actions,
        )
    }
}

@Composable
fun SlopesSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val c = Slopes.colors
    Row(
        modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = c.label,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.bodyMedium,
                color = c.accent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(2.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/** Small tinted circle (share / close / search / add in Slopes). */
@Composable
fun SlopesRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tinted: Boolean = true,
    size: Dp = 32.dp,
    enabled: Boolean = true,
) {
    val c = Slopes.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (tinted) c.accentTint else c.fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (tinted) c.accent else c.secondaryLabel,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
fun SlopesPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) = SlopesButton(label, onClick, modifier, enabled, icon, ButtonTone.Primary)

@Composable
fun SlopesTintedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) = SlopesButton(label, onClick, modifier, enabled, icon, ButtonTone.Tinted)

@Composable
fun SlopesDestructiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) = SlopesButton(label, onClick, modifier, enabled, icon, ButtonTone.Destructive)

private enum class ButtonTone { Primary, Tinted, Destructive }

@Composable
private fun SlopesButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    icon: ImageVector?,
    tone: ButtonTone,
) {
    val c = Slopes.colors
    val (bg, fg) = when (tone) {
        ButtonTone.Primary -> c.accent to c.onAccent
        ButtonTone.Tinted -> c.accentTint to c.accent
        ButtonTone.Destructive -> c.dangerTint to c.danger
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (enabled) bg else c.fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (enabled) fg else c.tertiaryLabel
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center)
    }
}

data class SlopesAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val enabled: Boolean = true,
)

/** Row of square-ish action tiles: icon over label; the first is usually the filled primary (Replay). */
@Composable
fun SlopesActionTiles(
    actions: List<SlopesAction>,
    modifier: Modifier = Modifier,
) {
    val c = Slopes.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 18.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        actions.forEach { a ->
            val bg = when {
                !a.enabled -> c.fill
                a.primary -> c.accent
                else -> c.accentTint
            }
            val fg = when {
                !a.enabled -> c.tertiaryLabel
                a.primary -> c.onAccent
                else -> c.accent
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 62.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg)
                    .clickable(enabled = a.enabled, role = Role.Button, onClick = a.onClick)
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(a.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    a.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Stats
// ---------------------------------------------------------------------------------------------

data class SlopesStat(
    val value: String,
    val label: String,
    val unit: String? = null,
    val icon: ImageVector? = null,
    val tint: Color? = null,
)

/** "42,5 MPH / top speed": big number, small caps unit, icon + caption underneath. */
@Composable
fun SlopesStatBlock(stat: SlopesStat, modifier: Modifier = Modifier, large: Boolean = false) {
    val c = Slopes.colors
    Column(modifier) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = if (large) 24.sp else 20.sp)) {
                    append(stat.value)
                }
                stat.unit?.let {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = if (large) 13.sp else 12.sp)) {
                        append(" " + it.uppercase())
                    }
                }
            },
            color = stat.tint ?: c.label,
            maxLines = 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            stat.icon?.let {
                Icon(it, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
            }
            Text(stat.label, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 1)
        }
    }
}

/** Inline stats separated by hairlines; scrolls sideways when it overflows (like Slopes' summary). */
@Composable
fun SlopesStatRow(stats: List<SlopesStat>, modifier: Modifier = Modifier) {
    val c = Slopes.colors
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { i, s ->
            if (i > 0) {
                Box(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                        .background(c.separator),
                )
            }
            SlopesStatBlock(s)
        }
    }
}

/** Two-column grid of stat tiles with chevrons (top speed / tallest run cards). */
@Composable
fun SlopesStatGrid(
    stats: List<Pair<SlopesStat, (() -> Unit)?>>,
    modifier: Modifier = Modifier,
) {
    val c = Slopes.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        stats.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                pair.forEach { (stat, onClick) ->
                    Row(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.background)
                            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SlopesStatBlock(stat, Modifier.weight(1f))
                        if (onClick != null) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = c.tertiaryLabel,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Grouped lists
// ---------------------------------------------------------------------------------------------

/** White inset group with rounded corners on the grey background. */
@Composable
fun SlopesGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Slopes.colors.surface),
        content = content,
    )
}

@Composable
fun SlopesRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleIcon: ImageVector? = null,
    titleColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    chevron: Boolean = true,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val c = Slopes.colors
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = titleColor ?: c.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        subtitleIcon?.let {
                            Icon(it, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.secondaryLabel,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                extra?.invoke(this)
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
            if (chevron && onClick != null) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = c.tertiaryLabel,
                    modifier = Modifier.padding(start = 4.dp).size(22.dp),
                )
            }
        }
        if (divider) SlopesDivider(start = if (leading != null) 62.dp else 16.dp)
    }
}

@Composable
fun SlopesDivider(modifier: Modifier = Modifier, start: Dp = 16.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(start = start)
            .height(1.dp)
            .background(Slopes.colors.separator),
    )
}

/** "92 / days" leading count used for upcoming items. */
@Composable
fun SlopesLeadingCount(value: String, caption: String, modifier: Modifier = Modifier) {
    val c = Slopes.colors
    Column(modifier.widthIn(min = 38.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.label)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
    }
}

/** Rounded-square or round icon badge (resort logos, emergency icons). */
@Composable
fun SlopesIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Slopes.colors.accent,
    container: Color = Slopes.colors.accentTint,
    round: Boolean = false,
    size: Dp = 34.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(if (round) CircleShape else RoundedCornerShape(9.dp))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/** Small capsule label (status, COD due). */
@Composable
fun SlopesPill(label: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = if (filled) Color.White else color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        maxLines = 1,
    )
}

/** Trailing checkmark row for single choice lists (iOS style). */
@Composable
fun SlopesCheckRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    divider: Boolean = true,
) {
    val c = Slopes.colors
    SlopesRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        chevron = false,
        divider = divider,
        onClick = onClick,
        trailing = {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = c.accent, modifier = Modifier.size(20.dp))
            }
        },
    )
}

@Composable
fun SlopesToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = Slopes.colors
    SlopesRow(
        title = title,
        subtitle = subtitle,
        modifier = modifier,
        chevron = false,
        divider = divider,
        leading = leading,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = c.success,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = c.fill,
                    uncheckedThumbColor = Color.White,
                    uncheckedBorderColor = c.fill,
                ),
            )
        },
    )
}

// ---------------------------------------------------------------------------------------------
// Inputs
// ---------------------------------------------------------------------------------------------

@Composable
fun SlopesSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val c = Slopes.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(38.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(c.fill)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.secondaryLabel, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.label),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Filled grey text field with a small caption above. */
@Composable
fun SlopesTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val c = Slopes.colors
    Column(modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.secondaryLabel, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.fill)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.tertiaryLabel)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                enabled = enabled,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.label),
                cursorBrush = SolidColor(c.accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Pill chips (Runs / Lifts / Medical): selected one filled in the accent. */
@Composable
fun SlopesChips(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Slopes.colors
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            val bg by animateColorAsState(if (on) c.accent else c.accentTint, label = "chip")
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (on) c.onAccent else c.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable(role = Role.Tab) { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Segmented control (Overview / Analyze / Vitals): sliding filled thumb on a grey track. */
@Composable
fun SlopesSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Slopes.colors
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.fill)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(segment * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .shadow(2.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(c.accent),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (i == selected) c.onAccent else c.label,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Timeline (runs bar)
// ---------------------------------------------------------------------------------------------

data class SlopesSegment(val weight: Float, val color: Color, val label: String? = null)

/** Thin segmented bar with numbered markers underneath — Slopes' run timeline. */
@Composable
fun SlopesTimeline(
    segments: List<SlopesSegment>,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    endLabel: String? = null,
) {
    val c = Slopes.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 18.dp)) {
        if (startLabel != null || endLabel != null) {
            Row(Modifier.fillMaxWidth()) {
                Text(startLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel, modifier = Modifier.weight(1f))
                Text(endLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
            }
            Spacer(Modifier.height(4.dp))
        }
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            segments.forEach { s ->
                Box(
                    Modifier
                        .weight(s.weight.coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(3.dp))
                        .background(s.color),
                )
            }
        }
        if (segments.any { it.label != null }) {
            Row(Modifier.fillMaxWidth().padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                segments.forEach { s ->
                    Text(
                        s.label.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = c.secondaryLabel,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(s.weight.coerceAtLeast(0.01f)),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Map chrome
// ---------------------------------------------------------------------------------------------

/** Floating rounded column of map buttons (layers / 2D / recenter). */
@Composable
fun SlopesMapControls(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .shadow(6.dp, RoundedCornerShape(10.dp))
            .clip(RoundedCornerShape(10.dp))
            .background(Slopes.colors.mapChrome),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun SlopesMapControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    divider: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Slopes.colors
    if (divider) Box(Modifier.width(30.dp).height(1.dp).background(c.separator))
    Box(
        Modifier
            .size(46.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) c.accent else c.tertiaryLabel, modifier = Modifier.size(22.dp))
    }
}

/** Floating circular button over the map (back, play). */
@Composable
fun SlopesMapFab(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Slopes.colors
    Box(
        modifier
            .size(42.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(c.mapChrome)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = c.accent, modifier = Modifier.size(22.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Messages
// ---------------------------------------------------------------------------------------------

enum class SlopesTone { Info, Success, Warning, Danger }

@Composable
fun SlopesBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: SlopesTone = SlopesTone.Info,
    icon: ImageVector? = null,
    action: String? = null,
    onAction: () -> Unit = {},
    inset: Dp = 18.dp,
) {
    val c = Slopes.colors
    val color = when (tone) {
        SlopesTone.Info -> c.accent
        SlopesTone.Success -> c.success
        SlopesTone.Warning -> c.warning
        SlopesTone.Danger -> c.danger
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = if (c.isDark) 0.20f else 0.11f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.label, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Tab bar
// ---------------------------------------------------------------------------------------------

data class SlopesTab(val key: String, val label: String, val icon: ImageVector)

/** Bottom tab bar: icon over label, accent when selected, hairline on top. */
@Composable
fun SlopesTabBar(
    tabs: List<SlopesTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Slopes.colors
    Column(modifier.fillMaxWidth().background(c.mapChrome)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.separator))
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val on = t.key == selectedKey
                val tint by animateColorAsState(if (on) c.accent else c.secondaryLabel, label = "tab")
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Tab) { onSelect(t.key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, style = MaterialTheme.typography.labelSmall, color = tint)
                }
            }
        }
    }
}

/** Content padding for plain (non-map) screens. */
val SlopesScreenPadding = PaddingValues(top = 8.dp, bottom = 24.dp)

@Composable
fun SlopesCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Slopes.colors
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.separator.copy(alpha = if (c.isDark) 0.6f else 0.0f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content,
    )
}
