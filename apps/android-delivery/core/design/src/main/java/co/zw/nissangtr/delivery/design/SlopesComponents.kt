package co.zw.nissangtr.delivery.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
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

/** Bold large title with a quiet subtitle and compact actions on the right (sheet header). */
@Composable
fun SlopesLargeTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Slopes.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 2.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = c.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp),
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
        modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = c.label,
            modifier = Modifier.weight(1f),
        )
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = c.accent,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(2.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

/** Raised round button (search / close / info). */
@Composable
fun SlopesRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tinted: Boolean = true,
    size: Dp = 38.dp,
    enabled: Boolean = true,
) {
    val c = Slopes.colors
    Box(
        modifier
            .size(size)
            .neuRaisedSmall(cornerRadius = size / 2)
            .clip(CircleShape)
            .background(c.surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (tinted) c.accent else c.secondaryLabel,
            modifier = Modifier.size(size * 0.5f),
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
) = SlopesButton(label, onClick, modifier, enabled, icon, ButtonTone.Soft)

@Composable
fun SlopesDestructiveButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) = SlopesButton(label, onClick, modifier, enabled, icon, ButtonTone.Destructive)

private enum class ButtonTone { Primary, Soft, Destructive }

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
    val shape = RoundedCornerShape(16.dp)
    val bg = if (tone == ButtonTone.Primary && enabled) c.accent else c.surface
    val fg = when {
        !enabled -> c.tertiaryLabel
        tone == ButtonTone.Primary -> c.onAccent
        tone == ButtonTone.Destructive -> c.danger
        else -> c.accent
    }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .then(if (enabled) Modifier.neuRaised(cornerRadius = 16.dp, distance = 4.dp, blur = 10.dp) else Modifier)
            .clip(shape)
            .background(if (enabled) bg else c.fill)
            .then(if (!enabled) Modifier.neuInset(16.dp, 2.dp) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, textAlign = TextAlign.Center)
    }
}

data class SlopesAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val primary: Boolean = false,
    val enabled: Boolean = true,
)

/** Row of raised action tiles: icon over label; the primary one is solid GTR red. */
@Composable
fun SlopesActionTiles(
    actions: List<SlopesAction>,
    modifier: Modifier = Modifier,
) {
    val c = Slopes.colors
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        actions.forEach { a ->
            val fg = when {
                !a.enabled -> c.tertiaryLabel
                a.primary -> c.onAccent
                else -> c.accent
            }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 68.dp)
                    .then(if (a.enabled) Modifier.neuRaised(cornerRadius = 16.dp, distance = 4.dp, blur = 10.dp) else Modifier)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        when {
                            !a.enabled -> c.fill
                            a.primary -> c.accent
                            else -> c.surface
                        },
                    )
                    .then(if (!a.enabled) Modifier.neuInset(16.dp, 2.dp) else Modifier)
                    .clickable(enabled = a.enabled, role = Role.Button, onClick = a.onClick)
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(a.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(5.dp))
                Text(
                    a.label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

/** Big number, small caps unit, caption underneath. */
@Composable
fun SlopesStatBlock(stat: SlopesStat, modifier: Modifier = Modifier) {
    val c = Slopes.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 21.sp)) { append(stat.value) }
                stat.unit?.let {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 12.sp)) { append(" " + it.uppercase()) }
                }
            },
            color = stat.tint ?: c.label,
            maxLines = 1,
        )
        Text(stat.label, style = MaterialTheme.typography.bodySmall, color = c.secondaryLabel, maxLines = 1)
    }
}

