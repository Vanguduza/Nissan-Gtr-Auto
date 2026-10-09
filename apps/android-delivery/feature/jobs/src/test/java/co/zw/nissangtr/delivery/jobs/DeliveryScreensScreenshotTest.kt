package co.zw.nissangtr.delivery.jobs

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
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
import co.zw.nissangtr.delivery.design.SlopesSheetFrame
import co.zw.nissangtr.delivery.design.SlopesTab
import co.zw.nissangtr.delivery.design.SlopesTabBar
import co.zw.nissangtr.delivery.design.SlopesTheme
import co.zw.nissangtr.delivery.pod.CodMethod
import co.zw.nissangtr.delivery.pod.DeliveryPaymentContent
import co.zw.nissangtr.delivery.pod.DeliveryPaymentUiState
import co.zw.nissangtr.delivery.pod.PodSectionContent
import co.zw.nissangtr.delivery.rpc.DeliveryCardAttempt
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
    @Test fun stop_light() = shot(SlopesMode.Light) { Detail("detail", popup = null) }
    @Test fun stop_dark() = shot(SlopesMode.Dark) { Detail("detail", popup = null) }
    @Test fun stop_proof_light() = shot(SlopesMode.Light) { Detail("detail", popup = StopPopup.Proof) }
    @Test fun stop_pay_cash_light() = shot(SlopesMode.Light) { Pay(payState) }
    @Test fun stop_pay_card_dark() = shot(SlopesMode.Dark) {
        Pay(payState.copy(method = CodMethod.Card, terminals = terminals, terminalsLoaded = true, selectedTerminalId = terminals.first().id, appInstalled = true, paired = false))
    }
    @Test fun stop_pay_recovery_light() = shot(SlopesMode.Light) {
        Pay(
            payState.copy(
                method = CodMethod.Card,
                attempt = DeliveryCardAttempt("att-1", "unknown", 45.50, "USD", "GTR-DCT-7F3A2C", "Demo swipe machine", emptyMap(), null, null, null, null),
            ),
        )
    }
    private val partPaid by lazy { payState.copy(context = payState.context!!.copy(amountPaid = 20.0, amountDue = 25.5), amount = "25.50") }
    private fun balance(status: String, basis: String) =
        co.zw.nissangtr.delivery.rpc.DeliveryBalanceApproval("b1", status, basis, 25.5, "USD", "Paid what they had", "Rudo M.".takeIf { status == "approved" }, null)
    @Test fun stop_pay_on_account_ask_light() = shot(SlopesMode.Light) {
        Pay(partPaid.copy(onAccountOpen = true, onAccountReason = "Paid what they had, will settle at the branch"))
    }
    @Test fun stop_pay_on_account_waiting_dark() = shot(SlopesMode.Dark) { Pay(partPaid.copy(approval = balance("pending", "back_office"))) }
    @Test fun stop_pay_on_account_approved_light() = shot(SlopesMode.Light) { Pay(partPaid.copy(approval = balance("approved", "back_office"))) }
    private val cashHeld by lazy {
        co.zw.nissangtr.delivery.rpc.DriverCash(
            listOf(
                co.zw.nissangtr.delivery.rpc.DriverCashHolding(
                    "USD", 125.5, 2, "2026-10-05T08:12:00Z",
                    listOf(
                        co.zw.nissangtr.delivery.rpc.DriverCashCollection("c1", 80.0, null, "DJ-00041", "SINV-00310"),
                        co.zw.nissangtr.delivery.rpc.DriverCashCollection("c2", 45.5, null, "DJ-00044", "SINV-00316"),
                    ),
                ),
            ),
            listOf(co.zw.nissangtr.delivery.rpc.DriverCashHandin("h1", "DCH-00012", "approved", "USD", 350.0, 350.0, 345.0, -5.0, 14, null, "Rudo M.", "driver_short")),
        )
    }
    @Test fun account_cash_light() = shot(SlopesMode.Light) {
        Shell("Account", null) { vm, tvm -> DeliveryMeTab(baseState.copy(driverCash = cashHeld), vm, tvm, "tatenda.driver@nissangtrauto.co.zw", {}, appearance = SlopesMode.Light) }
        Popup("Cash to hand in", "Cash collected on delivery goes to the cashier", 0.85f) {
            DriverCashContent(cashHeld, busy = false, error = null, onHandIn = { _, _, _ -> }, now = java.time.Instant.parse("2026-10-05T11:20:00Z"))
        }
    }
    @Test fun account_cash_waiting_dark() = shot(SlopesMode.Dark) {
        Shell("Account", null) { vm, tvm -> DeliveryMeTab(baseState.copy(driverCash = cashHeld), vm, tvm, "tatenda.driver@nissangtrauto.co.zw", {}, appearance = SlopesMode.Dark) }
        Popup("Cash to hand in", "Cash collected on delivery goes to the cashier", 0.6f) {
            DriverCashContent(
                cashHeld.copy(holding = emptyList(), handins = listOf(cashHeld.handins.first().copy(id = "h2", documentNumber = "DCH-00013", status = "submitted", receivedAmount = null, variance = null)) + cashHeld.handins),
                busy = false, error = null, onHandIn = { _, _, _ -> },
            )
        }
    }
    @Test fun stop_issue_dark() = shot(SlopesMode.Dark) { Detail("detail", popup = StopPopup.Issue) }
    @Test fun today_status_light() = shot(SlopesMode.Light) {
        Shell("Today", "overview") { vm, tvm -> JobsListScreen(baseState, vm, tvm, tracking = tracking) }
        Popup("Your status", "Dispatch sees this", 0.5f) { PresenceChooser(DriverPresenceStatus.ON_DUTY) {} }
    }
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
    private fun Detail(map: String, popup: StopPopup?) {
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
            )
        }
        // ModalBottomSheet opens in a window Paparazzi cannot draw, so the pop-up is framed here.
        when (popup) {
            StopPopup.Proof -> Popup("Proof of delivery", job.documentNumber, 0.9f) {
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
            }
            StopPopup.Issue -> Popup("Report an issue", job.documentNumber, 0.9f) { StopIssueContent(job, detailState, vm) }
            null -> Unit
        }
    }

    private val payState = DeliveryPaymentUiState(
        jobId = FakeRpcClient.JOB_1,
        context = runBlocking { rpc.getDeliveryPaymentContext(FakeRpcClient.JOB_1) },
        amount = "45.50",
        deviceId = "a1b2c3d4e5f60718",
    )
    private val terminals = runBlocking { rpc.listDeliveryCardTerminals(null, "a1b2c3d4e5f60718") }

    /** Payment above the proof steps; "Complete delivery" stays held while money is due. */
    @Composable
    private fun Pay(state: DeliveryPaymentUiState) {
        val job = jobs.first { it.id == FakeRpcClient.JOB_1 }
        Detail("detail", popup = null)
        Popup("Proof of delivery", job.documentNumber, 0.9f) {
            DeliveryPaymentContent(state, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
            PodSectionContent(
                state = PodUiState(jobId = job.id),
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
                paymentDone = state.blockingReason == null,
                paymentBlock = state.blockingReason,
            )
        }
    }

    @Composable
    private fun Popup(title: String, subtitle: String?, height: Float, content: @Composable ColumnScope.() -> Unit) {
        Box(Modifier.fillMaxSize().background(Slopes.colors.scrim), contentAlignment = Alignment.BottomCenter) {
            SlopesSheetFrame(title = title, subtitle = subtitle, onClose = {}, modifier = Modifier.fillMaxHeight(height), content = content)
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
