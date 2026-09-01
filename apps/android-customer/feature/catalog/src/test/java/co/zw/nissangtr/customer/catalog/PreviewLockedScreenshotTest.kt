package co.zw.nissangtr.customer.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import co.zw.nissangtr.customer.rpc.CatalogListItem
import co.zw.nissangtr.customer.rpc.CatalogProduct
import co.zw.nissangtr.customer.rpc.ProductReviewStats
import co.zw.nissangtr.customer.rpc.SelectedFitmentVehicle
import co.zw.nissangtr.customer.rpc.StockState
import co.zw.nissangtr.customer.rpc.VehicleMasterRow
import co.zw.nissangtr.customer.visual.GtrPremiumColors
import co.zw.nissangtr.customer.visual.PremiumAccountRow
import co.zw.nissangtr.customer.visual.PremiumBottomNav
import co.zw.nissangtr.customer.visual.PremiumCustomerTheme
import co.zw.nissangtr.customer.visual.PremiumEmptyState
import co.zw.nissangtr.customer.visual.PremiumPrimaryButton
import co.zw.nissangtr.customer.visual.PremiumScreenHeader
import co.zw.nissangtr.customer.visual.PremiumSurfaceCard
import co.zw.nissangtr.customer.visual.PremiumTab
import co.zw.nissangtr.customer.visual.PremiumTopBar
import co.zw.nissangtr.customer.visual.R
import co.zw.nissangtr.ui.shop.ShopTheme
import org.junit.Rule
import org.junit.Test

/**
 * Phone-viewport visual gates for the preview-locked pack.
 *
 * PIXEL_6 is 1080×2400 @ 420dpi — a normal Android phone frame. These snapshots
 * render the shipped Compose surfaces because this Cloud VM's emulator guest
 * never becomes adb-online.
 */
class PreviewLockedScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_6,
        theme = "android:Theme.Material.NoActionBar",
        maxPercentDifference = 0.1,
    )

    @Test
    fun gateHome() {
        paparazzi.snapshot(name = "gate_home") {
            PhoneShell(PremiumTab.Home) {
                PremiumCatalogHome(
                    state = sampleHomeState(),
                    wishOems = emptySet(),
                    onShopAll = {},
                    onSeeAllCategories = {},
                    onSeeAllPopular = {},
                    onSeeAllNewest = {},
                    onOpenProduct = {},
                    onToggleWish = {},
                    onAddToCart = {},
                    onCategoryBrowse = {},
                    onConfirmCascade = { _, _, _, _ -> },
                    onConfirmVin = {},
                    onClearVehicle = {},
                    onTrackOrder = {},
                )
            }
        }
    }

    @Test
    fun gateShop() {
        paparazzi.snapshot(name = "gate_shop") {
            PhoneShell(PremiumTab.Shop) {
                PremiumShopBrowse(
                    state = sampleHomeState(),
                    wishOems = emptySet(),
                    onOpenProduct = {},
                    onToggleWish = {},
                    onAddToCart = {},
                    onConfirmCascade = { _, _, _, _ -> },
                    onConfirmVin = {},
                    onClearVehicle = {},
                )
            }
        }
    }

    @Test
    fun gatePdp() {
        paparazzi.snapshot(name = "gate_pdp") {
            PhoneShell(PremiumTab.Shop) {
                PremiumCatalogPdp(
                    product = sampleProduct(),
                    selectedVehicle = sampleVehicle(),
                    qty = "1",
                    busy = false,
                    reviewStats = ProductReviewStats("stock-brake-pads", 4.6, 18),
                    liked = false,
                    onQtyChange = {},
                    onBack = {},
                    onAddToCart = {},
                    onToggleWishlist = {},
                    onOpenReviews = {},
                )
            }
        }
    }

    @Test
    fun gateChangeVehicle() {
        val state = sampleHomeState()
        paparazzi.snapshot(name = "gate_change_vehicle") {
            PhoneShell(PremiumTab.Home) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(GtrPremiumColors.Surface)
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
                ) {
                    Text(
                        text = "Change vehicle",
                        style = MaterialTheme.typography.headlineSmall,
                        color = GtrPremiumColors.TextPrimary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your selection only filters parts currently in stock that fit this vehicle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GtrPremiumColors.TextSecondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    VehicleSelectorSection(
                        vehicleRows = state.vehicleRows,
                        confirmedVehicle = state.selectedFitment,
                        busy = false,
                        error = null,
                        onConfirmCascade = { _, _, _, _ -> },
                        onConfirmVin = {},
                        onClear = {},
                        sectionTitle = "Vehicle details",
                        confirmLabel = "Use this vehicle",
                        showClear = false,
                    )
                }
            }
        }
    }

    @Test
    fun gateCart() {
        paparazzi.snapshot(name = "gate_cart") {
            PhoneShell(PremiumTab.Home, showBottomNav = false) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(GtrPremiumColors.Background),
                ) {
                    PremiumScreenHeader(
                        title = "Your Cart",
                        onBack = {},
                    )
                    Box(
                        Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PremiumEmptyState(
                                title = "Your cart is empty",
                                body = "Add in-stock parts to continue.",
                                art = R.drawable.gtr_empty_state_cart_empty,
                            )
                            Spacer(Modifier.height(12.dp))
                            PremiumPrimaryButton(
                                "Shop now",
                                onClick = {},
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun gateGarage() {
        paparazzi.snapshot(name = "gate_garage") {
            PhoneShell(PremiumTab.Garage) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(GtrPremiumColors.Background),
                ) {
                    PremiumScreenHeader(
                        title = "My Garage",
                        subtitle = "Saved vehicles for faster fitment filtering",
                        onBack = {},
                    )
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        PremiumSurfaceCard {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(
                                        R.drawable.gtr_hero_workshop_r35,
                                    ),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(132.dp),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "No vehicles saved yet",
                                    color = GtrPremiumColors.TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Add your Nissan to keep fitment selection quick.",
                                    color = GtrPremiumColors.TextSecondary,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(vertical = 6.dp),
                                )
                                PremiumPrimaryButton(
                                    text = "Add a vehicle",
                                    onClick = {},
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun gateAccount() {
        paparazzi.snapshot(name = "gate_account") {
            PhoneShell(PremiumTab.Account) {
                AccountTabBody()
            }
        }
    }
}

@Composable
private fun PhoneShell(
    tab: PremiumTab,
    showBottomNav: Boolean = true,
    content: @Composable () -> Unit,
) {
    ShopTheme(darkTheme = true) {
        PremiumCustomerTheme {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(GtrPremiumColors.Background),
            ) {
                PremiumTopBar(cartCount = 0, onMenu = {}, onCart = {})
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    content()
                }
                if (showBottomNav) {
                    PremiumBottomNav(selected = tab, onSelect = {})
                }
            }
        }
    }
}

@Composable
private fun AccountTabBody() {
    Column(
        Modifier
            .fillMaxSize()
            .background(GtrPremiumColors.Background)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 18.dp)) {
            Text(
                "My Account",
                color = GtrPremiumColors.TextPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.padding(top = 8.dp))
            PremiumSurfaceCard {
                Column {
                    Text(
                        "Guest",
                        color = GtrPremiumColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Sign in to sync orders, garage and saved items.",
                        color = GtrPremiumColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        PremiumAccountRow(Icons.Filled.AccountCircle, "Profile", "Personal and contact details", {})
        PremiumAccountRow(Icons.Filled.ReceiptLong, "Orders", "History and order status", {})
        PremiumAccountRow(Icons.Filled.LocalShipping, "Returns", "Request and track returns", {})
        PremiumAccountRow(Icons.Filled.Star, "GTR Rewards", "Points and eligible benefits", {})
        PremiumAccountRow(Icons.Filled.Build, "Service kits", "Maintenance bundles", {})
        PremiumAccountRow(Icons.Filled.LocationOn, "Addresses", "Delivery addresses", {})
        PremiumAccountRow(Icons.Filled.CreditCard, "Payment", "Secure payment", {})
        PremiumAccountRow(Icons.Filled.DirectionsCar, "My Garage", "Saved vehicles", {})
        PremiumAccountRow(Icons.Filled.CompareArrows, "Compare", "Compare selected products", {})
        PremiumAccountRow(Icons.Filled.LocalShipping, "Track delivery", "Latest permitted point and ETA", {})
        PremiumAccountRow(Icons.Filled.Chat, "Support", "Chat with the parts counter", {})
        PremiumAccountRow(Icons.Filled.Notifications, "Notifications", "Order and account updates", {})
        PremiumAccountRow(Icons.Filled.CardGiftcard, "Coupons", "Available promotions", {})
        PremiumAccountRow(Icons.Filled.Settings, "Settings", "Preferences, privacy and help", {})
        Column(Modifier.padding(16.dp)) {
            PremiumPrimaryButton(text = "Sign in", onClick = {}, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.padding(bottom = 20.dp))
        }
    }
}

private fun sampleVehicle() = SelectedFitmentVehicle(
    make = "Nissan",
    model = "Nissan GT-R R35",
    generation = "R35",
    engine = "VR38DETT",
)

private fun sampleHomeState() = CatalogUiState(
    selectedFitment = sampleVehicle(),
    vehicleRows = listOf(
        VehicleMasterRow(
            id = "r35",
            vinPrefix = "JN1",
            chassisCode = "R35",
            engineCode = "VR38DETT",
            productionYear = 2018,
            modelVariant = "Nissan GT-R R35",
        ),
    ),
    browseItems = listOf(
        CatalogListItem("1", "internal-1", "Front brake pad set", StockState.IN_STOCK, 89.0, "Brakes"),
        CatalogListItem("2", "internal-2", "Performance air filter", StockState.IN_STOCK, 54.0, "Engine"),
        CatalogListItem("3", "internal-3", "Coilover kit", StockState.LOW, 420.0, "Suspension"),
        CatalogListItem("4", "internal-4", "Oil filter", StockState.IN_STOCK, 12.0, "Engine"),
        CatalogListItem("5", "internal-5", "Spark plug set", StockState.IN_STOCK, 36.0, "Engine"),
        CatalogListItem("6", "internal-6", "Brake rotor pair", StockState.IN_STOCK, 160.0, "Brakes"),
        CatalogListItem("7", "internal-7", "Cabin filter", StockState.IN_STOCK, 18.0, "Interior"),
        CatalogListItem("8", "internal-8", "Wiper blade set", StockState.IN_STOCK, 22.0, "Exterior"),
        CatalogListItem("9", "internal-9", "Radiator cap", StockState.IN_STOCK, 9.0, "Cooling"),
        CatalogListItem("10", "internal-10", "Serpentine belt", StockState.IN_STOCK, 28.0, "Engine"),
    ),
)

private fun sampleProduct() = CatalogProduct(
    stockItemId = "1",
    baseUomId = "ea",
    oem = "internal-1",
    name = "Front brake pad set",
    brand = "Nissan",
    category = "Brakes",
    usd = 89.0,
    stock = StockState.IN_STOCK,
    imageUrls = emptyList(),
    specs = listOf("Axle: front", "Includes hardware kit"),
)
