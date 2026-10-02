package co.zw.nissangtr.delivery.jobs

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import co.zw.nissangtr.bridges.location.GpsBridge
import co.zw.nissangtr.bridges.location.GpsCoordinate
import co.zw.nissangtr.bridges.location.GpsWatchHandle
import co.zw.nissangtr.bridges.location.GpsWatchOptions
import co.zw.nissangtr.bridges.location.LocationPermissionStatus
import co.zw.nissangtr.bridges.podcamera.FakePodCameraBridge
import co.zw.nissangtr.bridges.podsignature.FakePodSignatureBridge
import co.zw.nissangtr.bridges.podsignature.rememberComposeSignaturePadState
import co.zw.nissangtr.delivery.auth.SignInForm
import co.zw.nissangtr.delivery.design.Slopes
import co.zw.nissangtr.delivery.design.SlopesMode
import co.zw.nissangtr.delivery.design.SlopesTab
import co.zw.nissangtr.delivery.design.SlopesTabBar
import co.zw.nissangtr.delivery.design.SlopesTheme
import co.zw.nissangtr.delivery.pod.PodSectionContent
import co.zw.nissangtr.delivery.pod.PodUiState
import co.zw.nissangtr.delivery.rpc.DriverPresenceStatus
import co.zw.nissangtr.delivery.rpc.FakeRpcClient
import co.zw.nissangtr.delivery.rpc.OptimizedStop
import co.zw.nissangtr.delivery.tracking.TrackingUiState
import co.zw.nissangtr.delivery.tracking.TrackingViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

/**
 * Screenshots of the Slopes-style driver screens in light and dark, from the Fake seed jobs.
 * The native map cannot render under Paparazzi, so the map slot shows a backdrop: a locally
 * rendered map image when `-PdeliveryMapDir` is given, otherwise a plain grid.
 */
class DeliveryScreensScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, maxPercentDifference = 0.1)

    private val rpc = FakeRpcClient()
    private val jobs = runBlocking { rpc.listMyDeliveryJobs() }
    private val driverLat = -17.8150
    private val driverLng = 31.0350

    private val baseState = JobsUiState(
        jobs = jobs,
        presence = DriverPresenceStatus.ON_DUTY,
        supportPhone = "+263 77 000 0000",
        optimizedStops = listOf(
            OptimizedStop(FakeRpcClient.JOB_1, 1, 3_675.0),
            OptimizedStop(FakeRpcClient.JOB_2, 2, 1_210.0),
        ),
    )
    private val tracking = TrackingUiState(
        trackingJobId = FakeRpcClient.JOB_1,
        tracking = true,
        lastLat = driverLat,
        lastLng = driverLng,
        ingestCount = 42,
        queuedCount = 0,
    )
    // Distance and time of the real driving route drawn on the detail backdrop.
    private val detailState = baseState.copy(
        selectedJobId = FakeRpcClient.JOB_1,
        routeEtaSource = RouteEtaSource.OSRM,
        routeDistanceMeters = 3_675,
        routeDurationSeconds = 288,
        routeLabel = "Driving · 3.7 km · 5 min",
    )

    @Test fun today_light() = shot(SlopesMode.Light) { Shell("Today", "overview") { vm, tvm -> JobsListScreen(baseState, vm, tvm, tracking = tracking) } }
    @Test fun today_dark() = shot(SlopesMode.Dark) { Shell("Today", "overview") { vm, tvm -> JobsListScreen(baseState, vm, tvm, tracking = tracking) } }
    @Test fun route_light() = shot(SlopesMode.Light) { Shell("Route", "overview") { vm, tvm -> DeliveryRouteTab(baseState, tracking, vm, tvm) } }
    @Test fun route_dark() = shot(SlopesMode.Dark) { Shell("Route", "overview") { vm, tvm -> DeliveryRouteTab(baseState, tracking, vm, tvm) } }
    @Test fun account_light() = shot(SlopesMode.Light) {
        Shell("Account", null) { vm, tvm -> DeliveryMeTab(baseState, vm, tvm, "tatenda.driver@nissangtrauto.co.zw", {}, appearance = SlopesMode.Light, appVersion = "GTR Delivery 0.1.1") }
    }
    @Test fun account_dark() = shot(SlopesMode.Dark) {
        Shell("Account", null) { vm, tvm -> DeliveryMeTab(baseState, vm, tvm, "tatenda.driver@nissangtrauto.co.zw", {}, appearance = SlopesMode.Dark, appVersion = "GTR Delivery 0.1.1") }
    }
    @Test fun stop_light() = shot(SlopesMode.Light) { Detail("detail", tab = 0) }
    @Test fun stop_dark() = shot(SlopesMode.Dark) { Detail("detail", tab = 0) }
    @Test fun stop_proof_light() = shot(SlopesMode.Light) { Detail("detail", tab = 1) }
    @Test fun stop_issue_dark() = shot(SlopesMode.Dark) { Detail("detail", tab = 2) }
    @Test fun signin_light() = shot(SlopesMode.Light) { SignIn() }
    @Test fun signin_dark() = shot(SlopesMode.Dark) { SignIn() }

    private fun shot(mode: SlopesMode, content: @Composable (SlopesMode) -> Unit) {
        paparazzi.snapshot {
            SlopesTheme(mode = mode) {
                Box(Modifier.fillMaxSize().background(Slopes.colors.background)) { content(mode) }
            }
        }
    }

    @Composable
    private fun viewModels(): Pair<JobsViewModel, TrackingViewModel> {
        val context = LocalContext.current
        val gps = StillGps(driverLat, driverLng)
        return JobsViewModel(rpc, gps, context, "+263 77 000 0000", routingBaseUrl = "") to
            TrackingViewModel.factory(rpc, gps, context).create(TrackingViewModel::class.java)
    }

    @Composable
    private fun Shell(
        selected: String,
        map: String?,
        body: @Composable (JobsViewModel, TrackingViewModel) -> Unit,
    ) {
        val (vm, tvm) = viewModels()
        WithMap(map) {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxWidth()) { body(vm, tvm) }
                SlopesTabBar(
                    tabs = listOf(
                        SlopesTab("Today", "Today", Icons.Filled.Today),
                        SlopesTab("Route", "Route", Icons.Filled.Route),
                        SlopesTab("Account", "Account", Icons.Filled.AccountCircle),
                    ),
                    selectedKey = selected,
                    onSelect = {},
                )
            }
        }
    }

    @Composable
    private fun Detail(map: String, tab: Int) {
        val (vm, tvm) = viewModels()
        val job = jobs.first { it.id == FakeRpcClient.JOB_1 }
        WithMap(map) {
            JobDetailScreen(
                job = job,
                state = detailState,
                tracking = tracking,
                vm = vm,
                trackingVm = tvm,
                rpc = rpc,
                camera = FakePodCameraBridge(),
                signature = FakePodSignatureBridge(),
                onBack = {},
                initialTab = tab,
                proofContent = {
                    PodSectionContent(
                        state = PodUiState(jobId = job.id, otpGenerated = true, otpCode = "4821"),
                        padState = rememberComposeSignaturePadState(),
                        onCapturePhoto = {},
                        onConfirmSignature = {},
                        onFullScreenSignature = {},
                        onResign = {},
                        onSendCode = {},
                        onCodeChange = {},
                        onVerifyCode = {},
                        onNotesChange = {},
                        onSubmit = {},
                        onFlushQueue = {},
                    )
                },
            )
        }
    }

    @Composable
    private fun SignIn() {
        SignInForm(
            email = "tatenda.driver@nissangtrauto.co.zw",
            password = "secret-pass",
            busy = false,
            error = null,
            onEmailChange = {},
            onPasswordChange = {},
            onSignIn = {},
        )
    }

    /** Provides the screenshot map backdrop for [DeliveryMap]. */
    @Composable
    private fun WithMap(name: String?, content: @Composable () -> Unit) {
        val dir = System.getProperty("delivery.mapDir").orEmpty()
        val dark = Slopes.colors.isDark
        val file = if (name != null && dir.isNotBlank()) File(dir, "$name-${if (dark) "dark" else "light"}.png") else null
        val bitmap = file?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
        CompositionLocalProvider(
            LocalDeliveryMap provides { _, modifier ->
                if (bitmap != null) {
                    Image(bitmap, contentDescription = null, contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = modifier.fillMaxSize())
                } else {
                    GridBackdrop(modifier)
                }
            },
            content = content,
        )
    }

    @Composable
    private fun GridBackdrop(modifier: Modifier) {
        val c = Slopes.colors
        Canvas(modifier.fillMaxSize().background(c.fill)) {
            val step = size.width / 6f
            var x = 0f
            while (x < size.width) {
                drawLine(c.separator, Offset(x, 0f), Offset(x, size.height), strokeWidth = 3f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(c.separator, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f)
                y += step
            }
        }
    }

    private class StillGps(private val lat: Double, private val lng: Double) : GpsBridge {
        override suspend fun getLocationPermissionStatus() = LocationPermissionStatus.GRANTED
        override suspend fun requestLocationPermission() = LocationPermissionStatus.GRANTED
        override suspend fun getCurrentPosition() = GpsCoordinate(lat, lng, capturedAt = "2026-10-02T09:00:00Z")
        override suspend fun watchPosition(
            onUpdate: (GpsCoordinate) -> Unit,
            onError: ((String) -> Unit)?,
            options: GpsWatchOptions,
        ): GpsWatchHandle = object : GpsWatchHandle {
            override suspend fun stop() = Unit
        }
    }
}
