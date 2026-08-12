package co.zw.nissangtr.delivery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.location.FusedLocationGpsBridge
import co.zw.nissangtr.bridges.location.GpsBridge
import co.zw.nissangtr.bridges.location.GpsPingBuffer
import co.zw.nissangtr.bridges.podcamera.CameraxPodCameraBridge
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.bridges.podsignature.CanvasPodSignatureBridge
import co.zw.nissangtr.bridges.podsignature.PodSignatureBridge
import co.zw.nissangtr.delivery.auth.AuthGate
import co.zw.nissangtr.delivery.auth.AuthModule
import co.zw.nissangtr.delivery.jobs.DeliveryMeTab
import co.zw.nissangtr.delivery.jobs.DeliveryRouteTab
import co.zw.nissangtr.delivery.jobs.JobDetailScreen
import co.zw.nissangtr.delivery.jobs.JobsListScreen
import co.zw.nissangtr.delivery.jobs.JobsModule
import co.zw.nissangtr.delivery.jobs.JobsViewModel
import co.zw.nissangtr.delivery.pod.PodModule
import co.zw.nissangtr.delivery.rpc.RpcClient
import co.zw.nissangtr.delivery.rpc.RpcClientFactory
import co.zw.nissangtr.delivery.rpc.SupabaseRpcClient
import co.zw.nissangtr.delivery.tracking.TrackingModule
import co.zw.nissangtr.delivery.tracking.TrackingViewModel
import co.zw.nissangtr.ui.shop.ShopBottomBar
import co.zw.nissangtr.ui.shop.ShopBottomTab
import co.zw.nissangtr.ui.shop.ShopSplash
import co.zw.nissangtr.ui.shop.ShopTheme

/**
 * Driver shell — Shopping-By-KMP MainNav IA (Jobs / Route / Me) over [RpcClient].
 * Full ShopKit visual system (ShopTheme Standard + ShopBottomBar), not staff compact.
 * Hardware stays Bridge-First: GPS FGS, POD camera, POD signature, Maps nav.
 */
class MainActivity : ComponentActivity() {

    private lateinit var gpsBridge: FusedLocationGpsBridge
    private lateinit var cameraBridge: CameraxPodCameraBridge
    private lateinit var signatureBridge: CanvasPodSignatureBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gpsBridge = FusedLocationGpsBridge(this, pingBuffer = GpsPingBuffer())
        cameraBridge = CameraxPodCameraBridge(this)
        signatureBridge = CanvasPodSignatureBridge(this)
        listOf(AuthModule.id, JobsModule.id, TrackingModule.id, PodModule.id)

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
            // Full ShopKit density (Standard) — same entry as customer; no compact shortcut.
            ShopTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var splashDone by remember { mutableStateOf(false) }
                    if (!splashDone) {
                        ShopSplash(
                            brand = "Nissan GTR Auto",
                            tagline = "Driver · jobs · live maps · POD",
                            onFinished = { splashDone = true },
                        )
                    } else {
                        AuthGate(liveRpc = live, supabase = supabase) { email, onSignOut ->
                            DeliveryApp(
                                rpc = rpc,
                                gps = gpsBridge,
                                camera = cameraBridge,
                                signature = signatureBridge,
                                liveRpc = live,
                                signedInEmail = email,
                                supportPhone = BuildConfig.SUPPORT_PHONE,
                                mapsApiKey = BuildConfig.GOOGLE_MAPS_API_KEY,
                                osrmUrl = BuildConfig.OSRM_URL,
                                useMapLibre = BuildConfig.USE_MAPLIBRE,
                                onSignOut = onSignOut,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::gpsBridge.isInitialized) gpsBridge.attachActivity(this)
        if (::cameraBridge.isInitialized) cameraBridge.attachActivity(this)
        if (::signatureBridge.isInitialized) signatureBridge.attachActivity(this)
    }

    override fun onPause() {
        if (::gpsBridge.isInitialized) gpsBridge.detachActivity()
        if (::cameraBridge.isInitialized) cameraBridge.detachActivity()
        if (::signatureBridge.isInitialized) signatureBridge.detachActivity()
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
            FusedLocationGpsBridge.REQUEST_LOCATION,
            FusedLocationGpsBridge.REQUEST_BACKGROUND_LOCATION,
            -> if (::gpsBridge.isInitialized) gpsBridge.onPermissionResult()
            CameraxPodCameraBridge.REQUEST_CAMERA,
            -> if (::cameraBridge.isInitialized) cameraBridge.onPermissionResult()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            CameraxPodCameraBridge.REQUEST_CAPTURE ->
                if (::cameraBridge.isInitialized) {
                    cameraBridge.onCaptureActivityResult(resultCode, data)
                }
            CanvasPodSignatureBridge.REQUEST_SIGNATURE ->
                if (::signatureBridge.isInitialized) {
                    signatureBridge.onSignatureActivityResult(resultCode, data)
                }
        }
    }
}

private enum class DriverTab(val label: String, val icon: ImageVector) {
    Jobs("Jobs", Icons.Filled.LocalShipping),
    Route("Route", Icons.Filled.Map),
    Me("Me", Icons.Filled.AccountCircle),
}

@Composable
private fun DeliveryApp(
    rpc: RpcClient,
    gps: GpsBridge,
    camera: PodCameraBridge,
    signature: PodSignatureBridge,
    liveRpc: Boolean,
    signedInEmail: String?,
    supportPhone: String,
    mapsApiKey: String,
    osrmUrl: String,
    onSignOut: () -> Unit,
) {
    val context = LocalContext.current
    val trackingVm: TrackingViewModel = viewModel(
        factory = TrackingViewModel.factory(rpc, gps, context),
    )
    val jobsVm: JobsViewModel = viewModel(
        factory = JobsViewModel.factory(
            rpc,
            gps,
            context,
            supportPhone,
            mapsApiKey,
            osrmUrl,
        ),
    )
    val state by jobsVm.state.collectAsState()
    val tracking by trackingVm.state.collectAsState()
    val selected = jobsVm.selectedJob()
    var tab by remember { mutableStateOf(DriverTab.Jobs) }
    val modeLabel = if (liveRpc) "Live · GPS/POD" else "Fake · GPS/POD"

    if (selected != null) {
        JobDetailScreen(
            job = selected,
            state = state,
            tracking = tracking,
            vm = jobsVm,
            trackingVm = trackingVm,
            rpc = rpc,
            camera = camera,
            signature = signature,
            onBack = { jobsVm.selectJob(null) },
        )
        return
    }

    val bottomTabs = remember {
        DriverTab.entries.map { ShopBottomTab(it.name, it.label, it.icon) }
    }
    // KMP MainNav: elevated ShopBottomBar + tab bodies (same chrome as customer 4-tab).
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ShopBottomBar(
                tabs = bottomTabs,
                selectedKey = tab.name,
                onSelect = { key -> tab = DriverTab.valueOf(key) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                DriverTab.Jobs -> JobsListScreen(
                    state = state,
                    vm = jobsVm,
                    trackingVm = trackingVm,
                    shellSubtitle = modeLabel,
                )
                DriverTab.Route -> DeliveryRouteTab(
                    state = state,
                    tracking = tracking,
                    vm = jobsVm,
                    trackingVm = trackingVm,
                )
                DriverTab.Me -> DeliveryMeTab(
                    state = state,
                    vm = jobsVm,
                    trackingVm = trackingVm,
                    signedInEmail = signedInEmail,
                    modeLabel = modeLabel,
                    onSignOut = onSignOut,
                )
            }
        }
    }
}
