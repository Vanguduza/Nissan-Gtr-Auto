package co.zw.nissangtr.pos.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.design.theme.PosWindowClass
import co.zw.nissangtr.pos.domain.model.ApprovalRequest
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.EpcImage
import co.zw.nissangtr.pos.domain.model.EpcSection
import co.zw.nissangtr.pos.domain.model.EpcVariant
import co.zw.nissangtr.pos.domain.model.InvoiceSummary
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.ParkedSale
import co.zw.nissangtr.pos.domain.model.Quotation
import co.zw.nissangtr.pos.domain.model.Receipt
import co.zw.nissangtr.pos.domain.model.Tender
import co.zw.nissangtr.pos.domain.model.TenderLine
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.state.EpcBrowse
import co.zw.nissangtr.pos.domain.state.PosDestination
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.ui.fakes.FakeSaleGateways
import co.zw.nissangtr.pos.ui.fakes.PosFixtures
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner
import java.time.LocalDateTime

/** Every destination, every dialog (with the blurred page behind), and the Medium / Compact shells. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PosScreensScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = LocalDateTime.of(2026, 10, 1, 7, 42)
    private fun usd(v: Double) = Money.ofMajor(v, CurrencyCode.USD)
    private val sale = PosFixtures.homeWithSale

    private fun capture(name: String, width: Dp, height: Dp, state: PosState) {
        compose.setContent {
            PosTheme(windowClass = PosWindowClass.derive(width, height)) {
                PosHomeScreen(state = state, now = now, dispatch = {}, host = PosHostActions(onScan = {}, onPrint = { _, _ -> }, onStaffPortal = {}, onKioskSettings = {}))
            }
        }
        compose.waitForIdle()
        captureScreenRoboImage("build/outputs/roborazzi/screen_$name.png")
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun quickSale() = capture("quick_sale", 1280.dp, 800.dp, sale.copy(destination = PosDestination.QuickSale))

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun customer() = capture(
        "customer",
        1280.dp,
        800.dp,
        sale.copy(
            destination = PosDestination.Customer,
            customerResults = listOf(FakeSaleGateways.customer),
            customer = FakeSaleGateways.customer,
            garage = FakeSaleGateways.garage,
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun orders() = capture(
        "orders",
        1280.dp,
        800.dp,
        PosFixtures.homeEmpty.copy(
            destination = PosDestination.Orders,
            parked = listOf(ParkedSale("p1", "PARK-001", "2026-10-01T09:12:00", usd(54.0), 2)),
            quotations = listOf(Quotation("q1", "QUO-0007", "draft", "2026-10-15", usd(220.0), 3, null)),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun returns() = capture(
        "returns",
        1280.dp,
        800.dp,
        PosFixtures.homeEmpty.copy(
            destination = PosDestination.Returns,
            invoices = listOf(InvoiceSummary("i9", "INV-000099", "Rudo Chikwanha", usd(96.0), "2026-09-30T15:20:00", "GT-R R35 VR38DETT")),
        ),
    )

    private fun epcState(): PosState {
        val detail = FakeSaleGateways.brakeDiagram
        return PosFixtures.homeEmpty.copy(
            destination = PosDestination.EpcBrowse,
            epc = EpcBrowse(
                model = VehicleModel("gt-r", "GT-R"),
                variant = EpcVariant("r35", "R35", "VR38DETT", "2007–"),
                section = EpcSection("brakes", "Brakes"),
                diagrams = listOf(detail.diagram),
                detail = detail,
                image = EpcImage(detail.imageUrl!!, FakeSaleGateways.diagramPng),
                activeOem = "D1060-JF00A",
            ),
        )
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun epc() = capture("epc", 1280.dp, 800.dp, epcState())

    @Test @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    fun epcPortrait() = capture("epc_portrait", 800.dp, 1280.dp, epcState())

    @Test @Config(qualifiers = "w400dp-h860dp-port-mdpi")
    fun epcPhone() = capture("epc_phone", 400.dp, 860.dp, epcState())

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun epcImageMissing() = capture(
        "epc_image_missing",
        1280.dp,
        800.dp,
        epcState().let { s -> s.copy(epc = s.epc.copy(image = EpcImage(s.epc.detail!!.imageUrl!!, null), activeOem = null)) },
    )

    private fun offlineSale(): PosState {
        val local = co.zw.nissangtr.pos.domain.state.LOCAL_CART_ID
        return sale.copy(online = false, cart = sale.cart.copy(cartId = local), offlineQueue = co.zw.nissangtr.pos.domain.model.OfflineSyncStatus(2, 0))
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun offlineHome() = capture("offline_home", 1280.dp, 800.dp, offlineSale())

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun offlinePayment() = capture("offline_payment", 1280.dp, 800.dp, offlineSale().copy(paymentOpen = true))

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun offlineReceipt() = capture(
        "offline_receipt",
        1280.dp,
        800.dp,
        PosFixtures.homeEmpty.copy(
            online = false,
            offlineQueue = co.zw.nissangtr.pos.domain.model.OfflineSyncStatus(3, 0),
            receipt = co.zw.nissangtr.pos.domain.model.Receipt(
                "c0ffee00-1111", "OFFLINE-C0FFEE00", sale.cart.lines, sale.cart.subtotal, sale.cart.discount, sale.cart.total,
                listOf(co.zw.nissangtr.pos.domain.model.TenderLine(co.zw.nissangtr.pos.domain.model.Tender.Cash, sale.cart.total)),
                null, null, null, null, "Tendai Moyo", "2026-10-01T10:00", offline = true,
            ),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun settingsOutbox() = capture(
        "settings_outbox",
        1280.dp,
        800.dp,
        PosFixtures.homeEmpty.copy(destination = PosDestination.Settings, offlineQueue = co.zw.nissangtr.pos.domain.model.OfflineSyncStatus(2, 1)),
    )

    private val pairing = co.zw.nissangtr.pos.domain.model.CompanionSession(
        "s1", "cart-1", "482913", "2099-10-01T07:57:00Z", co.zw.nissangtr.pos.domain.model.CompanionStatus.Open,
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun companionCode() = capture("companion_code", 1280.dp, 800.dp, sale.copy(companion = pairing, companionOpen = true))

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun companionClaimed() = capture(
        "companion_claimed",
        1280.dp,
        800.dp,
        sale.copy(companion = pairing.copy(status = co.zw.nissangtr.pos.domain.model.CompanionStatus.Claimed), companionOpen = true),
    )

    @Test @Config(qualifiers = "w400dp-h860dp-port-mdpi")
    fun companionPhone() = capture("companion_phone", 400.dp, 860.dp, sale.copy(companion = pairing, companionOpen = true))

    @Test @Config(qualifiers = "w400dp-h860dp-port-mdpi")
    fun scannerCode() = capture("scanner_code", 400.dp, 860.dp, PosFixtures.homeEmpty.copy(destination = PosDestination.Settings, scannerOpen = true))

    @Test @Config(qualifiers = "w400dp-h860dp-port-mdpi")
    fun scannerLinked() = capture(
        "scanner_linked",
        400.dp,
        860.dp,
        PosFixtures.homeEmpty.copy(
            destination = PosDestination.Settings,
            scannerOpen = true,
            scanner = co.zw.nissangtr.pos.domain.model.ScannerLink("s1", "cart-1", listOf("D1060-JF00A", "15208-65F0A")),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun settings() = capture("settings", 1280.dp, 800.dp, PosFixtures.homeEmpty.copy(destination = PosDestination.Settings))

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun paymentDialog() = capture("payment_dialog", 1280.dp, 800.dp, sale.copy(paymentOpen = true))

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun receiptDialog() = capture(
        "receipt_dialog",
        1280.dp,
        800.dp,
        PosFixtures.homeEmpty.copy(
            receipt = Receipt(
                "inv-1", "INV-000123", PosFixtures.cart.lines, PosFixtures.cart.subtotal, PosFixtures.cart.discount, PosFixtures.cart.total,
                listOf(TenderLine(Tender.Cash, usd(100.0)), TenderLine(Tender.EcoCash, usd(47.5))), usd(120.0), usd(20.0),
                "Rudo Chikwanha", "GT-R R35 VR38DETT", "Tendai Moyo", "2026-10-01T07:45:00+02:00",
            ),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun approvalDialog() = capture("approval_dialog", 1280.dp, 800.dp, sale.copy(destination = PosDestination.QuickSale, approval = ApprovalRequest.Discount(10.0)))

    @Test @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    fun mediumPortraitHome() = capture("medium_800x1280_home", 800.dp, 1280.dp, sale)

    @Test @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    fun mediumLandscapeHome() = capture("medium_1024x768_home", 1024.dp, 768.dp, sale)

    @Test @Config(qualifiers = "w412dp-h915dp-port-mdpi")
    fun compactHome() = capture("compact_412x915_home", 412.dp, 915.dp, sale)

    @Test @Config(qualifiers = "w360dp-h800dp-port-mdpi")
    fun compactMinimumHome() = capture("compact_360x800_home", 360.dp, 800.dp, sale)

    @Test @Config(qualifiers = "w412dp-h915dp-port-mdpi")
    fun compactPaymentSheet() = capture("compact_412x915_payment", 412.dp, 915.dp, sale.copy(paymentOpen = true))

    @Test @Config(qualifiers = "w412dp-h915dp-port-mdpi")
    fun compactCustomer() = capture(
        "compact_412x915_customer",
        412.dp,
        915.dp,
        sale.copy(destination = PosDestination.Customer, customerResults = listOf(FakeSaleGateways.customer), customer = FakeSaleGateways.customer, garage = FakeSaleGateways.garage),
    )
}