/** Up to three stats evenly spaced in one raised strip. */
@Composable
fun SlopesStatRow(stats: List<SlopesStat>, modifier: Modifier = Modifier) {
    val c = Slopes.colors
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .neuRaised(cornerRadius = 18.dp, distance = 4.dp, blur = 11.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .padding(vertical = 14.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { i, s ->
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().padding(vertical = 4.dp).background(c.separator))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { SlopesStatBlock(s) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Cards, rows and drawers
// ---------------------------------------------------------------------------------------------

/** Raised soft-UI card holding rows. */
@Composable
fun SlopesGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .neuRaised()
            .clip(RoundedCornerShape(18.dp))
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
) {
    val c = Slopes.colors
    Column(modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        subtitleIcon?.let {
                            Icon(it, contentDescription = null, tint = c.tertiaryLabel, modifier = Modifier.size(13.dp))
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = c.secondaryLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
        if (divider) SlopesDivider(start = if (leading != null) 66.dp else 16.dp)
    }
}

@Composable
fun SlopesDivider(modifier: Modifier = Modifier, start: Dp = 16.dp) {
    Box(modifier.fillMaxWidth().padding(start = start, end = 12.dp).height(1.dp).background(Slopes.colors.separator))
}

/**
 * Collapsible drawer: a raised card whose header (icon, title, one-line summary) opens to show
 * the detail. Keeps secondary information one tap away instead of on the page.
 */
@Composable
fun SlopesDrawer(
    title: String,
    summary: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    initiallyOpen: Boolean = false,
    iconTint: Color = Slopes.colors.accent,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Slopes.colors
    var open by rememberSaveable(title) { mutableStateOf(initiallyOpen) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "drawer")
    SlopesGroup(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { open = !open }
                .heightIn(min = 62.dp)
                .padding(start = 16.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SlopesIconBadge(icon, tint = iconTint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.label)
                if (!summary.isNullOrBlank()) {
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = c.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (open) "Collapse" else "Expand",
                tint = c.secondaryLabel,
                modifier = Modifier.size(24.dp).rotate(turn),
            )
        }
        AnimatedVisibility(visible = open, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.fillMaxWidth()) {
                SlopesDivider(start = 16.dp)
                content()
            }
        }
    }
}

/** "3.7 / km" leading count used in stop lists. */
@Composable
fun SlopesLeadingCount(value: String, caption: String, modifier: Modifier = Modifier) {
    val c = Slopes.colors
    Column(modifier.widthIn(min = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = c.label)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
    }
}

/** Pressed-in icon well. */
@Composable
fun SlopesIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Slopes.colors.accent,
    container: Color = Slopes.colors.fill,
    round: Boolean = false,
    size: Dp = 38.dp,
) {
    val radius = if (round) size / 2 else 11.dp
    Box(
        modifier.size(size).clip(RoundedCornerShape(radius)).background(container).neuInset(radius, 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

/** Small capsule label (status, COD due). */
@Composable
fun SlopesPill(label: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = color,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (Slopes.colors.isDark) 0.2f else 0.12f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        maxLines = 1,
    )
}

/** Raised status chip with a coloured dot; tapping opens a chooser. */
@Composable
fun SlopesStatusChip(label: String, dot: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Slopes.colors
    Row(
        modifier
            .height(38.dp)
            .neuRaisedSmall(cornerRadius = 19.dp)
            .clip(RoundedCornerShape(19.dp))
            .background(c.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = c.label)
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(20.dp))
    }
}

/** Single-choice row with a trailing check. */
@Composable
fun SlopesCheckRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
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
        onClick = onClick,
        titleColor = if (selected) c.accent else null,
        trailing = {
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = c.accent, modifier = Modifier.size(22.dp))
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
                    checkedTrackColor = c.accent,
                    checkedThumbColor = Color.White,
                    checkedBorderColor = c.accent,
                    uncheckedTrackColor = c.fill,
                    uncheckedThumbColor = c.tertiaryLabel,
                    uncheckedBorderColor = c.separator,
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
            .padding(horizontal = 20.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.fill)
            .neuInset(14.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = c.secondaryLabel, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = c.tertiaryLabel, maxLines = 1)
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

/** Pressed-in text field with a caption above. */
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
                .heightIn(min = 50.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.fill)
                .neuInset(14.dp)
                .padding(horizontal = 14.dp, vertical = 14.dp),
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

/** Segmented control: pressed-in track with a raised sliding thumb. */
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
            .padding(horizontal = 20.dp)
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.fill)
            .neuInset(14.dp)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size.coerceAtLeast(1)
        val x by animateDpAsState(segment * selected, spring(dampingRatio = 0.8f, stiffness = 500f), label = "seg")
        Box(
            Modifier
                .offset(x = x)
                .width(segment)
                .fillMaxHeight()
                .neuRaisedSmall(cornerRadius = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(c.surface),
        )
        Row(Modifier.fillMaxWidth().fillMaxHeight()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(10.dp)).clickable(role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium),
                        color = if (i == selected) c.accent else c.secondaryLabel,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Timeline
// ---------------------------------------------------------------------------------------------

data class SlopesSegment(val weight: Float, val color: Color, val label: String? = null)

/** Pressed-in progress track with coloured segments and markers underneath. */
@Composable
fun SlopesTimeline(
    segments: List<SlopesSegment>,
    modifier: Modifier = Modifier,
    startLabel: String? = null,
    endLabel: String? = null,
) {
    val c = Slopes.colors
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        if (startLabel != null || endLabel != null) {
            Row(Modifier.fillMaxWidth()) {
                Text(startLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel, modifier = Modifier.weight(1f))
                Text(endLabel.orEmpty(), style = MaterialTheme.typography.labelSmall, color = c.secondaryLabel)
            }
            Spacer(Modifier.height(6.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(c.fill)
                .neuInset(8.dp, 2.dp)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            segments.forEach { s ->
                Box(Modifier.weight(s.weight.coerceAtLeast(0.01f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(s.color))
            }
        }
        if (segments.any { it.label != null }) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
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

/** Floating rounded column of map buttons. */
@Composable
fun SlopesMapControls(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.shadow(8.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Slopes.colors.mapChrome),
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
    Box(Modifier.size(48.dp).clickable(enabled = enabled, role = Role.Button, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = contentDescription, tint = if (enabled) c.accent else c.tertiaryLabel, modifier = Modifier.size(22.dp))
    }
}

/** Floating circular button over the map (back). */
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
            .size(44.dp)
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(c.mapChrome)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = c.label, modifier = Modifier.size(22.dp))
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
    inset: Dp = 20.dp,
) {
    val c = Slopes.colors
    val color = when (tone) {
        SlopesTone.Info -> c.secondaryLabel
        SlopesTone.Success -> c.success
        SlopesTone.Warning -> c.warning
        SlopesTone.Danger -> c.danger
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .clip(RoundedCornerShape(14.dp))
            .background(c.fill)
            .neuInset(14.dp, 2.dp)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = c.label, modifier = Modifier.weight(1f))
        if (action != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = c.accent,
                modifier = Modifier.padding(start = 8.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Pop-up sheets
// ---------------------------------------------------------------------------------------------

/** Body of a pop-up sheet: grabber, title with a close button, scrolling content. */
@Composable
fun SlopesSheetFrame(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Slopes.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(c.background),
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), contentAlignment = Alignment.Center) { SlopesGrabber() }
        SlopesLargeTitle(title = title, subtitle = subtitle) {
            SlopesRoundButton(Icons.Filled.Close, "Close", onClose, tinted = false, size = 34.dp)
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 8.dp)) {
            content()
            Spacer(Modifier.navigationBarsPadding().height(24.dp))
        }
    }
}

/** Modal pop-up sheet over the current screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlopesModalSheet(
    title: String,
    onDismiss: () -> Unit,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Slopes.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.background,
        scrimColor = c.scrim,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
    ) {
        SlopesSheetFrame(title = title, subtitle = subtitle, onClose = onDismiss, content = content)
    }
}

// ---------------------------------------------------------------------------------------------
// Tab bar
// ---------------------------------------------------------------------------------------------

data class SlopesTab(val key: String, val label: String, val icon: ImageVector)

/** Bottom tab bar: icon over label, GTR red when selected, hairline on top. */
@Composable
fun SlopesTabBar(
    tabs: List<SlopesTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Slopes.colors
    Column(modifier.fillMaxWidth().background(c.background)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.separator))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t ->
                val on = t.key == selectedKey
                val tint by animateColorAsState(if (on) c.accent else c.tertiaryLabel, label = "tab")
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable(role = Role.Tab) { onSelect(t.key) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(t.icon, contentDescription = null, tint = tint, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(
                        t.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium),
                        color = tint,
                    )
                }
            }
        }
    }
}
