package co.zw.nissangtr.pos.lookup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.FitmentBadge
import co.zw.nissangtr.pos.api.TillItem
import co.zw.nissangtr.pos.api.VehicleLatch
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes

/** Fitment / supersession info drawer (sheet over till — not a new destination). */
@Composable
fun FitmentInfoSheet(
    item: TillItem,
    badge: FitmentBadge,
    latch: VehicleLatch?,
    onSwapSupersession: ((String) -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(GtrColors.SteelLift, GtrShapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Part info",
            style = MaterialTheme.typography.titleMedium,
            color = GtrColors.Chalk,
            fontWeight = FontWeight.Bold,
        )
        Text(item.oemPartNumber, color = GtrColors.Chalk, fontWeight = FontWeight.SemiBold)
        Text(item.description, color = GtrColors.Silver)
        FitmentChip(badge = badge)
        Text(
            text = "Chassis: ${item.chassisCodes.joinToString().ifBlank { "—" }}",
            color = GtrColors.SilverDim,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "Engine: ${item.engineCodes.joinToString().ifBlank { "—" }}",
            color = GtrColors.SilverDim,
            style = MaterialTheme.typography.bodySmall,
        )
        item.pncCode?.let {
            Text("PNC $it", color = GtrColors.SilverDim, style = MaterialTheme.typography.bodySmall)
        }
        latch?.let {
            Text(
                text = "Latch ${it.chassisCode} ${it.engineCode.orEmpty()}",
                color = GtrColors.Silver,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        item.supersededBy?.let { oem ->
            Text("Use $oem instead", color = GtrColors.Warning)
            if (onSwapSupersession != null) {
                TextButton(onClick = { onSwapSupersession(oem) }) {
                    Text("Swap to $oem", color = GtrColors.PrimaryInk)
                }
            }
        }
        Text(
            text = "Diagram thumbnail online-only (Later if offline)",
            color = GtrColors.SilverDim,
            style = MaterialTheme.typography.labelSmall,
        )
        TextButton(onClick = onDismiss) {
            Text("Close", color = GtrColors.Silver)
        }
    }
}
