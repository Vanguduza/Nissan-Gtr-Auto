package co.zw.nissangtr.pos.till

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.ChassisShortcut
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes

@Composable
fun ChassisShortcutChips(
    chips: List<ChassisShortcut>,
    selectedChassis: String?,
    onSelect: (ChassisShortcut) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            val selected = selectedChassis.equals(chip.chassisCode, ignoreCase = true)
            Text(
                text = chip.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) GtrColors.PrimaryInk else GtrColors.Chalk,
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .background(
                        if (selected) GtrColors.Primary else GtrColors.SteelLift,
                        GtrShapes.small,
                    )
                    .clickable { onSelect(chip) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
