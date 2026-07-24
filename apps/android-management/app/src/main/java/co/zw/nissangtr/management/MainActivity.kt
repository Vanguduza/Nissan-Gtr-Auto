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
import androidx.compose.material3.OutlinedButton
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
import co.zw.nissangtr.management.auth.AuthGate
import co.zw.nissangtr.management.auth.AuthModule
import co.zw.nissangtr.management.dispatch.DispatchModule
import co.zw.nissangtr.management.dispatch.DispatchScreen
import co.zw.nissangtr.management.hr.ClockAttendanceScreen
import co.zw.nissangtr.management.hr.HrModule
import co.zw.nissangtr.management.pos.PosModule
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcClientFactory
import co.zw.nissangtr.management.rpc.SupabaseRpcClient
import co.zw.nissangtr.management.warehouse.WarehouseModule

private enum class ManagementRoute {
    Home,
    HrClock,
    Dispatch,
}

/**
 * Management shell. Feature screens are thin scaffolds over [RpcClient]
 * ([RpcClientFactory]: Live [SupabaseRpcClient] or Fake).
 * Live requires GoTrue email/password session via [AuthGate].
 * Money/pricing: @gtr/shared. Hardware: bridges/ contracts only.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listOf(AuthModule.id, PosModule.id, WarehouseModule.id, DispatchModule.id, HrModule.id)
        val live = RpcClientFactory.isLive(
            BuildConfig.SUPABASE_URL,
            BuildConfig.SUPABASE_ANON_KEY,
            BuildConfig.RPC_FORCE_FAKE,
        )
        val rpc: RpcClient = RpcClientFactory.create(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
            forceFake = BuildConfig.RPC_FORCE_FAKE,
        )
        val supabase = rpc as? SupabaseRpcClient
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuthGate(liveRpc = live, supabase = supabase) { email, onSignOut ->
                        ManagementApp(
                            rpc = rpc,
                            liveRpc = live,
                            signedInEmail = email,
                            onSignOut = onSignOut,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementApp(
    rpc: RpcClient,
    liveRpc: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
) {
    var route by remember { mutableStateOf(ManagementRoute.Home) }
    when (route) {
        ManagementRoute.Home -> ManagementHome(
            liveRpc = liveRpc,
            signedInEmail = signedInEmail,
            onSignOut = onSignOut,
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
    liveRpc: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
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
            "Modules: ${AuthModule.id}, ${PosModule.id}, ${WarehouseModule.id}, " +
                "${DispatchModule.id}, ${HrModule.id}",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (liveRpc) "RPC: Live (supabase-kt)"
            else "RPC: Fake (set SUPABASE_URL + SUPABASE_ANON_KEY)",
            style = MaterialTheme.typography.bodySmall,
        )
        if (signedInEmail != null) {
            Text("Signed in: $signedInEmail", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text("Sign out")
            }
        } else if (!liveRpc) {
            Text("Fake mode — auth optional / bypassed", style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = onHr,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("HR — Clock in / out") }
        Button(
            onClick = onDispatch,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Logistics — Pick / DN") }
        Text(
            "Auth: GoTrue signInWith(Email). No payroll tax. Bridge-First for QR/GPS.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
