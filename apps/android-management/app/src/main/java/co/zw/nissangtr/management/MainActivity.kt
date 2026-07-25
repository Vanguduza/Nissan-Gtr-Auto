package co.zw.nissangtr.management

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import co.zw.nissangtr.management.credit.CreditModule
import co.zw.nissangtr.management.credit.CreditScreen
import co.zw.nissangtr.management.dispatch.DispatchModule
import co.zw.nissangtr.management.dispatch.DispatchScreen
import co.zw.nissangtr.management.fleet.FleetModule
import co.zw.nissangtr.management.fleet.FleetScreen
import co.zw.nissangtr.management.hr.ClockAttendanceScreen
import co.zw.nissangtr.management.hr.HrModule
import co.zw.nissangtr.management.pos.PosModule
import co.zw.nissangtr.management.pos.PosScreen
import co.zw.nissangtr.management.procurement.BlanketsScreen
import co.zw.nissangtr.management.procurement.ProcurementModule
import co.zw.nissangtr.management.rpc.ChatStaffRoles
import co.zw.nissangtr.management.rpc.CreditStaffRoles
import co.zw.nissangtr.management.rpc.FleetStaffRoles
import co.zw.nissangtr.management.rpc.ManagementHomeRoles
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcClientFactory
import co.zw.nissangtr.management.rpc.SupabaseRpcClient
import co.zw.nissangtr.management.warehouse.BinsScreen
import co.zw.nissangtr.management.warehouse.ConsignmentScreen
import co.zw.nissangtr.management.warehouse.WarehouseModule
import co.zw.nissangtr.management.warehouse.WarehouseScreen

/** Top-level hub modules — each opens a sub-feature menu (or a single feature). */
private enum class HubModule(val title: String) {
    Pos("POS"),
    Warehouse("Warehouse"),
    Procurement("Procurement"),
    Crm("CRM"),
    Hr("HR"),
    Logistics("Logistics"),
    Fleet("Company fleet"),
    Chat("Chat"),
}

private enum class ManagementRoute {
    Home,
    ModuleMenu,
    HrClock,
    Dispatch,
    Fleet,
    Pos,
    Warehouse,
    Bins,
    Consignment,
    Blankets,
    Credit,
    Chat,
}

