package co.zw.nissangtr.management

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopSecondaryButton
import co.zw.nissangtr.ui.shop.ShopSectionHeader
import co.zw.nissangtr.ui.shop.ShopStaffScreen
import co.zw.nissangtr.ui.shop.ShopTheme
import co.zw.nissangtr.ui.shop.StaffModuleTile
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import co.zw.nissangtr.bridges.biometricphoto.CameraxBiometricPhotoBridge
import co.zw.nissangtr.bridges.escpos.AndroidDocumentPrinterBridge
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
import co.zw.nissangtr.management.hr.HrOnboardingScreen
import co.zw.nissangtr.management.kiosk.DeviceAdminConsoleScreen
import co.zw.nissangtr.management.kiosk.IdleSessionHost
import co.zw.nissangtr.management.kiosk.KioskDevicePrefs
import co.zw.nissangtr.management.kiosk.KioskModule
import co.zw.nissangtr.management.kiosk.LockTaskController
import co.zw.nissangtr.management.pos.PosModule
import co.zw.nissangtr.management.pos.PosScreen
import co.zw.nissangtr.management.pos.StaffOfflineEpcBrowseScreen
import co.zw.nissangtr.management.procurement.BlanketsScreen
import co.zw.nissangtr.management.procurement.ProcurementModule
import co.zw.nissangtr.management.rpc.ChatStaffRoles
import co.zw.nissangtr.management.rpc.CreditStaffRoles
import co.zw.nissangtr.management.rpc.FleetStaffRoles
import co.zw.nissangtr.management.rpc.HrOnboardingStaffRoles
import co.zw.nissangtr.management.rpc.ManagementHomeLanding
import co.zw.nissangtr.management.rpc.ManagementHomeRoles
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.RpcClientFactory
import co.zw.nissangtr.management.rpc.SupabaseRpcClient
import co.zw.nissangtr.management.warehouse.BinsScreen
import co.zw.nissangtr.management.warehouse.ConsignmentScreen
import co.zw.nissangtr.management.warehouse.WarehouseModule
import co.zw.nissangtr.management.warehouse.WarehouseScreen
import kotlinx.coroutines.launch

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
    RoleDenied,
    ModuleMenu,
    HrClock,
    HrOnboarding,
    Dispatch,
    Fleet,
    Pos,
    EpcBrowse,
    Warehouse,
    Bins,
    Consignment,
    Blankets,
    Credit,
    Chat,
    DeviceAdmin,
}

/**
 * Management shell. Feature screens are thin scaffolds over [RpcClient]
 * ([RpcClientFactory]: Live [SupabaseRpcClient] or Fake).
 * Live requires GoTrue email/password session via [AuthGate].
 *
 * **Sales-only** → POS. **Warehouse / finance / HR / admin / dispatcher** → hub.
 * Empty / unknown roles → fail closed (deny). Tablet flavor: Lock Task + idle + Device Admin.
 *
 * Money/pricing: @gtr/shared. Hardware: bridges/ only (QR / ESC/POS).
 * No ZIMRA. No HTML5 QR.
 */
class MainActivity : ComponentActivity() {

