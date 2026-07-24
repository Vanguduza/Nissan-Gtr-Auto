package co.zw.nissangtr.management

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.management.dispatch.DispatchModule
import co.zw.nissangtr.management.dispatch.DispatchScreen
import co.zw.nissangtr.management.hr.ClockAttendanceScreen
import co.zw.nissangtr.management.hr.HrModule
import co.zw.nissangtr.management.pos.PosModule
import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.warehouse.WarehouseModule

private enum class ManagementRoute {
    Home,
    HrClock,
    Dispatch,
}

/**
 * Management shell. Feature screens are thin scaffolds over [RpcClient]
 * (FakeRpcClient until Supabase Kotlin SDK is wired).
 * Money/pricing: @gtr/shared. Hardware: bridges/ contracts only.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep placeholder modules on the compile classpath.
        listOf(PosModule.id, WarehouseModule.id, DispatchModule.id, HrModule.id)
        // TODO(live): build SupabaseRpcClient from BuildConfig.SUPABASE_URL / ANON_KEY
        val rpc: RpcClient = FakeRpcClient()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ManagementApp(rpc = rpc)
                }
            }
        }
    }
}

@Composable
private fun ManagementApp(rpc: RpcClient) {
    var route by remember { mutableStateOf(ManagementRoute.Home) }
    when (route) {
        ManagementRoute.Home -> ManagementHome(
            onHr = { route = ManagementRoute.HrClock },
            onDispatch = { route = ManagementRoute.Dispatch },
        )
        ManagementRoute.HrClock -> ClockAttendanceScreen(
            rpc = rpc,
            onBack = { route = ManagementRoute.Home },
        )
        ManagementRoute.Dispatch -> DispatchScreen(
            rpc = rpc,
            onBack = { route = ManagementRoute.Home },
        )
    }
}

@Composable
private fun ManagementHome(
    onHr: () -> Unit,
    onDispatch: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nissan GTR Auto", style = MaterialTheme.typography.headlineMedium)
        Text("Management app", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Modules: ${PosModule.id}, ${WarehouseModule.id}, ${DispatchModule.id}, ${HrModule.id}",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = onHr,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("HR — Clock in / out") }
        Button(
            onClick = onDispatch,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Logistics — Pick / DN") }
        Text(
            "RPC: FakeRpcClient stub (see core:rpc). No payroll tax. Bridge-First for QR/GPS.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
