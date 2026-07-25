package co.zw.nissangtr.management

import android.content.Intent
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.bridges.escpos.BluetoothEscPosPrinterBridge
import co.zw.nissangtr.bridges.escpos.EscPosPrinterBridge
import co.zw.nissangtr.bridges.qr.CameraxQrScannerBridge
import co.zw.nissangtr.bridges.qr.QrScannerBridge
import co.zw.nissangtr.management.auth.AuthGate
import co.zw.nissangtr.management.auth.AuthModule
import co.zw.nissangtr.management.chat.ChatModule
import co.zw.nissangtr.management.chat.ChatScreen
import co.zw.nissangtr.management.dispatch.DispatchModule
import co.zw.nissangtr.management.dispatch.DispatchScreen
import co.zw.nissangtr.management.hr.ClockAttendanceScreen
import co.zw.nissangtr.management.hr.HrModule
import co.zw.nissangtr.management.pos.PosModule
import co.zw.nissangtr.management.pos.PosScreen
import co.zw.nissangtr.management.rpc.ChatStaffRoles
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcClientFactory
import co.zw.nissangtr.management.rpc.SupabaseRpcClient
import co.zw.nissangtr.management.warehouse.WarehouseModule
import co.zw.nissangtr.management.warehouse.WarehouseScreen

private enum class ManagementRoute {
    Home,
    HrClock,
    Dispatch,
    Pos,
    Warehouse,
    Chat,
}

/**
 * Management shell. Feature screens are thin scaffolds over [RpcClient]
 * ([RpcClientFactory]: Live [SupabaseRpcClient] or Fake).
 * Live requires GoTrue email/password session via [AuthGate].
 * Money/pricing: @gtr/shared. Hardware: bridges/ only (QR / ESC/POS).
 *
 * Driver GPS FGS producer removed — sole producer is `apps/android-delivery`
 * (Bridge-First location-tracker). Staff VIEW live last-point / ETA in dispatch.
 *
 * Bridges: [CameraxQrScannerBridge], [BluetoothEscPosPrinterBridge] —
 * Activity attachment + permission / scan results.
 */
class MainActivity : ComponentActivity() {

    private lateinit var qrBridge: CameraxQrScannerBridge
    private lateinit var printerBridge: BluetoothEscPosPrinterBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        qrBridge = CameraxQrScannerBridge(this)
        printerBridge = BluetoothEscPosPrinterBridge(this)
        listOf(
            AuthModule.id,
            PosModule.id,
            WarehouseModule.id,
            DispatchModule.id,
            HrModule.id,
            ChatModule.id,
        )
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
                            qr = qrBridge,
                            printer = printerBridge,
                            liveRpc = live,
                            signedInEmail = email,
                            onSignOut = onSignOut,
                            supportPhone = BuildConfig.DELIVERY_SUPPORT_PHONE,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::qrBridge.isInitialized) qrBridge.attachActivity(this)
        if (::printerBridge.isInitialized) printerBridge.attachActivity(this)
    }

    override fun onPause() {
        if (::qrBridge.isInitialized) qrBridge.detachActivity()
        if (::printerBridge.isInitialized) printerBridge.detachActivity()
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CameraxQrScannerBridge.REQUEST_CAMERA,
            -> if (::qrBridge.isInitialized) qrBridge.onPermissionResult()
            BluetoothEscPosPrinterBridge.REQUEST_BLUETOOTH,
            -> if (::printerBridge.isInitialized) printerBridge.onPermissionResult()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CameraxQrScannerBridge.REQUEST_SCAN && ::qrBridge.isInitialized) {
            qrBridge.onScanActivityResult(resultCode, data)
        }
    }
}

@Composable
private fun ManagementApp(
    rpc: RpcClient,
    qr: QrScannerBridge,
    printer: EscPosPrinterBridge,
    liveRpc: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
    supportPhone: String,
) {
    var route by remember { mutableStateOf(ManagementRoute.Home) }
    var showChat by remember { mutableStateOf(!liveRpc) }

    LaunchedEffect(liveRpc, signedInEmail) {
        if (!liveRpc) {
            showChat = true
            return@LaunchedEffect
        }
        showChat = runCatching {
            ChatStaffRoles.allows(rpc.listMyStaffRoles())
        }.getOrDefault(false)
    }

    when (route) {
        ManagementRoute.Home -> ManagementHome(
            liveRpc = liveRpc,
            signedInEmail = signedInEmail,
            onSignOut = onSignOut,
            showChat = showChat,
            onHr = { route = ManagementRoute.HrClock },
            onDispatch = { route = ManagementRoute.Dispatch },
            onPos = { route = ManagementRoute.Pos },
            onWarehouse = { route = ManagementRoute.Warehouse },
            onChat = { route = ManagementRoute.Chat },
        )
        ManagementRoute.HrClock -> ClockAttendanceScreen(
            rpc = rpc,
            onBack = { route = ManagementRoute.Home },
        )
        ManagementRoute.Dispatch -> DispatchScreen(
            rpc = rpc,
            supportPhone = supportPhone,
            onBack = { route = ManagementRoute.Home },
        )
        ManagementRoute.Pos -> PosScreen(
            rpc = rpc,
            qr = qr,
            printer = printer,
            onBack = { route = ManagementRoute.Home },
        )
        ManagementRoute.Warehouse -> WarehouseScreen(
            rpc = rpc,
            qr = qr,
            onBack = { route = ManagementRoute.Home },
        )
        ManagementRoute.Chat -> ChatScreen(
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
    showChat: Boolean,
    onHr: () -> Unit,
    onDispatch: () -> Unit,
    onPos: () -> Unit,
    onWarehouse: () -> Unit,
    onChat: () -> Unit,
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
                "${DispatchModule.id}, ${HrModule.id}, ${ChatModule.id}",
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
            onClick = onPos,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("POS — Cart / Checkout") }
        Button(
            onClick = onWarehouse,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Warehouse — Receive / Transfer / Cycle") }
        Button(
            onClick = onHr,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("HR — Clock in / out") }
        Button(
            onClick = onDispatch,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Logistics — Pick / DN / Dispatch") }
        if (showChat) {
            Button(
                onClick = onChat,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Chat — Staff inbox") }
        } else if (liveRpc) {
            Text(
                "Chat hidden — needs staff role admin|sales|warehouse",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(
            "Auth: GoTrue signInWith(Email). No ZIMRA / payroll tax. " +
                "Bridge-First for QR/printer. Driver GPS: delivery app only. Money: USD|ZIG.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