    private lateinit var qrBridge: CameraxQrScannerBridge
    private lateinit var printerBridge: BluetoothEscPosPrinterBridge
    private lateinit var documentPrinterBridge: AndroidDocumentPrinterBridge
    private lateinit var biometricPhotoBridge: CameraxBiometricPhotoBridge
    private lateinit var lockTask: LockTaskController
    private lateinit var kioskPrefs: KioskDevicePrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tabletKiosk = BuildConfig.IS_TABLET_KIOSK
        if (tabletKiosk) {
            applyImmersiveChrome()
        }
        qrBridge = CameraxQrScannerBridge(this)
        printerBridge = BluetoothEscPosPrinterBridge(this)
        documentPrinterBridge = AndroidDocumentPrinterBridge(this)
        biometricPhotoBridge = CameraxBiometricPhotoBridge(this)
        lockTask = LockTaskController(this, tabletKiosk)
        kioskPrefs = KioskDevicePrefs(applicationContext)
        listOf(
            AuthModule.id,
            KioskModule.id,
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
        if (tabletKiosk) {
            lockTask.enterLockTaskIfAllowed()
        }
        setContent {
            ShopTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AuthGate(
                        liveRpc = live,
                        supabase = supabase,
                        allowFakeSkip = !tabletKiosk,
                        showFakeLogin = tabletKiosk,
                        forceFreshSignIn = tabletKiosk,
                    ) { email, onSignOut ->
                        IdleSessionHost(
                            prefs = kioskPrefs,
                            enabled = tabletKiosk,
                            liveRpc = live,
                            supabase = supabase,
                            signedInEmail = email,
                            onIdleLock = {
                                // Clear in-memory nav; overlay handles reauth — stay in Lock Task.
                            },
                        ) {
                            ManagementApp(
                                rpc = rpc,
                                qr = qrBridge,
                                printer = printerBridge,
                                documentPrinter = documentPrinterBridge,
                                biometricPhoto = biometricPhotoBridge,
                                liveRpc = live,
                                signedInEmail = email,
                                onSignOut = onSignOut,
                                supportPhone = BuildConfig.DELIVERY_SUPPORT_PHONE,
                                tabletKiosk = tabletKiosk,
                                kioskPrefs = kioskPrefs,
                                lockTask = lockTask,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun applyImmersiveChrome() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onResume() {
        super.onResume()
        if (::qrBridge.isInitialized) qrBridge.attachActivity(this)
        if (::printerBridge.isInitialized) printerBridge.attachActivity(this)
        if (::documentPrinterBridge.isInitialized) documentPrinterBridge.attachActivity(this)
        if (::biometricPhotoBridge.isInitialized) biometricPhotoBridge.attachActivity(this)
        if (::lockTask.isInitialized && BuildConfig.IS_TABLET_KIOSK) {
            lockTask.enterLockTaskIfAllowed()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && BuildConfig.IS_TABLET_KIOSK) {
            applyImmersiveChrome()
            if (::lockTask.isInitialized) lockTask.enterLockTaskIfAllowed()
        }
    }

    override fun onPause() {
        if (::qrBridge.isInitialized) qrBridge.detachActivity()
        if (::printerBridge.isInitialized) printerBridge.detachActivity()
        if (::documentPrinterBridge.isInitialized) documentPrinterBridge.detachActivity()
        if (::biometricPhotoBridge.isInitialized) biometricPhotoBridge.detachActivity()
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
            CameraxBiometricPhotoBridge.REQUEST_CAMERA,
            -> if (::biometricPhotoBridge.isInitialized) biometricPhotoBridge.onPermissionResult()
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
        if (requestCode == CameraxBiometricPhotoBridge.REQUEST_CAPTURE &&
            ::biometricPhotoBridge.isInitialized
        ) {
            biometricPhotoBridge.onCaptureActivityResult(resultCode, data)
        }
    }
}

@Composable
private fun ManagementApp(
    rpc: RpcClient,
    qr: QrScannerBridge,
    printer: EscPosPrinterBridge,
    documentPrinter: co.zw.nissangtr.bridges.escpos.DocumentPrinterBridge,
    biometricPhoto: CameraxBiometricPhotoBridge,
    liveRpc: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
    supportPhone: String,
    tabletKiosk: Boolean,
    kioskPrefs: KioskDevicePrefs,
    lockTask: LockTaskController,
) {
    var route by remember { mutableStateOf<ManagementRoute?>(null) }
    var openModule by remember { mutableStateOf<HubModule?>(null) }
    var showChat by remember { mutableStateOf(!liveRpc) }
    var showCredit by remember { mutableStateOf(!liveRpc) }
    var showFleet by remember { mutableStateOf(!liveRpc) }
    var salesHome by remember { mutableStateOf(false) }
    var moduleAccess by remember { mutableStateOf<List<String>>(emptyList()) }
    var staffRoles by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDeviceAdmin by remember { mutableStateOf(false) }
    var staffPortalActive by remember { mutableStateOf(false) }
    var staffPortalLoginOpen by remember { mutableStateOf(false) }
    var staffPortalIdentifier by remember { mutableStateOf("") }
    var staffPortalPassword by remember { mutableStateOf("") }
    var staffPortalBusy by remember { mutableStateOf(false) }
    var staffPortalError by remember { mutableStateOf<String?>(null) }
    val appScope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val staffPortalGuard = remember(appContext) {
        appContext.getSharedPreferences("gtr_pos_staff_portal_guard", android.content.Context.MODE_PRIVATE)
    }

    // A staff elevation is intentionally process-scoped. If the process dies while elevated,
    // persisted GoTrue state may belong to the elevated staff account; fail closed to a fresh login.
    LaunchedEffect(liveRpc) {
        if (liveRpc && staffPortalGuard.getBoolean("elevation_in_progress", false)) {
            runCatching { (rpc as? SupabaseRpcClient)?.signOut() }
            staffPortalGuard.edit().putBoolean("elevation_in_progress", false).apply()
            staffPortalActive = false
            route = null
        }
    }

    fun returnToPosFromStaffPortal() {
        appScope.launch {
            staffPortalBusy = true
            val restored = runCatching { (rpc as? SupabaseRpcClient)?.endStaffPortalSession() }
            if (restored.isFailure && liveRpc) {
                // Never leave a possibly elevated persisted session behind after restore failure.
                runCatching { (rpc as? SupabaseRpcClient)?.signOut() }
                staffPortalError = "POS session restore failed; sign in again"
            }
            staffPortalGuard.edit().putBoolean("elevation_in_progress", false).apply()
            staffPortalActive = false
            staffPortalBusy = false
            openModule = null
            salesHome = true
            route = if (restored.isSuccess || !liveRpc) ManagementRoute.Pos else null
        }
    }

    fun goHome() {
        openModule = null
        route = if (salesHome) ManagementRoute.Pos else ManagementRoute.Home
    }

    fun openModuleMenu(module: HubModule) {
        openModule = module
        route = ManagementRoute.ModuleMenu
    }

    fun backFromFeature() {
        val parent = openModule
        route = if (parent != null) ManagementRoute.ModuleMenu else {
            if (salesHome) ManagementRoute.Pos else ManagementRoute.Home
        }
    }

    LaunchedEffect(liveRpc, signedInEmail, staffPortalActive) {
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
        staffRoles = roles
        moduleAccess = runCatching { rpc.listMyModuleAccess() }.getOrDefault(emptyList())
        showDeviceAdmin = tabletKiosk &&
            ManagementHomeRoles.normalize(roles).any { it == "admin" }
        openModule = null

        if (staffPortalActive) {
            salesHome = false
            route = ManagementRoute.Home
            return@LaunchedEffect
        }
        val defaultLanding = runCatching { rpc.myDefaultLanding() }.getOrNull()
        when (ManagementHomeRoles.resolveLanding(roles, defaultLanding)) {
            ManagementHomeLanding.Deny -> {
                // Fail closed: empty / unknown roles never open hub or POS.
                salesHome = false
                route = ManagementRoute.RoleDenied
            }
            ManagementHomeLanding.Pos -> {
                salesHome = true
                route = ManagementRoute.Pos
            }
            ManagementHomeLanding.Hub -> {
                salesHome = false
                route = ManagementRoute.Home
            }
        }
    }

    when (route) {
        null -> ShopStaffScreen(
            title = "Nissan GTR Auto",
            subtitle = "Staff",
            showLogo = true,
        ) {
            Text("Loading roles…", style = MaterialTheme.typography.bodyMedium)
        }
        ManagementRoute.RoleDenied -> RoleDeniedScreen(onSignOut = onSignOut)
        ManagementRoute.Home -> {
            if (staffPortalActive) BackHandler { returnToPosFromStaffPortal() }
            ManagementHome(
                signedInEmail = signedInEmail,
                onSignOut = onSignOut,
                staffPortalActive = staffPortalActive,
                onReturnToPos = ::returnToPosFromStaffPortal,
                showChat = showChat,
                showCredit = showCredit,
                showFleet = showFleet,
                staffRoles = staffRoles,
                moduleAccess = moduleAccess,
                onOpenModule = ::openModuleMenu,
            )
        }
        ManagementRoute.DeviceAdmin -> {
            BackHandler { goHome() }
            var printerDiagnostics by remember {
                mutableStateOf(
                    "ESC/POS — MAC=${printer.getConfiguredPrinterAddress() ?: "unset"}",
                )
            }
            var scannerDiagnostics by remember {
                mutableStateOf("QR scanner (CameraX) — Bridge-First only.")
            }
            var diagnosticsBusy by remember { mutableStateOf(false) }
            val diagScope = rememberCoroutineScope()
            fun refreshBridgeDiagnostics() {
                diagScope.launch {
                    diagnosticsBusy = true
                    printerDiagnostics = runCatching {
                        val transport = printer.getConfiguredTransport()
                        val connected = runCatching { printer.isConnected() }.getOrDefault(false)
                        if (transport == co.zw.nissangtr.bridges.escpos.PrinterTransport.WIFI) {
                            val host = printer.getConfiguredNetworkHost() ?: "unset"
                            "ESC/POS · Wi-Fi/LAN · $host:${printer.getConfiguredNetworkPort()} · connected=$connected"
                        } else {
                            val mac = printer.getConfiguredPrinterAddress() ?: "unset"
                            val bt = printer.getBluetoothPermissionStatus()
                            val bonded = runCatching { printer.listBondedDevices() }.getOrDefault(emptyList())
                            "ESC/POS · Bluetooth · MAC=$mac · BT=$bt · bonded=${bonded.size} · connected=$connected" +
                                if (bonded.isNotEmpty()) " · " + bonded.take(3).joinToString { "${it.name}:${it.address}" } else ""
                        }
                    }.getOrElse { "ESC/POS diagnostics failed: ${it.message}" }
                    scannerDiagnostics = runCatching {
                        val cam = qr.getCameraPermissionStatus()
                        "QR scanner (CameraX) · camera=$cam · Bridge-First only (no HTML5)"
                    }.getOrElse { "QR diagnostics failed: ${it.message}" }
                    diagnosticsBusy = false
                }
            }
            LaunchedEffect(Unit) { refreshBridgeDiagnostics() }
            DeviceAdminConsoleScreen(
                prefs = kioskPrefs,
                lockTask = lockTask,
                tabletKiosk = tabletKiosk,
                printerDiagnostics = printerDiagnostics,
                scannerDiagnostics = scannerDiagnostics,
                diagnosticsBusy = diagnosticsBusy,
                onRefreshDiagnostics = ::refreshBridgeDiagnostics,
                onBack = ::goHome,
                onExitLockTask = { /* local audit inside console */ },
            )
        }
        ManagementRoute.ModuleMenu -> {
            val module = openModule
            if (module == null) {
                LaunchedEffect(Unit) { goHome() }
            } else {
                BackHandler { goHome() }
                ModuleSubMenu(
                    module = module,
                    staffRoles = staffRoles,
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
            ClockAttendanceScreen(rpc = rpc, onBack = ::backFromFeature)
        }
        ManagementRoute.HrOnboarding -> {
            BackHandler { backFromFeature() }
            HrOnboardingScreen(
                rpc = rpc,
                photoBridge = biometricPhoto,
                staffRoles = staffRoles,
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
            FleetScreen(rpc = rpc, onBack = ::backFromFeature)
        }
        ManagementRoute.Pos -> {
            fun escapeToHub() {
                salesHome = false
                openModule = null
                route = ManagementRoute.Home
            }
            BackHandler {
                if (openModule != null) backFromFeature() else escapeToHub()
            }
            PosScreen(
                rpc = rpc,
                qr = qr,
                printer = printer,
                documentPrinter = documentPrinter,
                isSalesHome = salesHome,
                onOpenHub = ::escapeToHub,
                onOpenStaffPortal = {
                    staffPortalError = null
                    staffPortalIdentifier = ""
                    staffPortalPassword = ""
                    staffPortalLoginOpen = true
                },
                onOpenKioskSettings = if (showDeviceAdmin) {
                    { route = ManagementRoute.DeviceAdmin }
                } else {
                    null
                },
                operatorLabel = signedInEmail,
                onBack = {
                    if (openModule != null) backFromFeature() else escapeToHub()
                },
            )
        }
        ManagementRoute.EpcBrowse -> {
            BackHandler { backFromFeature() }
            StaffOfflineEpcBrowseScreen(onBack = ::backFromFeature)
        }
        ManagementRoute.Warehouse -> {
            BackHandler { backFromFeature() }
            WarehouseScreen(rpc = rpc, qr = qr, onBack = ::backFromFeature)
        }
        ManagementRoute.Bins -> {
            BackHandler { backFromFeature() }
            BinsScreen(rpc = rpc, printer = printer, onBack = ::backFromFeature)
        }
        ManagementRoute.Consignment -> {
            BackHandler { backFromFeature() }
            ConsignmentScreen(rpc = rpc, onBack = ::backFromFeature)
        }
        ManagementRoute.Blankets -> {
            BackHandler { backFromFeature() }
            BlanketsScreen(rpc = rpc, onBack = ::backFromFeature)
        }
        ManagementRoute.Credit -> {
            BackHandler { backFromFeature() }
            CreditScreen(rpc = rpc, onBack = ::backFromFeature)
        }
        ManagementRoute.Chat -> {
            BackHandler { backFromFeature() }
            ChatScreen(rpc = rpc, onBack = ::backFromFeature)
        }
    }

    if (staffPortalLoginOpen) {
        AlertDialog(
            onDismissRequest = { if (!staffPortalBusy) staffPortalLoginOpen = false },
            title = { Text("Staff access") },
            text = {
                androidx.compose.foundation.layout.Column {
                    Text(
                        "Sign in again to open the staff portal. Access is recalculated from this staff account's roles and company module policy.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = staffPortalIdentifier,
                        onValueChange = { staffPortalIdentifier = it; staffPortalError = null },
                        label = { Text("Emp # / email / phone") },
                        singleLine = true,
                        enabled = !staffPortalBusy,
                    )
                    OutlinedTextField(
                        value = staffPortalPassword,
                        onValueChange = { staffPortalPassword = it; staffPortalError = null },
                        label = { Text("Password") },
                        singleLine = true,
                        enabled = !staffPortalBusy,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    staffPortalError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        appScope.launch {
                            staffPortalBusy = true
                            staffPortalError = null
                            try {
                                staffPortalGuard.edit().putBoolean("elevation_in_progress", true).commit()
                                if (liveRpc) {
                                    val live = rpc as? SupabaseRpcClient
                                        ?: error("Live staff authentication unavailable")
                                    live.beginStaffPortalSession(staffPortalIdentifier, staffPortalPassword)
                                } else {
                                    require(staffPortalIdentifier.isNotBlank() && staffPortalPassword.isNotBlank())
                                }
                                staffPortalLoginOpen = false
                                staffPortalPassword = ""
                                staffPortalActive = true
                                salesHome = false
                                openModule = null
                                route = ManagementRoute.Home
                            } catch (_: Throwable) {
                                staffPortalGuard.edit().putBoolean("elevation_in_progress", false).apply()
                                staffPortalError = "Staff sign-in failed"
                            } finally {
                                staffPortalBusy = false
                            }
                        }
                    },
                    enabled = !staffPortalBusy && staffPortalIdentifier.isNotBlank() && staffPortalPassword.isNotBlank(),
                ) { Text(if (staffPortalBusy) "Signing in…" else "Open staff portal") }
            },
            dismissButton = {
                ShopSecondaryButton(
                    label = "Cancel",
                    onClick = { staffPortalLoginOpen = false },
                    enabled = !staffPortalBusy,
                )
            },
        )
    }
}

@Composable
private fun RoleDeniedScreen(onSignOut: () -> Unit) {
    ShopStaffScreen(
        title = "Nissan GTR Auto",
        subtitle = "Access denied",
    ) {
        ShopHonestEmpty(
            title = "No staff role",
            body = "No active staff role is assigned to this account. " +
                "Contact an administrator. Hub and POS stay locked (fail closed).",
        )
        ShopSecondaryButton(label = "Back to sign in", onClick = onSignOut)
    }
}

@Composable
private fun ManagementHome(
    signedInEmail: String?,
    onSignOut: () -> Unit,
    staffPortalActive: Boolean,
    onReturnToPos: () -> Unit,
    showChat: Boolean,
    showCredit: Boolean,
    showFleet: Boolean,
    staffRoles: List<String>,
    moduleAccess: List<String>,
    onOpenModule: (HubModule) -> Unit,
) {
    fun allowed(key: String) =
        ManagementHomeRoles.moduleAllowed(key, staffRoles, moduleAccess)

    ShopStaffScreen(
        title = "Nissan GTR Auto",
        subtitle = "Staff hub",
    ) {
        if (signedInEmail != null) {
            Text("Signed in: $signedInEmail", style = MaterialTheme.typography.bodySmall)
            if (staffPortalActive) {
                ShopSecondaryButton(label = "Return to POS", onClick = onReturnToPos)
            } else {
                ShopSecondaryButton(label = "Sign out", onClick = onSignOut)
            }
        }

        ShopSectionHeader(title = "Modules", actionLabel = null)
        Text(
            "Role-gated staff surfaces — same modules as web staff hub.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (allowed("pos")) {
            HubModuleTile(HubModule.Pos, "Counter · offline · kiosk", Icons.Filled.PointOfSale, onOpenModule)
        }
        if (allowed("warehouse")) {
            HubModuleTile(HubModule.Warehouse, "Receive · bins · consignment", Icons.Filled.Inventory2, onOpenModule)
        }
        if (allowed("procurement")) {
            HubModuleTile(HubModule.Procurement, "Blankets · RFQ", Icons.Filled.ShoppingCart, onOpenModule)
        }
        if (showCredit && allowed("crm")) {
            HubModuleTile(HubModule.Crm, "Credit · AR", Icons.Filled.AccountBalance, onOpenModule)
        }
        if (allowed("hr")) {
            HubModuleTile(HubModule.Hr, "Clock · onboarding", Icons.Filled.People, onOpenModule)
        }
        if (allowed("logistics")) {
            HubModuleTile(HubModule.Logistics, "Dispatch · track", Icons.Filled.LocalShipping, onOpenModule)
        }
        if (showFleet && allowed("fleet")) {
            HubModuleTile(HubModule.Fleet, "Vehicles · drivers", Icons.Filled.DirectionsCar, onOpenModule)
        }
        if (showChat && allowed("chat")) {
            HubModuleTile(HubModule.Chat, "Counter threads", Icons.AutoMirrored.Filled.Chat, onOpenModule)
        }
    }
}

@Composable
private fun HubModuleTile(
    module: HubModule,
    subtitle: String,
    icon: ImageVector,
    onOpenModule: (HubModule) -> Unit,
) {
    StaffModuleTile(
        title = module.title,
        subtitle = subtitle,
        icon = icon,
        onClick = { onOpenModule(module) },
    )
}

@Composable
private fun ModuleSubMenu(
    module: HubModule,
    staffRoles: List<String>,
    onBack: () -> Unit,
    onOpenFeature: (ManagementRoute) -> Unit,
) {
    val features = featuresFor(module, staffRoles)
    ShopStaffScreen(
        title = module.title,
        subtitle = "Staff module",
        onBack = onBack,
    ) {
        if (features.isEmpty()) {
            ShopHonestEmpty(
                title = "No features",
                body = "Nothing available in this module for your role.",
            )
        } else {
            features.forEach { (label, feature) ->
                StaffModuleTile(
                    title = label,
                    subtitle = module.title,
                    onClick = { onOpenFeature(feature) },
                )
            }
        }
        ShopSecondaryButton(label = "Back to modules", onClick = onBack)
    }
}

/** Labels + routes for each hub module (existing screens only). */
private fun featuresFor(
    module: HubModule,
    staffRoles: List<String>,
): List<Pair<String, ManagementRoute>> = when (module) {
    HubModule.Pos -> listOf(
        "Sales till" to ManagementRoute.Pos,
        "Offline EPC catalog" to ManagementRoute.EpcBrowse,
    )
    HubModule.Warehouse -> listOf(
        "Receive / Transfer / Cycle" to ManagementRoute.Warehouse,
        "Bins" to ManagementRoute.Bins,
        "Consignment" to ManagementRoute.Consignment,
    )
    HubModule.Procurement -> listOf(
        "Blanket POs" to ManagementRoute.Blankets,
    )
    HubModule.Crm -> listOf(
        "B2B credit" to ManagementRoute.Credit,
    )
    HubModule.Hr -> buildList {
        add("Clock in / out" to ManagementRoute.HrClock)
        if (HrOnboardingStaffRoles.allows(staffRoles)) {
            add("Onboarding" to ManagementRoute.HrOnboarding)
        }
    }
    HubModule.Logistics -> listOf(
        "Pick / DN / Dispatch" to ManagementRoute.Dispatch,
    )
    HubModule.Fleet -> listOf(
        "Vehicles" to ManagementRoute.Fleet,
    )
    HubModule.Chat -> listOf(
        "Inbox" to ManagementRoute.Chat,
    )
}
