import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.customer.auth.AuthGate
import co.zw.nissangtr.customer.auth.AuthModule
import co.zw.nissangtr.customer.cart.CartModule
import co.zw.nissangtr.customer.cart.CartScreen
import co.zw.nissangtr.customer.chat.ChatModule
import co.zw.nissangtr.customer.chat.ChatScreen
import co.zw.nissangtr.customer.garage.GarageModule
import co.zw.nissangtr.customer.garage.GarageScreen
import co.zw.nissangtr.customer.orders.OrdersModule
import co.zw.nissangtr.customer.orders.OrdersScreen
import co.zw.nissangtr.customer.pay.PayIntentScreen
import co.zw.nissangtr.customer.pay.PayModule
import co.zw.nissangtr.customer.rpc.FakeRpcClient
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcClientFactory
import co.zw.nissangtr.customer.rpc.SupabaseRpcClient
import co.zw.nissangtr.customer.track.DeliveryTrackScreen
import co.zw.nissangtr.customer.track.TrackModule

private enum class CustomerRoute {
    Home,
    Cart,
    Orders,
    Garage,
    Pay,
    Chat,
    Track,
}

private data class TrackLaunchArgs(
    val token: String? = null,
    val jobId: String? = null,
    val seq: Int = 0,
)

/**
 * Customer shell. Feature screens are thin scaffolds over [RpcClient]
 * ([RpcClientFactory]: Live [SupabaseRpcClient] or Fake).
 * Live requires GoTrue email/password session via [AuthGate].
 * Money/pricing: @gtr/shared. Hardware QR: bridges/ only — never HTML5.
 *
 * Deep link / extras for privacy-safe track (last point + ETA only):
 * - Intent extras: `track_token`, `track_job_id`
 * - Custom: `gtrcustomer://track/{token}`
 * - HTTPS path: `https://…/track/{token}` (same path as web SMS)
 */
