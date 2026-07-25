package co.zw.nissangtr.delivery

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import co.zw.nissangtr.delivery.jobs.JobsModule
import co.zw.nissangtr.delivery.jobs.JobsScreen
import co.zw.nissangtr.delivery.pod.PodModule
import co.zw.nissangtr.delivery.rpc.RpcClient
import co.zw.nissangtr.delivery.rpc.RpcClientFactory
import co.zw.nissangtr.delivery.rpc.SupabaseRpcClient
import co.zw.nissangtr.delivery.tracking.TrackingModule
import co.zw.nissangtr.delivery.tracking.TrackingViewModel

private enum class DeliveryRoute {
    Home,
    Jobs,
}

/**
 * Driver-only shell. Feature screens over [RpcClient] (Live or Fake).
 * Hardware: bridges only — GPS FGS, POD camera, POD signature.
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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuthGate(liveRpc = live, supabase = supabase) { email, onSignOut ->
                        DeliveryApp(
                            rpc = rpc,
                            gps = gpsBridge,
                            camera = cameraBridge,
                            signature = signatureBridge,
                            liveRpc = live,
                            signedInEmail = email,
                            supportPhone = BuildConfig.SUPPORT_PHONE,
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

@Composable
private fun DeliveryApp(
    rpc: RpcClient,
    gps: GpsBridge,
    camera: PodCameraBridge,
    signature: PodSignatureBridge,
    liveRpc: Boolean,
    signedInEmail: String?,
    supportPhone: String,
    onSignOut: () -> Unit,
) {
    var route by remember { mutableStateOf(DeliveryRoute.Home) }
    val context = LocalContext.current
    val trackingVm: TrackingViewModel = viewModel(
        factory = TrackingViewModel.factory(rpc, gps, context),
    )

    when (route) {
        DeliveryRoute.Home -> DeliveryHome(
            liveRpc = liveRpc,
            signedInEmail = signedInEmail,
            supportPhone = supportPhone,
            onSignOut = onSignOut,
            onJobs = { route = DeliveryRoute.Jobs },
        )
        DeliveryRoute.Jobs -> JobsScreen(
            rpc = rpc,
            gps = gps,
            camera = camera,
            signature = signature,
            supportPhone = supportPhone,
            trackingVm = trackingVm,
            onBack = { route = DeliveryRoute.Home },
        )
    }
}

@Composable
private fun DeliveryHome(
    liveRpc: Boolean,
    signedInEmail: String?,
    supportPhone: String,
    onSignOut: () -> Unit,
    onJobs: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nissan GTR Auto", style = MaterialTheme.typography.headlineMedium)
        Text("Delivery driver app", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Modules: ${AuthModule.id}, ${JobsModule.id}, ${TrackingModule.id}, ${PodModule.id}",
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
        if (supportPhone.isNotBlank()) {
            Text("Support: $supportPhone", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                "SUPPORT_PHONE not set — panic dialer limited",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Button(onClick = onJobs, modifier = Modifier.fillMaxWidth()) {
            Text("My jobs — GPS / POD / navigate")
        }
        Text(
            "Driver-only. No POS/warehouse/HR/finance. No ZIMRA. Bridge-First GPS/camera/signature.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
