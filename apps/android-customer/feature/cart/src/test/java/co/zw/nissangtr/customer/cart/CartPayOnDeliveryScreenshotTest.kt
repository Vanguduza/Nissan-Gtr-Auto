package co.zw.nissangtr.customer.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import co.zw.nissangtr.customer.rpc.DeliveryPaymentMethod
import co.zw.nissangtr.customer.rpc.FakeRpcClient
import co.zw.nissangtr.customer.rpc.FulfillmentMode
import co.zw.nissangtr.customer.visual.CustomerStyle
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumCustomerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

/** Cart with nationwide delivery and pay on delivery (`checkout_customer_cart_v2`). */
@OptIn(ExperimentalCoroutinesApi::class)
class CartPayOnDeliveryScreenshotTest {
    // The fake backend never suspends, so view-model work completes before the snapshot.
    @Before fun mainImmediate() = Dispatchers.setMain(Dispatchers.Unconfined)

    @After fun resetMainDispatcher() = Dispatchers.resetMain()

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    private fun dispatchCart(): FakeRpcClient = FakeRpcClient().also { rpc ->
        runBlocking {
            val cart = rpc.ensureOpenCart(fulfillmentMode = FulfillmentMode.DISPATCH)
            rpc.addCustomerCartLine(cart.id, FakeRpcClient.SEED_OIL_FILTER_ID, FakeRpcClient.SEED_UOM_ID, 2.0)
        }
    }

    @Test
    fun payOnDelivery() {
        val rpc = dispatchCart()
        val vm = CartViewModel(rpc).apply { onDeliveryPaymentChange(DeliveryPaymentMethod.CARD_ON_DELIVERY) }
        assertEquals(DeliveryPaymentMethod.CARD_ON_DELIVERY, vm.state.value.deliveryPayment)
        paparazzi.snapshot(name = "cart_pay_on_delivery") {
            PremiumCustomerTheme(style = CustomerStyle.Express, darkTheme = false) {
                Box(Modifier.fillMaxSize().background(GtrPremiumColors.Background)) {
                    CartScreen(rpc = rpc, onBack = {}, onPay = {}, onManageOrders = {}, viewModel = vm)
                }
            }
        }
    }

    @Test
    fun clickAndCollectRefusesPayOnDelivery() = runBlocking {
        val rpc = FakeRpcClient()
        val cart = rpc.ensureOpenCart(fulfillmentMode = FulfillmentMode.IMMEDIATE)
        val refused = runCatching { rpc.setCustomerCartDeliveryPaymentMethod(cart.id, DeliveryPaymentMethod.CASH_ON_DELIVERY) }
        assertEquals("pay-on-delivery is available only for dispatch orders", refused.exceptionOrNull()?.message)
    }

    @Test
    fun payOnDeliveryPlacesTheOrderWithoutAnOnlinePaymentStep() = runBlocking {
        val rpc = dispatchCart()
        val cart = rpc.getOpenCart()!!
        val invoice = rpc.checkoutCustomerCartOnDelivery(cart.id, DeliveryPaymentMethod.CASH_OR_CARD_ON_DELIVERY)
        assertNotNull(rpc.getCustomerOrder(invoice))
        assertEquals(null, rpc.getOpenCart())
    }

    @Test
    fun suspendedAccountCannotPayOnDelivery() {
        val rpc = dispatchCart().apply { fakeSuspension = co.zw.nissangtr.customer.rpc.AccountSuspension("Failed to settle", listOf(co.zw.nissangtr.customer.rpc.OwedAmount("USD", 120.0))) }
        val vm = CartViewModel(rpc).apply { onDeliveryPaymentChange(DeliveryPaymentMethod.CASH_ON_DELIVERY) }
        assertEquals("pay on delivery is not offered", DeliveryPaymentMethod.PREPAY, vm.state.value.deliveryPayment)
        assertNotNull(vm.state.value.suspension)
        paparazzi.snapshot(name = "cart_suspended") {
            PremiumCustomerTheme(style = CustomerStyle.Express, darkTheme = false) {
                Box(Modifier.fillMaxSize().background(GtrPremiumColors.Background)) {
                    CartScreen(rpc = rpc, onBack = {}, onPay = {}, onManageOrders = {}, viewModel = vm)
                }
            }
        }
    }
}