class MainActivity : ComponentActivity() {
    private val trackLaunch = mutableStateOf(TrackLaunchArgs())
    private var trackSeq = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        listOf(
            AuthModule.id,
            CartModule.id,
            OrdersModule.id,
            GarageModule.id,
            PayModule.id,
            ChatModule.id,
            TrackModule.id,
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
        applyTrackIntent(intent)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AuthGate(liveRpc = live, supabase = supabase) { email, onSignOut ->
                        val launch by trackLaunch
                        CustomerApp(
                            rpc = rpc,
                            liveRpc = live,
                            signedInEmail = email,
                            onSignOut = onSignOut,
                            whatsappE164 = BuildConfig.WHATSAPP_E164,
                            trackLaunch = launch,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyTrackIntent(intent)
    }

    private fun applyTrackIntent(intent: Intent?) {
        if (intent == null) return
        val fromExtra = intent.getStringExtra(EXTRA_TRACK_TOKEN)?.trim()?.takeIf { it.isNotEmpty() }
        val fromUri = parseTrackToken(intent.data)
        val token = fromExtra ?: fromUri
        val jobId = intent.getStringExtra(EXTRA_TRACK_JOB_ID)?.trim()?.takeIf { it.isNotEmpty() }
        if (token == null && jobId == null) return
        trackSeq += 1
        trackLaunch.value = TrackLaunchArgs(token = token, jobId = jobId, seq = trackSeq)
    }

    companion object {
        const val EXTRA_TRACK_TOKEN = "track_token"
        const val EXTRA_TRACK_JOB_ID = "track_job_id"

        /** Parse `/track/{token}` from https or `gtrcustomer://track/{token}`. */
        fun parseTrackToken(uri: Uri?): String? {
            if (uri == null) return null
            val segments = uri.pathSegments.orEmpty()
            when {
                // gtrcustomer://track/{token} → host=track, path=/{token}
                uri.scheme.equals("gtrcustomer", ignoreCase = true) &&
                    uri.host.equals("track", ignoreCase = true) -> {
                    val token = segments.firstOrNull() ?: uri.lastPathSegment
                    return token?.trim()?.takeIf { it.length >= 8 }
                }
                // https://host/track/{token}
                segments.size >= 2 &&
                    segments[segments.size - 2].equals("track", ignoreCase = true) -> {
                    return segments.last().trim().takeIf { it.length >= 8 }
                }
            }
            return null
        }
    }
}

@Composable
private fun CustomerApp(
    rpc: RpcClient,
    liveRpc: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
    whatsappE164: String,
    trackLaunch: TrackLaunchArgs = TrackLaunchArgs(),
) {
    var route by remember { mutableStateOf(CustomerRoute.Home) }
    var trackToken by remember { mutableStateOf<String?>(null) }
    var trackJobId by remember { mutableStateOf<String?>(null) }
    var trackReturn by remember { mutableStateOf(CustomerRoute.Home) }
    var trackSession by remember { mutableIntStateOf(0) }
    var lastLaunchSeq by remember { mutableIntStateOf(0) }

    fun openTrack(
        jobId: String?,
        token: String?,
        from: CustomerRoute = CustomerRoute.Home,
        resetFake: Boolean = false,
    ) {
        if (resetFake && rpc is FakeRpcClient) {
            rpc.resetFakeTrackPoint()
        }
        trackJobId = jobId
        trackToken = token
        trackReturn = from
        trackSession += 1
        route = CustomerRoute.Track
    }

    LaunchedEffect(trackLaunch.seq) {
        if (trackLaunch.seq == 0 || trackLaunch.seq == lastLaunchSeq) return@LaunchedEffect
        lastLaunchSeq = trackLaunch.seq
        openTrack(
            jobId = trackLaunch.jobId,
            token = trackLaunch.token,
            from = CustomerRoute.Home,
            resetFake = !liveRpc && trackLaunch.token == FakeRpcClient.SEED_TRACK_TOKEN,
        )
    }

    when (route) {
        CustomerRoute.Home -> CustomerHome(
            liveRpc = liveRpc,
            signedInEmail = signedInEmail,
            onSignOut = onSignOut,
            onCart = { route = CustomerRoute.Cart },
            onOrders = { route = CustomerRoute.Orders },
            onGarage = { route = CustomerRoute.Garage },
            onPay = { route = CustomerRoute.Pay },
            onChat = { route = CustomerRoute.Chat },
            onTrack = {
                openTrack(
                    jobId = if (!liveRpc) FakeRpcClient.SEED_ACTIVE_JOB_ID else null,
                    token = if (!liveRpc) FakeRpcClient.SEED_TRACK_TOKEN else null,
                    from = CustomerRoute.Home,
                    resetFake = !liveRpc,
                )
            },
        )
        CustomerRoute.Cart -> CartScreen(
            rpc = rpc,
            onBack = { route = CustomerRoute.Home },
        )
        CustomerRoute.Orders -> OrdersScreen(
            rpc = rpc,
            onBack = { route = CustomerRoute.Home },
            onTrackDelivery = { jobId, token ->
                openTrack(
                    jobId = jobId,
                    token = token,
                    from = CustomerRoute.Orders,
                    resetFake = !liveRpc && jobId == FakeRpcClient.SEED_ACTIVE_JOB_ID,
                )
            },
        )
        CustomerRoute.Garage -> GarageScreen(
            rpc = rpc,
            onBack = { route = CustomerRoute.Home },
        )
        CustomerRoute.Pay -> PayIntentScreen(
            rpc = rpc,
            onBack = { route = CustomerRoute.Home },
        )
        CustomerRoute.Chat -> ChatScreen(
            rpc = rpc,
            onBack = { route = CustomerRoute.Home },
            whatsappE164Digits = whatsappE164,
        )
        CustomerRoute.Track -> DeliveryTrackScreen(
            rpc = rpc,
            initialToken = trackToken,
            initialJobId = trackJobId,
            onBack = { route = trackReturn },
        )
    }
}

@Composable
private fun CustomerHome(
    liveRpc: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
    onCart: () -> Unit,
    onOrders: () -> Unit,
    onGarage: () -> Unit,
    onPay: () -> Unit,
    onChat: () -> Unit,
    onTrack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nissan GTR Auto", style = MaterialTheme.typography.headlineMedium)
        Text("Customer app", style = MaterialTheme.typography.bodyMedium)
        Text(
            "Modules: ${AuthModule.id}, ${CartModule.id}, ${OrdersModule.id}, " +
                "${GarageModule.id}, ${PayModule.id}, ${ChatModule.id}, ${TrackModule.id}",
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
        Button(onClick = onCart, modifier = Modifier.fillMaxWidth()) {
            Text("Cart")
        }
        Button(onClick = onOrders, modifier = Modifier.fillMaxWidth()) {
            Text("Orders")
        }
        Button(onClick = onGarage, modifier = Modifier.fillMaxWidth()) {
            Text("My Garage")
        }
        Button(onClick = onPay, modifier = Modifier.fillMaxWidth()) {
            Text("Pay — ContiPay / Paynow")
        }
        Button(onClick = onChat, modifier = Modifier.fillMaxWidth()) {
            Text("Live chat")
        }
        Button(onClick = onTrack, modifier = Modifier.fillMaxWidth()) {
            Text("Track delivery")
        }
        Text(
            "Auth: GoTrue signInWith(Email). Bridge-First for QR. " +
                "Delivery track: last point + ETA only.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
