package co.zw.nissangtr.pos.till

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.ui.theme.GtrColors

@Composable
fun UtilityRail(
    onSync: () -> Unit = {},
    onScan: () -> Unit = {},
    onPrint: () -> Unit = {},
    onDrawer: () -> Unit = {},
    onInfo: () -> Unit = {},
    onSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(GtrColors.Steel)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RailIcon(Icons.Filled.Sync, "Sync", onSync)
        RailIcon(Icons.Filled.QrCodeScanner, "Scan", onScan)
        RailIcon(Icons.Filled.Print, "Print", onPrint)
        RailIcon(Icons.Filled.PointOfSale, "Drawer", onDrawer)
        RailIcon(Icons.Filled.Info, "Info", onInfo)
        RailIcon(Icons.Filled.Settings, "Settings", onSettings)
    }
}

@Composable
private fun RailIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = GtrColors.Silver,
        modifier = Modifier
            .size(40.dp)
            .clickable(onClick = onClick)
            .padding(8.dp),
    )
}