/**
 * Management shell. Feature screens are thin scaffolds over [RpcClient]
 * ([RpcClientFactory]: Live [SupabaseRpcClient] or Fake).
 * Live requires GoTrue email/password session via [AuthGate].
 *
 * Hub navigation is hierarchical: module list → sub-features → screen.
 *
 * **Sales role** → default home is POS-dedicated workspace (standalone till).
 * **Admin / warehouse** → hub remains home; POS available from hub.
 *
 * Money/pricing: @gtr/shared. Hardware: bridges/ only (QR / ESC/POS).
 * No ZIMRA. No HTML5 QR.
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
            ProcurementModule.id,
            CreditModule.id,
            FleetModule.id,
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
    var route by remember { mutableStateOf<ManagementRoute?>(null) }
    var openModule by remember { mutableStateOf<HubModule?>(null) }
    var showChat by remember { mutableStateOf(!liveRpc) }
    var showCredit by remember { mutableStateOf(!liveRpc) }
    var showFleet by remember { mutableStateOf(!liveRpc) }
    var salesHome by remember { mutableStateOf(false) }

    fun goHome() {
        openModule = null
        route = ManagementRoute.Home
    }

    fun openModuleMenu(module: HubModule) {
        openModule = module
        route = ManagementRoute.ModuleMenu
    }

    fun backFromFeature() {
        val parent = openModule
        route = if (parent != null) ManagementRoute.ModuleMenu else ManagementRoute.Home
    }

    LaunchedEffect(liveRpc, signedInEmail) {
        val roles = if (!liveRpc) {
            showChat = true
            showCredit = true
            showFleet = true
            rpc.listMyStaffRoles()
        } else {
            val r = runCatching { rpc.listMyStaffRoles() }.getOrDefault(emptyList())
            showChat = ChatStaffRoles.allows(r)
            showCredit = CreditStaffRoles.allows(r)
            showFleet = FleetStaffRoles.allows(r)
            r
        }
        salesHome = ManagementHomeRoles.prefersPosHome(roles)
        openModule = null
        route = if (salesHome) ManagementRoute.Pos else ManagementRoute.Home
    }

    when (route) {
        null -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Loading roles…", style = MaterialTheme.typography.bodyMedium)
        }
        ManagementRoute.Home -> ManagementHome(
            liveRpc = liveRpc,
            signedInEmail = signedInEmail,
            onSignOut = onSignOut,
            showChat = showChat,
            showCredit = showCredit,
            showFleet = showFleet,
            onOpenModule = ::openModuleMenu,
        )
        ManagementRoute.ModuleMenu -> {
            val module = openModule
            if (module == null) {
                LaunchedEffect(Unit) { goHome() }
            } else {
                BackHandler { goHome() }
                ModuleSubMenu(
                    module = module,
                    onBack = ::goHome,
                    onOpenFeature = { feature ->
                        openModule = module
                        route = feature
                    },
                )
            }
        }
        ManagementRoute.HrClock -> {
            BackHandler { backFromFeature() }
            ClockAttendanceScreen(
                rpc = rpc,
                onBack = ::backFromFeature,
            )
        }
        ManagementRoute.Dispatch -> {
            BackHandler { backFromFeature() }
            DispatchScreen(
                rpc = rpc,
                supportPhone = supportPhone,
                onBack = ::backFromFeature,
            )
        }
        ManagementRoute.Fleet -> {
            BackHandler { backFromFeature() }
            FleetScreen(
                rpc = rpc,
                onBack = ::backFromFeature,
            )
        }
        ManagementRoute.Pos -> {
            BackHandler {
                if (openModule != null) backFromFeature() else goHome()
            }
            PosScreen(
                rpc = rpc,
                qr = qr,
                printer = printer,
                isSalesHome = salesHome,
                onOpenHub = ::goHome,
                onBack = {
                    if (openModule != null) backFromFeature() else goHome()
                },
            )
        }
        ManagementRoute.Warehouse -> {
            BackHandler { backFromFeature() }
            WarehouseScreen(
                rpc = rpc,
                qr = qr,
                onBack = ::backFromFeature,
            )
        }
        ManagementRoute.Bins -> {
            BackHandler { backFromFeature() }
            BinsScreen(
                rpc = rpc,
                printer = printer,
                onBack = ::backFromFeature,
            )
        }
        ManagementRoute.Consignment -> {
            BackHandler { backFromFeature() }
            ConsignmentScreen(
                rpc = rpc,
                onBack = ::backFromFeature,
            )
        }
        ManagementRoute.Blankets -> {
            BackHandler { backFromFeature() }
            BlanketsScreen(
                rpc = rpc,
                onBack = ::backFromFeature,
            )
        }
        ManagementRoute.Credit -> {
            BackHandler { backFromFeature() }
            CreditScreen(
                rpc = rpc,
                onBack = ::backFromFeature,
            )
        }
        ManagementRoute.Chat -> {
            BackHandler { backFromFeature() }
            ChatScreen(
                rpc = rpc,
                onBack = ::backFromFeature,
            )
        }
    }
}

@Composable
private fun ManagementHome(
    liveRpc: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
    showChat: Boolean,
    showCredit: Boolean,
    showFleet: Boolean,
    onOpenModule: (HubModule) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nissan GTR Auto", style = MaterialTheme.typography.headlineMedium)
        Text("Management hub", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Modules: ${AuthModule.id}, ${PosModule.id}, ${WarehouseModule.id}, " +
                "${ProcurementModule.id}, ${CreditModule.id}, ${DispatchModule.id}, " +
                "${FleetModule.id}, ${HrModule.id}, ${ChatModule.id}",
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

        Text(
            "Select a module",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        HubModuleButton(HubModule.Pos, onOpenModule)
        HubModuleButton(HubModule.Warehouse, onOpenModule)
        HubModuleButton(HubModule.Procurement, onOpenModule)
        if (showCredit) {
            HubModuleButton(HubModule.Crm, onOpenModule)
        } else if (liveRpc) {
            Text(
                "CRM hidden — needs staff role admin|sales|finance",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        HubModuleButton(HubModule.Hr, onOpenModule)
        HubModuleButton(HubModule.Logistics, onOpenModule)
        if (showFleet) {
            HubModuleButton(HubModule.Fleet, onOpenModule)
        } else if (liveRpc) {
            Text(
                "Fleet hidden — needs staff role admin|warehouse|dispatcher",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (showChat) {
            HubModuleButton(HubModule.Chat, onOpenModule)
        } else if (liveRpc) {
            Text(
                "Chat hidden — needs staff role admin|sales|warehouse",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            "Sales-only users land on POS. Admin/warehouse keep this hub. " +
                "No ZIMRA / payroll tax. Bridge-First QR/printer. Money: USD|ZIG.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun HubModuleButton(
    module: HubModule,
    onOpenModule: (HubModule) -> Unit,
) {
    Button(
        onClick = { onOpenModule(module) },
        modifier = Modifier.fillMaxWidth(),
    ) { Text(module.title) }
}

@Composable
private fun ModuleSubMenu(
    module: HubModule,
    onBack: () -> Unit,
    onOpenFeature: (ManagementRoute) -> Unit,
) {
    val features = featuresFor(module)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(module.title, style = MaterialTheme.typography.headlineMedium)
        Text("Sub-features", style = MaterialTheme.typography.bodyMedium)
        features.forEach { (label, feature) ->
            Button(
                onClick = { onOpenFeature(feature) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(label) }
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to modules")
        }
    }
}

/** Labels + routes for each hub module (existing screens only). */
private fun featuresFor(module: HubModule): List<Pair<String, ManagementRoute>> = when (module) {
    HubModule.Pos -> listOf(
        "Sales till" to ManagementRoute.Pos,
    )
    HubModule.Warehouse -> listOf(
        "Receive / Transfer / Cycle" to ManagementRoute.Warehouse,
        "Bins — Locations / pick-path / labels" to ManagementRoute.Bins,
        "Consignment — Draft / submit" to ManagementRoute.Consignment,
    )
    HubModule.Procurement -> listOf(
        "Blanket POs" to ManagementRoute.Blankets,
    )
    HubModule.Crm -> listOf(
        "B2B credit" to ManagementRoute.Credit,
    )
    HubModule.Hr -> listOf(
        "Clock in / out" to ManagementRoute.HrClock,
    )
    HubModule.Logistics -> listOf(
        // Live track + panic inbox live on this screen (no separate Android routes).
        "Pick / DN / Dispatch (track & panic)" to ManagementRoute.Dispatch,
    )
    HubModule.Fleet -> listOf(
        "Plates / status" to ManagementRoute.Fleet,
    )
    HubModule.Chat -> listOf(
        "Staff inbox" to ManagementRoute.Chat,
    )
}
