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

    private fun tillSession(status: co.zw.nissangtr.pos.domain.model.TillStatus, variance: Money? = null) = co.zw.nissangtr.pos.domain.model.TillSession(
        "t1", "w1", CurrencyCode.USD, "u1", usd(50.0), status, variance?.let { usd(72.5) }, variance?.let { usd(70.0) }, variance, null, "2026-10-01T07:30:00Z", null,
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tillOpen() = capture(
        "till_open",
        1280.dp,
        800.dp,
        PosFixtures.homeEmpty.copy(
            destination = PosDestination.Till,
            till = co.zw.nissangtr.pos.domain.state.TillPanel(
                enforced = true, loaded = true,
                session = tillSession(co.zw.nissangtr.pos.domain.model.TillStatus.Open),
                history = listOf(tillSession(co.zw.nissangtr.pos.domain.model.TillStatus.Open)),
            ),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tillClosed() = capture(
        "till_closed",
        1280.dp,
        800.dp,
        PosFixtures.homeEmpty.copy(
            destination = PosDestination.Till,
            till = co.zw.nissangtr.pos.domain.state.TillPanel(enforced = true, loaded = true, history = emptyList()),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tillVariance() = capture(
        "till_variance",
        1280.dp,
        800.dp,
        PosFixtures.homeEmpty.copy(
            destination = PosDestination.Till,
            till = co.zw.nissangtr.pos.domain.state.TillPanel(
                enforced = true, loaded = true,
                session = tillSession(co.zw.nissangtr.pos.domain.model.TillStatus.VariancePending, usd(-2.5)),
                reasons = mapOf("till_variance" to listOf(
                    co.zw.nissangtr.pos.domain.model.ReasonCode("count_error", "Count error", false),
                    co.zw.nissangtr.pos.domain.model.ReasonCode("other", "Other", true),
                )),
                history = emptyList(),
            ),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun tillCount() = capture(
        "till_count",
        1280.dp,
        800.dp,
        PosFixtures.homeEmpty.copy(
            destination = PosDestination.Till,
            till = co.zw.nissangtr.pos.domain.state.TillPanel(
                enforced = true, loaded = true,
                session = tillSession(co.zw.nissangtr.pos.domain.model.TillStatus.Open),
                dialog = co.zw.nissangtr.pos.domain.state.TillDialog.Close,
                history = emptyList(),
            ),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun discountWithinPolicy() = capture(
        "approval_within_policy",
        1280.dp,
        800.dp,
        sale.copy(
            destination = PosDestination.QuickSale,
            approval = co.zw.nissangtr.pos.domain.model.ApprovalRequest.Discount(3.0),
            approvalNeedsManager = false,
            approvalReasons = listOf(
                co.zw.nissangtr.pos.domain.model.ReasonCode("customer_retention", "Customer retention", false),
                co.zw.nissangtr.pos.domain.model.ReasonCode("price_match", "Price match", false),
            ),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun voidNeedsManager() = capture(
        "approval_manager",
        1280.dp,
        800.dp,
        sale.copy(
            destination = PosDestination.QuickSale,
            approval = co.zw.nissangtr.pos.domain.model.ApprovalRequest.VoidSale,
            approvalNeedsManager = true,
            approvalReasons = listOf(
                co.zw.nissangtr.pos.domain.model.ReasonCode("customer_cancelled", "Customer cancelled", false),
                co.zw.nissangtr.pos.domain.model.ReasonCode("pricing_error", "Pricing error", true),
            ),
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

    private val returnSale = InvoiceSummary("i9", "INV-000099", "Rudo Chikwanha", usd(110.0), "2026-09-30T15:20:00", "GT-R R35 VR38DETT")
    private fun returnState() = PosFixtures.homeEmpty.copy(
        destination = PosDestination.Returns,
        returnSale = returnSale,
        returnInvoice = co.zw.nissangtr.pos.domain.model.InvoiceDetail(
            "i9", "INV-000099", "c1", usd(110.0), usd(110.0), "2026-09-30T15:20:00", "till-1",
            listOf(
                co.zw.nissangtr.pos.domain.model.InvoiceLine("l1", "si", "D1060-JF00A", "Front brake pad set", "ea", 2.0, usd(45.0), usd(90.0), false, 2.0),
                co.zw.nissangtr.pos.domain.model.InvoiceLine("l2", "si", "D1060-JF00A", "Caliper core charge", "ea", 1.0, usd(20.0), usd(20.0), true, 1.0),
            ),
        ),
        returnReasons = mapOf(
            "return_post" to listOf(
                co.zw.nissangtr.pos.domain.model.ReasonCode("wrong_part", "Wrong part", false),
                co.zw.nissangtr.pos.domain.model.ReasonCode("defective", "Defective", false),
            ),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun returnSale() = capture("return_sale", 1280.dp, 800.dp, returnState())

    @Test @Config(qualifiers = "w400dp-h860dp-port-mdpi")
    fun returnSalePhone() = capture("return_sale_phone", 400.dp, 860.dp, returnState())

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun warrantyDecision() = capture(
        "warranty_approval",
        1280.dp,
        800.dp,
        returnState().copy(
            approval = ApprovalRequest.WarrantyDecide(
                co.zw.nissangtr.pos.domain.model.WarrantyClaim(
                    "wc", "WAR-00001", "open", null, "i9", "INV-000099", "si", "D1060-JF00A", "PAD-SN-0042", "Squeal after a week", null, "2026-10-01T09:00:00", null,
                ),
                co.zw.nissangtr.pos.domain.model.WarrantyDecision.Replace(1.0, "ea"),
            ),
            approvalReasons = emptyList(),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun stockByBranch() = capture(
        "stock_by_branch",
        1280.dp,
        800.dp,
        sale.copy(
            stockPart = co.zw.nissangtr.pos.domain.model.CatalogPart("si", "15208-65F0A", "Oil filter", usd(9.5), 10.0, null),
            branchStock = listOf(
                co.zw.nissangtr.pos.domain.model.BranchStock("w1", "MAIN", "Harare main", 10.0, 1.0, 9.0, 0.0),
                co.zw.nissangtr.pos.domain.model.BranchStock("w2", "BYO", "Bulawayo branch", 0.0, 0.0, 0.0, 4.0),
            ),
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

    // ------------------------------------------------------------ reserve-first checkout (§10.6–10.7)

    private fun status(state: String = "awaiting_payment", invoice: String? = null) = co.zw.nissangtr.pos.domain.model.PaymentStatus(
        orderId = "7d1c2a90-0000-4000-8000-000000000001", state = state, total = sale.cart.total,
        reservationExpiresAtIso = "2026-10-01T07:57:00Z", activeProvider = null, providerStatus = null, providerFailure = null,
        settledProvider = null, reference = null, salesInvoiceId = invoice, paymentException = null,
    )

    private fun reserved(
        outcome: co.zw.nissangtr.pos.domain.model.TenderOutcome? = null,
        attempt: co.zw.nissangtr.pos.domain.model.ProviderAttempt? = null,
        st: co.zw.nissangtr.pos.domain.model.PaymentStatus = status(),
    ) = sale.copy(
        reserveCheckout = true,
        paymentOpen = true,
        providers = mapOf(
            co.zw.nissangtr.pos.domain.model.DigitalProvider.EcoCash to null,
            co.zw.nissangtr.pos.domain.model.DigitalProvider.Paynow to null,
            co.zw.nissangtr.pos.domain.model.DigitalProvider.ContiPay to "Not set up for this shop yet.",
        ),
        checkout = co.zw.nissangtr.pos.domain.state.CheckoutSession(
            orderId = st.orderId, cartId = sale.cart.cartId, requestId = "req-1", status = st, attempt = attempt, outcome = outcome,
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun reservedPayment() = capture("reserved_payment", 1280.dp, 800.dp, reserved())

    @Test @Config(qualifiers = "w400dp-h860dp-port-mdpi")
    fun reservedPaymentPhone() = capture("reserved_payment_phone", 400.dp, 860.dp, reserved())

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun reservedProviderWaiting() = capture(
        "reserved_provider_waiting",
        1280.dp,
        800.dp,
        reserved(
            attempt = co.zw.nissangtr.pos.domain.model.ProviderAttempt(
                co.zw.nissangtr.pos.domain.model.DigitalProvider.Paynow, "intent-1", "https://www.paynow.co.zw/payment/confirm/abc123", 0,
            ),
            st = status("payment_processing"),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun reservedUnknown() = capture(
        "reserved_unknown",
        1280.dp,
        800.dp,
        reserved(outcome = co.zw.nissangtr.pos.domain.model.TenderOutcome.Unknown, st = status("allocation_pending")),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun reservedDeclined() = capture(
        "reserved_declined",
        1280.dp,
        800.dp,
        reserved(outcome = co.zw.nissangtr.pos.domain.model.TenderOutcome.Declined).let {
            it.copy(checkout = it.checkout!!.copy(message = "Insufficient funds"))
        },
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun recoveryOrder() = capture(
        "recovery_order",
        1280.dp,
        800.dp,
        sale.copy(
            reserveCheckout = true,
            destination = PosDestination.Recovery,
            recoveryOrderId = status().orderId,
            recoveryStatus = status("allocation_pending").copy(
                settledProvider = "ecocash",
                reference = "MP261001.0742.A12345",
                exceptions = listOf(co.zw.nissangtr.pos.domain.model.PaymentExceptionInfo("allocation_failed", "Stock moved before allocation", null, null, "2026-10-01T07:44:00Z")),
            ),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun recoveryList() = capture(
        "recovery_list",
        1280.dp,
        800.dp,
        sale.copy(
            reserveCheckout = true,
            destination = PosDestination.Recovery,
            recoveryItems = listOf(
                co.zw.nissangtr.pos.domain.model.RecoveryItem("o1", "allocation_pending", usd(184.0), "ecocash", null, "2026-10-01T07:44:00Z", 1),
                co.zw.nissangtr.pos.domain.model.RecoveryItem("o2", "payment_processing", usd(62.5), "paynow", null, "2026-10-01T07:31:00Z", 0),
            ),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun ordersPickup() = capture(
        "orders_pickup",
        1280.dp,
        800.dp,
        sale.copy(
            reserveCheckout = true,
            destination = PosDestination.Orders,
            parked = emptyList(),
            quotations = emptyList(),
            pickups = listOf(
                co.zw.nissangtr.pos.domain.model.PickupOrder("o3", "INV-2026-000412", "Tendai Moyo", "dispatch_ready", usd(240.0), "cash", "2026-10-01T07:20:00Z"),
            ),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun receiptHandover() = capture(
        "receipt_handover",
        1280.dp,
        800.dp,
        sale.copy(
            reserveCheckout = true,
            receiptOrderId = "o3",
            receipt = co.zw.nissangtr.pos.domain.model.Receipt(
                invoiceId = "inv-1", documentNumber = "INV-2026-000413", lines = sale.cart.lines, subtotal = sale.cart.subtotal,
                discount = sale.cart.discount, total = sale.cart.total,
                tenders = listOf(co.zw.nissangtr.pos.domain.model.TenderLine(co.zw.nissangtr.pos.domain.model.Tender.Paynow, sale.cart.total)),
                cashGiven = null, change = null, customerName = null, vehicleLabel = null, operatorName = "Rudo",
                issuedAtIso = "2026-10-01T07:45:00+02:00",
            ),
        ),
    )

    // ------------------------------------------------------------ part payments (§10.5, §10.8)

    private fun splitSession(received: Double, status: String = "partially_captured", refunds: List<co.zw.nissangtr.pos.domain.model.SplitRefund> = emptyList()) =
        co.zw.nissangtr.pos.domain.model.SplitSession(
            sessionId = "s1", orderId = status().orderId, status = status, total = sale.cart.total, received = usd(received), pending = usd(0.0),
            balanceDue = usd(sale.cart.total.minor / 100.0 - received), availableToAllocate = usd(sale.cart.total.minor / 100.0 - received),
            finalInvoiceId = null, finalizationError = null,
            legs = listOf(co.zw.nissangtr.pos.domain.model.SplitLeg("leg1", 1, "cash", usd(received), if (refunds.isEmpty()) "captured" else "refund_review", null, null, null, null)),
            refunds = refunds,
        )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun splitPartPaid() = capture("split_part_paid", 1280.dp, 800.dp, reserved(st = status("payment_processing")).copy(split = splitSession(50.0)))

    @Test @Config(qualifiers = "w400dp-h860dp-port-mdpi")
    fun splitPartPaidPhone() = capture("split_part_paid_phone", 400.dp, 860.dp, reserved(st = status("payment_processing")).copy(split = splitSession(50.0)))

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun splitRecoveryRefund() = capture(
        "split_recovery_refund",
        1280.dp,
        800.dp,
        sale.copy(
            reserveCheckout = true,
            destination = PosDestination.Recovery,
            recoveryOrderId = status().orderId,
            recoveryStatus = status("cancelled"),
            recoverySplit = splitSession(
                50.0,
                status = "refund_review",
                refunds = listOf(co.zw.nissangtr.pos.domain.model.SplitRefund("r1", "leg1", "review", usd(50.0), "manual_review", null, null, null, "Customer changed their mind")),
            ),
        ),
    )

    // ------------------------------------------------------------ card machine (§10.7)

    private val machine = co.zw.nissangtr.pos.domain.model.CardTerminal("t1", "Counter card machine", "CBZ", mapOf("package_name" to "zw.co.cbz.pos"))
    private val machineReady = co.zw.nissangtr.pos.domain.model.TerminalSetup(listOf(machine), machine, appInstalled = true, paired = true)
    private val machineAttempt = co.zw.nissangtr.pos.domain.model.TerminalAttempt(
        "a1", "purchase", "initiated", sale.cart.total, "Counter card machine", null, null, null, null, status().orderId, null, null, null,
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun cardMachineWaiting() = capture(
        "card_machine_waiting",
        1280.dp,
        800.dp,
        reserved(st = status("payment_processing")).let {
            it.copy(terminalSetup = machineReady, terminalAttempt = machineAttempt, terminalBusy = true, checkout = it.checkout!!.copy(busy = true))
        },
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun cardMachineUnknown() = capture(
        "card_machine_unknown",
        1280.dp,
        800.dp,
        reserved(st = status("payment_processing")).let {
            it.copy(
                terminalSetup = machineReady,
                terminalAttempt = machineAttempt.copy(status = "unknown"),
                checkout = it.checkout!!.copy(outcome = co.zw.nissangtr.pos.domain.model.TenderOutcome.Unknown, message = "terminal_unknown"),
            )
        },
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun cardMachineRecovery() = capture(
        "card_machine_recovery",
        1280.dp,
        800.dp,
        sale.copy(
            reserveCheckout = true,
            destination = PosDestination.Recovery,
            terminalSetup = machineReady,
            terminalRecovery = listOf(
                co.zw.nissangtr.pos.domain.model.TerminalRecoveryItem("a1", "purchase", "approved", "Counter card machine", "o1", usd(147.5), "TX-88231", "4242", "insufficient FIFO batch qty", "2026-10-01T07:44:00Z"),
                co.zw.nissangtr.pos.domain.model.TerminalRecoveryItem("a2", "purchase", "unknown", "Counter card machine", "o2", usd(62.5), null, null, null, "2026-10-01T07:31:00Z"),
            ),
            splitRecovery = emptyList(),
            recoveryItems = emptyList(),
        ),
    )

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun settingsCardMachine() = capture(
        "settings_card_machine",
        1280.dp,
        800.dp,
        sale.copy(destination = PosDestination.Settings, terminalSetup = machineReady.copy(paired = false)),
    )
}
