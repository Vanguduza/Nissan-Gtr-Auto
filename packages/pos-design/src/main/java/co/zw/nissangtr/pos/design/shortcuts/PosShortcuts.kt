package co.zw.nissangtr.pos.design.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import co.zw.nissangtr.pos.design.theme.PosTheme

/**
 * Keyboard shortcut definition (Blueprint §5.10 / SYS-18).
 */
data class PosShortcut(
    val keyBinding: String,
    val description: String,
)

/**
 * Standard keyboard shortcuts mapping for physical counters.
 */
object PosShortcuts {
    val defaultMap: List<PosShortcut> = listOf(
        PosShortcut("/ or Ctrl+K", "Focus search"),
        PosShortcut("F2", "Open vehicle cascade"),
        PosShortcut("F4", "Park sale"),
        PosShortcut("F8", "Proceed to payment"),
        PosShortcut("+ / −", "Adjust quantity on focused line"),
        PosShortcut("Del", "Remove focused line (with Undo)"),
        PosShortcut("Esc", "Dismiss active overlay / dialog"),
        PosShortcut("Ctrl+Z", "Undo last reversible action"),
        PosShortcut("?", "Show keyboard shortcuts"),
    )
}

/**
 * Keyboard shortcuts discoverability overlay triggered on '?' (Blueprint §5.10 / SYS-18).
 */
@Composable
fun PosKeyboardShortcutsDialog(
    onDismissRequest: () -> Unit,
    shortcuts: List<PosShortcut> = PosShortcuts.defaultMap,
) {
    val palette = PosTheme.palette
    val shape = PosTheme.shape.lg
    val elevation = PosTheme.elevation

    Dialog(onDismissRequest = onDismissRequest) {
        Box(
            modifier = Modifier
                .width(440.dp)
                .shadow(elevation.elevation3, shape)
                .clip(shape)
                .background(palette.surfacePrimary)
                .border(elevation.borderSubtleWidth, palette.borderSubtle, shape)
                .padding(PosTheme.space.space6),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PosTheme.space.space4),
            ) {
                Text(
                    text = "Keyboard Shortcuts",
                    style = PosTheme.type.heading2,
                    color = palette.textPrimary,
                )

                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(PosTheme.space.space2),
                ) {
                    items(shortcuts) { shortcut ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = shortcut.description,
                                style = PosTheme.type.bodyPrimary,
                                color = palette.textSecondary,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(PosTheme.shape.xs)
                                    .background(palette.canvas)
                                    .border(
                                        elevation.borderSubtleWidth,
                                        palette.borderSubtle,
                                        PosTheme.shape.xs,
                                    )
                                    .padding(
                                        horizontal = PosTheme.space.space2,
                                        vertical = PosTheme.space.space1,
                                    ),
                            ) {
                                Text(
                                    text = shortcut.keyBinding,
                                    style = PosTheme.type.monoReference,
                                    color = palette.textPrimary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
