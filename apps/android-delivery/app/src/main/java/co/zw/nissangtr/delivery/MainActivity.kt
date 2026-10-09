package co.zw.nissangtr.delivery

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Today
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import co.zw.nissangtr.delivery.design.Slopes
import co.zw.nissangtr.delivery.design.SlopesMode
import co.zw.nissangtr.delivery.design.SlopesTab
import co.zw.nissangtr.delivery.design.SlopesTabBar
import co.zw.nissangtr.delivery.design.SlopesTheme
import co.zw.nissangtr.delivery.jobs.DeliveryMeTab
import co.zw.nissangtr.delivery.jobs.DeliveryRouteTab
import co.zw.nissangtr.delivery.jobs.JobDetailScreen
import co.zw.nissangtr.delivery.jobs.JobsListScreen
import co.zw.nissangtr.delivery.jobs.JobsModule
import co.zw.nissangtr.delivery.jobs.JobsViewModel
import co.zw.nissangtr.delivery.jobs.resolveSelectedJob
import co.zw.nissangtr.delivery.pod.PodModule
import co.zw.nissangtr.delivery.rpc.RpcClient
import co.zw.nissangtr.delivery.rpc.RpcClientFactory
import co.zw.nissangtr.delivery.rpc.SupabaseRpcClient
import co.zw.nissangtr.delivery.tracking.TrackingModule
import co.zw.nissangtr.delivery.tracking.TrackingViewModel

/**
 * Driver shell — Today / Route / Account over [RpcClient], in the Slopes-style visual system
 * (map-first screens with a draggable sheet; light, dark or follow the phone).
 * Hardware stays Bridge-First: GPS FGS, POD camera, POD signature, maps.
 */
class MainActivity : ComponentActivity() {

    private lateinit var gpsBridge: FusedLocationGpsBridge
    private lateinit var cameraBridge: CameraxPodCameraBridge
    private lateinit var signatureBridge: CanvasPodSignatureBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
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

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        setContent {
            var appearance by remember {
                mutableStateOf(
                    runCatching { SlopesMode.valueOf(prefs.getString(KEY_APPEARANCE, null) ?: "") }
                        .getOrDefault(SlopesMode.System),
                )
            }
            SlopesTheme(mode = appearance) {
                Box(Modifier.fillMaxSize().background(Slopes.colors.background)) {
                    AuthGate(liveRpc = live, supabase = supabase) { email, onSignOut ->
                        DeliveryApp(
                            rpc = rpc,
                            gps = gpsBridge,
                            camera = cameraBridge,
                            signature = signatureBridge,
                            signedInEmail = email,
                            supportPhone = BuildConfig.SUPPORT_PHONE,
                            routingBaseUrl = BuildConfig.ROUTING_BASE_URL,
                            mapStyleUrl = BuildConfig.MAPLIBRE_STYLE_URL,
                            appearance = appearance,
                            onAppearanceChange = {
                                appearance = it
                                prefs.edit().putString(KEY_APPEARANCE, it.name).apply()
                            },
                            onSignOut = onSignOut,
                        )
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

    private companion object {
        const val PREFS = "delivery_prefs"
        const val KEY_APPEARANCE = "appearance"
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
    Today("Today", Icons.Filled.Today),
    Route("Route", Icons.Filled.Route),
    Account("Account", Icons.Filled.AccountCircle),
}

@Composable
private fun DeliveryApp(
    rpc: RpcClient,
    gps: GpsBridge,
    camera: PodCameraBridge,
    signature: PodSignatureBridge,
    signedInEmail: String?,
    supportPhone: String,
    routingBaseUrl: String,
    mapStyleUrl: String,
    appearance: SlopesMode,
    onAppearanceChange: (SlopesMode) -> Unit,
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
            routingBaseUrl = routingBaseUrl,
            mapStyleUrl = mapStyleUrl,
        ),
    )
    val state by jobsVm.state.collectAsState()
    val tracking by trackingVm.state.collectAsState()
    // Derive from collected state so selectedJobId invalidates composition (not a raw VM peek).
    val selected = resolveSelectedJob(state)
    var tab by rememberSaveable { mutableStateOf(DriverTab.Today) }

    if (selected != null) {
        BackHandler { jobsVm.selectJob(null) }
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

    val tabs = remember { DriverTab.entries.map { SlopesTab(it.name, it.label, it.icon) } }
    Column(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
            label = "driver-tab",
        ) { current ->
            when (current) {
                DriverTab.Today -> JobsListScreen(
                    state = state,
                    vm = jobsVm,
                    trackingVm = trackingVm,
                    tracking = tracking,
                )
                DriverTab.Route -> DeliveryRouteTab(
                    state = state,
                    tracking = tracking,
                    vm = jobsVm,
                    trackingVm = trackingVm,
                )
                DriverTab.Account -> DeliveryMeTab(
                    state = state,
                    vm = jobsVm,
                    trackingVm = trackingVm,
                    signedInEmail = signedInEmail,
                    onSignOut = onSignOut,
                    appearance = appearance,
                    onAppearanceChange = onAppearanceChange,
                    appVersion = "GTR Delivery ${BuildConfig.VERSION_NAME}",
                )
            }
        }
        SlopesTabBar(
            tabs = tabs,
            selectedKey = tab.name,
            onSelect = { key -> tab = DriverTab.valueOf(key) },
        )
    }
}
