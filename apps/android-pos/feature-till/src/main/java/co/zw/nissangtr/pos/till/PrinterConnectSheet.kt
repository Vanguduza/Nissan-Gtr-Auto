package co.zw.nissangtr.pos.till

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrShapes

/** One system-bonded Bluetooth printer row (address = MAC). */
data class PrinterDeviceRow(
    val name: String,
    val address: String,
)

/** Status line for Connect printer (Connected · MAC · last error). */
data class PrinterConnectUiState(
    val connected: Boolean = false,
    val mac: String? = null,
    val lastError: String? = null,
    val bonded: List<PrinterDeviceRow> = emptyList(),
    val busy: Boolean = false,
    val message: String? = null,
) {
    val statusLine: String
        get() {
            val conn = if (connected) "Connected" else "Disconnected"
            val macPart = mac?.takeIf { it.isNotBlank() } ?: "no MAC"
            return "$conn · $macPart"
        }
}

/**
 * Utilities → Connect printer. Lists system-bonded BT devices, selects MAC
 * (persisted by bridge prefs), connects RFCOMM ESC/POS. Pair new printers in
 * Android Bluetooth settings first — Bridge-First, no Web Bluetooth.
 */
@Composable
fun PrinterConnectSheet(
    state: PrinterConnectUiState,
    onRefreshBonded: () -> Unit,
    onSelectDevice: (PrinterDeviceRow) -> Unit,
    onConnect: () -> Unit,
    onOpenFloat: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 440.dp)
            .fillMaxWidth()
            .background(GtrColors.SteelLift, GtrShapes.medium)
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Till utilities",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = GtrColors.Chalk,
        )
        Text(
            text = "Connect printer",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = GtrColors.Chalk,
        )
        Text(
            text = state.statusLine,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.connected) GtrColors.StockIn else GtrColors.SilverDim,
        )
        state.message?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = GtrColors.Silver)
        }
        state.lastError?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = GtrColors.Danger)
        }
        Text(
            text = "Pair the thermal printer in Android Bluetooth settings, then select it here.",
            style = MaterialTheme.typography.labelSmall,
            color = GtrColors.SilverDim,
        )
        OutlinedButton(
            onClick = onRefreshBonded,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = GtrShapes.small,
        ) {
            Text("List bonded printers", color = GtrColors.Chalk)
        }
        if (state.bonded.isEmpty()) {
            Text(
                text = "No bonded devices yet",
                style = MaterialTheme.typography.bodySmall,
                color = GtrColors.SilverDim,
            )
        } else {
            state.bonded.forEach { device ->
                val selected = device.address.equals(state.mac, ignoreCase = true)
                Text(
                    text = "${device.name} · ${device.address}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) GtrColors.Primary else GtrColors.Chalk,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(enabled = !state.busy) { onSelectDevice(device) }
                        .padding(vertical = 12.dp),
                )
            }
        }
        Button(
            onClick = onConnect,
            enabled = !state.busy && !state.mac.isNullOrBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = GtrShapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = GtrColors.Primary,
                contentColor = GtrColors.PrimaryInk,
            ),
        ) {
            Text(
                if (state.connected) "Reconnect printer" else "Connect printer",
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = "Till float",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = GtrColors.Chalk,
            modifier = Modifier.padding(top = 8.dp),
        )
        OutlinedButton(
            onClick = onOpenFloat,
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            shape = GtrShapes.small,
        ) {
            Text("Open / close float (1120)", color = GtrColors.Chalk)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onDismiss) {
                Text("Done", color = GtrColors.Silver)
            }
        }
    }
}
