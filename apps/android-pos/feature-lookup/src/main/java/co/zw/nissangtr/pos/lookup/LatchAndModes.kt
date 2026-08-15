package co.zw.nissangtr.pos.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.VehicleLatch
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes

@Composable
fun VehicleLatchChrome(
    latch: VehicleLatch?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = if (latch == null) {
        "No vehicle latched"
    } else {
        buildString {
            append(latch.chassisCode)
            latch.engineCode?.let { append(" · "); append(it) }
            latch.modelVariant?.let { append(" · "); append(it) }
        }
    }
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .background(GtrColors.SteelLift, GtrShapes.small)
            .border(1.dp, GtrColors.SilverDim, GtrShapes.small)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (latch == null) GtrColors.SilverDim else GtrColors.Chalk,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (latch != null) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Clear vehicle latch",
                    tint = GtrColors.Silver,
                )
            }
        }
    }
}

@Composable
fun FinderModesRow(
    selected: FinderMode,
    onSelect: (FinderMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FinderMode.entries.forEach { mode ->
            val selectedMode = mode == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selectedMode) GtrColors.Chalk else GtrColors.SilverDim,
                )
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(0.7f)
                        .heightIn(min = 2.dp)
                        .background(
                            if (selectedMode) GtrColors.Primary else GtrColors.Steel,
                        ),
                )
            }
        }
    }
}
