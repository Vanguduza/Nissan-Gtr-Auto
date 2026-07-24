package co.zw.nissangtr.management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.management.dispatch.DispatchModule
import co.zw.nissangtr.management.pos.PosModule
import co.zw.nissangtr.management.warehouse.WarehouseModule

/**
 * Management shell scaffold. Feature modules are placeholders only.
 * Money/pricing: @gtr/shared. Hardware: bridges/ contracts.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Touch placeholders so modules stay on the compile classpath.
        listOf(PosModule.id, WarehouseModule.id, DispatchModule.id)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ManagementHome()
                }
            }
        }
    }
}

@Composable
private fun ManagementHome() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nissan GTR Auto", style = MaterialTheme.typography.headlineMedium)
        Text("Management app scaffold", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Modules: ${PosModule.id}, ${WarehouseModule.id}, ${DispatchModule.id}",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
