package co.zw.nissangtr.pos.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import co.zw.nissangtr.pos.design.icons.PosIcons
import co.zw.nissangtr.pos.design.primitives.posFocusRing
import co.zw.nissangtr.pos.design.primitives.posNeuRaised
import co.zw.nissangtr.pos.design.primitives.posNeuRaisedSmall
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.design.theme.PosWindowClass
import co.zw.nissangtr.pos.ui.R

/** Count of open POS dialogs; the shell blurs itself while it is above zero. */
val LocalPosDialogDepth = compositionLocalOf<MutableIntState> { mutableIntStateOf(0) }

@Composable
fun rememberPosDialogDepth(): MutableIntState = remember { mutableIntStateOf(0) }

/**
 * Blur and dim the POS behind an open dialog. RenderEffect blur is API 31+; on older devices
 * [Modifier.blur] is a no-op and the dialog window's own dim still separates the layers.
 */
fun Modifier.posBehindDialog(depth: Int): Modifier =
    if (depth > 0) this.blur(14.dp).alpha(0.92f) else this

/**
 * Focus dialog: its own window, so touch, keyboard focus and TalkBack stay inside it while it is
 * open, and Back / outside tap dismiss it (when [dismissible]). Rises as a bottom sheet on phones.
 */
@Composable
fun PosModal(
    title: String,
    onDismiss: () -> Unit,
    dismissible: Boolean = true,
    width: Dp = 480.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val depth = LocalPosDialogDepth.current
    DisposableEffect(Unit) {
        depth.intValue += 1
        onDispose { depth.intValue -= 1 }
    }
    val windowClass = PosTheme.geometry.windowClass
    val sheet = windowClass == PosWindowClass.CompactPortrait || windowClass == PosWindowClass.CompactLandscape
    val palette = PosTheme.palette
    Dialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            decorFitsSystemWindows = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(palette.scrim.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = dismissible,
                    onClick = onDismiss,
                ),
            contentAlignment = if (sheet) Alignment.BottomCenter else Alignment.Center,
        ) {
            val shape = if (sheet) RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp) else PosTheme.shape.lg
            Column(
                Modifier
                    .then(if (sheet) Modifier.fillMaxWidth() else Modifier.padding(24.dp).widthIn(max = width).fillMaxWidth())
                    .heightIn(max = if (sheet) 720.dp else 880.dp)
                    // Crisp edge (owner): a solid border, no soft shadow halo around the dialog.
                    .clip(shape)
                    .background(palette.surfaceElevated)
                    .border(1.dp, palette.borderSubtle, shape)
                    // Swallow taps so they never reach the scrim behind the card.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                    .semantics { paneTitle = title }
                    .then(if (sheet) Modifier.navigationBarsPadding() else Modifier)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PosText(
                        title,
                        PosTheme.type.heading2.copy(fontWeight = FontWeight.Bold),
                        palette.textPrimary,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    if (dismissible) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(PosTheme.shape.sm)
                                .posFocusRing()
                                .clickable(role = Role.Button, onClick = onDismiss),
                            contentAlignment = Alignment.Center,
                        ) {
                            PosIcon(PosIcons.X, tint = palette.textSecondary, size = 18.dp, contentDescription = stringResource(R.string.pos_close))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.verticalScroll(rememberScrollState()), content = content)
            }
        }
    }
}

/** Labelled single-line field (pressed soft-UI well). */
@Composable
fun PosField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboard: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    enabled: Boolean = true,
) {
    val palette = PosTheme.palette
    val type = PosTheme.type
    Column(modifier) {
        PosText(label, type.labelMeta.copy(fontWeight = FontWeight.SemiBold), palette.textMuted, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(PosTheme.shape.sm)
                .background(palette.canvas)
                .border(1.dp, palette.borderSubtle, PosTheme.shape.sm)
                .alpha(if (enabled) 1f else 0.5f)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && placeholder.isNotEmpty()) PosText(placeholder, type.bodyPrimary, palette.textMuted, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                enabled = enabled,
                singleLine = true,
                textStyle = type.bodyPrimary.copy(color = palette.textPrimary),
                cursorBrush = SolidColor(palette.brandRed),
                keyboardOptions = KeyboardOptions(keyboardType = keyboard),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
    }
}

@Composable
fun PosPrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val palette = PosTheme.palette
    Row(
        modifier
            .height(52.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .posNeuRaisedSmall(cornerRadius = 14.dp)
            .clip(PosTheme.shape.md)
            .background(palette.brandRed)
            .posFocusRing(PosTheme.shape.md)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            PosIcon(icon, tint = palette.textOnBrand, size = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        PosText(label, PosTheme.type.labelAction.copy(fontWeight = FontWeight.Bold), palette.textOnBrand, maxLines = 1)
    }
}

/** Segmented choice (pressed = selected). */
@Composable
fun <T> PosSegmented(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val palette = PosTheme.palette
    Row(
        modifier
            .clip(PosTheme.shape.sm)
            .background(palette.canvas)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            PosText(
                label(option),
                PosTheme.type.labelAction.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium),
                if (on) palette.textPrimary else palette.textSecondary,
                align = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier
                    .clip(PosTheme.shape.sm)
                    .then(if (on) Modifier.posNeuRaisedSmall().background(palette.surfacePrimary) else Modifier)
                    .clickable(role = Role.RadioButton) { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
fun PosRowEnd(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Card surface for destination screens. */
@Composable
fun PosPanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .posNeuRaised()
            .clip(PosTheme.shape.lg)
            .background(PosTheme.palette.surfacePrimary)
            .padding(20.dp),
    ) {
        PosText(title, PosTheme.type.heading2.copy(fontWeight = FontWeight.Bold), PosTheme.palette.textPrimary, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        content()
    }
}
