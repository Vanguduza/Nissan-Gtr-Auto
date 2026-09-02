package co.zw.nissangtr.customer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import co.zw.nissangtr.bridges.podcamera.CameraxPodCameraBridge
import co.zw.nissangtr.bridges.podcamera.PodCameraBridge
import co.zw.nissangtr.customer.address.AddressModule
import co.zw.nissangtr.customer.address.AddressScreen
import co.zw.nissangtr.customer.auth.AuthGate
import co.zw.nissangtr.customer.auth.AuthGateState
import co.zw.nissangtr.customer.auth.AuthModule
import co.zw.nissangtr.customer.auth.AuthSessionViewModel
import co.zw.nissangtr.customer.auth.SignInScreen
import co.zw.nissangtr.customer.cart.CartModule
import co.zw.nissangtr.customer.cart.CartScreen
import co.zw.nissangtr.customer.catalog.CatalogModule
import co.zw.nissangtr.customer.catalog.CatalogScreen
import co.zw.nissangtr.customer.catalog.CatalogLanding
import co.zw.nissangtr.customer.catalog.CategoriesGridScreen
import co.zw.nissangtr.customer.chat.ChatModule
import co.zw.nissangtr.customer.chat.ChatScreen
import co.zw.nissangtr.customer.compare.CompareModule
import co.zw.nissangtr.customer.compare.CompareScreen
import co.zw.nissangtr.customer.compare.GuestCompareStore
import co.zw.nissangtr.customer.kits.KitsScreen
import co.zw.nissangtr.customer.loyalty.LoyaltyWalletScreen
import co.zw.nissangtr.customer.garage.GarageModule
import co.zw.nissangtr.customer.garage.GarageScreen
import co.zw.nissangtr.customer.orders.OrdersModule
import co.zw.nissangtr.customer.orders.OrdersScreen
import co.zw.nissangtr.customer.pay.PayIntentScreen
import co.zw.nissangtr.customer.pay.PayModule
import co.zw.nissangtr.customer.profile.EditProfileScreen
import co.zw.nissangtr.customer.returns.ReturnsScreen
import co.zw.nissangtr.customer.prefs.CustomerPrefs
import co.zw.nissangtr.customer.prefs.ThemeMode
import co.zw.nissangtr.customer.rpc.FakeRpcClient
import co.zw.nissangtr.customer.rpc.RpcClient
import co.zw.nissangtr.customer.rpc.RpcClientFactory
import co.zw.nissangtr.customer.rpc.SupabaseRpcClient
import co.zw.nissangtr.customer.settings.SettingsHubScreen
import co.zw.nissangtr.customer.shell.CustomerShellTopBar
import co.zw.nissangtr.customer.shell.HamburgerMenuAction
import co.zw.nissangtr.customer.shell.HamburgerMenuOverlay
import co.zw.nissangtr.customer.track.DeliveryTrackScreen
import co.zw.nissangtr.customer.track.TrackModule
import co.zw.nissangtr.customer.wishlist.WishlistModule
import co.zw.nissangtr.customer.wishlist.WishlistScreen
import co.zw.nissangtr.customer.wishlist.WishlistStore
import co.zw.nissangtr.customer.ui.CustomerShopTheme
import co.zw.nissangtr.customer.shell.CustomerPremiumBottomBar
import co.zw.nissangtr.customer.ui.PremiumCouponsScreen
import co.zw.nissangtr.customer.ui.PremiumNotificationsScreen
import co.zw.nissangtr.customer.ui.CustomerPremiumSplash
import co.zw.nissangtr.customer.ui.PremiumAccountTab
import co.zw.nissangtr.ui.shop.ShopBottomBar
import co.zw.nissangtr.ui.shop.ShopBottomTab
import co.zw.nissangtr.ui.shop.ShopDefaultScreen
import co.zw.nissangtr.ui.shop.ShopHonestEmpty
import co.zw.nissangtr.ui.shop.ShopProfileAvatar
import co.zw.nissangtr.ui.shop.ShopProfileItemBox
import co.zw.nissangtr.ui.shop.ShopSplash
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Primary shell tabs — Home / Shop / Wishlist / My Garage / Settings.
 * Cart + Account / Sign-in live in the top strip; extras under My Account.
 */
private enum class ShellTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Shop("Shop", Icons.Filled.Storefront),
    Garage("Garage", Icons.Filled.DirectionsCar),
    Wishlist("Wishlist", Icons.Filled.Favorite),
    Account("Account", Icons.Filled.AccountCircle),
}

/** Nested account destinations — Reviews / Wallet / Settings / Help removed from hub. */
private enum class ProfileDest(val title: String, val subtitle: String) {
    Hub("My Account", "Orders · pay · extras"),
    EditProfile("Edit profile", "Personal · contact"),
    Orders("Orders", "History · track"),
    Returns("Returns", "Quarantine protocol"),
    Loyalty("Loyalty wallet", "Points"),
    Kits("Service kits", "Bundles"),
    Addresses("Addresses", "Delivery · map pick"),
    Compare("Compare", "Attribute matrix"),
    Pay("Pay", "ContiPay · Paynow · EcoCash"),
    Chat("Live chat", "Counter support"),
    Track("Live delivery", "Last point · ETA only"),
    Notifications("Notifications", "Inbox"),
    Coupons("Coupons", "Promos"),
}

private enum class ShellOverlay {
    None,
    Menu,
    Cart,
    Account,
    SignIn,
    Categories,
    Pay,
    Orders,
}

private data class TrackLaunchArgs(
    val token: String? = null,
    val jobId: String? = null,
    val seq: Int = 0,
)

private data class PartsLaunchArgs(
    val oem: String? = null,
    val seq: Int = 0,
)

/**
 * Customer shell — Shopping-By-KMP fork (MIT) IA over live [RpcClient].
 * Deep links: `gtrcustomer://track/{token}`, `gtrcustomer://parts/{oem}`, `gtrcustomer://auth/callback`.
 */
class MainActivity : ComponentActivity() {
    private val trackLaunch = mutableStateOf(TrackLaunchArgs())
    private val partsLaunch = mutableStateOf(PartsLaunchArgs())
    private var trackSeq = 0
    private var partsSeq = 0
    private lateinit var cameraBridge: CameraxPodCameraBridge
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var liveSupabase: SupabaseRpcClient? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraBridge = CameraxPodCameraBridge(this)
        cameraBridge.attachActivity(this)
        listOf(
            AuthModule.id,
            CartModule.id,
            OrdersModule.id,
            GarageModule.id,
            PayModule.id,
            ChatModule.id,
            TrackModule.id,
            WishlistModule.id,
            CompareModule.id,
            CatalogModule.id,
            AddressModule.id,
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
        liveSupabase = supabase
        val mapsKeyPresent = BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank()
        val googleServerClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        val prefs = CustomerPrefs(this)
        applyTrackIntent(intent)
        applyPartsIntent(intent)
        applyAuthIntent(intent)
        setContent {
            var themeMode by remember { mutableStateOf(prefs.themeMode) }
            val darkTheme = when (themeMode) {
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
                ThemeMode.System -> isSystemInDarkTheme()
            }
            CustomerShopTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    var splashDone by remember { mutableStateOf(false) }
                    if (!splashDone) {
                        CustomerPremiumSplash(onFinished = { splashDone = true })
                    } else {
                        AuthGate(
                            liveRpc = live,
                            supabase = supabase,
                            googleServerClientId = googleServerClientId,
                        ) { email, onSignOut, authVm ->
                            val launch by trackLaunch
                            val parts by partsLaunch
                            val signedIn = !live || email != null
                            LaunchedEffect(email) {
                                if (live && email != null) {
                                    syncGuestCompare(rpc)
                                }
                            }
                            CustomerApp(
                                rpc = rpc,
                                liveRpc = live,
                                supabase = supabase,
                                authSessionViewModel = authVm,
                                signedIn = signedIn,
                                signedInEmail = email,
                                onSignOut = onSignOut,
                                prefs = prefs,
                                themeMode = themeMode,
                                onThemeModeChange = {
                                    themeMode = it
                                    prefs.themeMode = it
                                },
                                whatsappE164 = BuildConfig.WHATSAPP_E164,
                                mapsKeyPresent = mapsKeyPresent,
                                trackLaunch = launch,
                                partsLaunch = parts,
                                camera = cameraBridge,
                                googleServerClientId = googleServerClientId,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::cameraBridge.isInitialized) cameraBridge.attachActivity(this)
    }

    override fun onPause() {
        if (::cameraBridge.isInitialized) cameraBridge.detachActivity()
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
        if (requestCode == CameraxPodCameraBridge.REQUEST_CAMERA && ::cameraBridge.isInitialized) {
            cameraBridge.onPermissionResult()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CameraxPodCameraBridge.REQUEST_CAPTURE && ::cameraBridge.isInitialized) {
            cameraBridge.onCaptureActivityResult(resultCode, data)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyTrackIntent(intent)
        applyPartsIntent(intent)
        applyAuthIntent(intent)
    }

    private fun applyAuthIntent(intent: Intent?) {
        if (intent == null) return
        liveSupabase?.handleAuthDeeplink(intent)
    }

    private fun syncGuestCompare(rpc: RpcClient) {
        syncScope.launch {
            val local = GuestCompareStore.readOems(this@MainActivity)
            if (local.isEmpty()) return@launch
            for (oem in local) {
                runCatching { rpc.addCustomerCompareItem(oem = oem) }
            }
            runCatching {
                val listed = rpc.listCompareItems()
                GuestCompareStore.writeOems(this@MainActivity, listed.map { it.oemPartNumber })
            }
        }
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

    private fun applyPartsIntent(intent: Intent?) {
        if (intent == null) return
        val oem = parsePartsOem(intent.data) ?: return
        partsSeq += 1
        partsLaunch.value = PartsLaunchArgs(oem = oem, seq = partsSeq)
    }

    companion object {
        const val EXTRA_TRACK_TOKEN = "track_token"
        const val EXTRA_TRACK_JOB_ID = "track_job_id"

        fun parseTrackToken(uri: Uri?): String? {
            if (uri == null) return null
            val segments = uri.pathSegments.orEmpty()
            when {
                uri.scheme.equals("gtrcustomer", ignoreCase = true) &&
                    uri.host.equals("track", ignoreCase = true) -> {
                    val token = segments.firstOrNull() ?: uri.lastPathSegment
                    return token?.trim()?.takeIf { it.length >= 8 }
                }
                segments.size >= 2 &&
                    segments[segments.size - 2].equals("track", ignoreCase = true) -> {
                    return segments.last().trim().takeIf { it.length >= 8 }
                }
            }
            return null
        }

        fun parsePartsOem(uri: Uri?): String? {
            if (uri == null) return null
            val segments = uri.pathSegments.orEmpty()
            when {
                uri.scheme.equals("gtrcustomer", ignoreCase = true) &&
                    uri.host.equals("parts", ignoreCase = true) -> {
                    return (segments.firstOrNull() ?: uri.lastPathSegment)
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                }
                segments.size >= 2 &&
                    segments[segments.size - 2].equals("parts", ignoreCase = true) -> {
                    return segments.last().trim().takeIf { it.isNotEmpty() }
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
    supabase: SupabaseRpcClient?,
    authSessionViewModel: AuthSessionViewModel?,
    signedIn: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
    prefs: CustomerPrefs,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    whatsappE164: String,
    mapsKeyPresent: Boolean,
    trackLaunch: TrackLaunchArgs = TrackLaunchArgs(),
    partsLaunch: PartsLaunchArgs = PartsLaunchArgs(),
    camera: PodCameraBridge?,
    googleServerClientId: String = "",
) {
    var tab by remember { mutableStateOf(ShellTab.Home) }
    var overlay by remember { mutableStateOf(ShellOverlay.None) }
    var profileDest by remember { mutableStateOf(ProfileDest.Hub) }
    var trackToken by remember { mutableStateOf<String?>(null) }
    var trackJobId by remember { mutableStateOf<String?>(null) }
    var trackSession by remember { mutableIntStateOf(0) }
    var lastLaunchSeq by remember { mutableIntStateOf(0) }
    var catalogOem by remember { mutableStateOf<String?>(null) }
    var catalogSeed by remember { mutableStateOf<String?>(null) }
    var catalogSeedSeq by remember { mutableIntStateOf(0) }
    var lastPartsSeq by remember { mutableIntStateOf(0) }
    var cartBadge by remember { mutableIntStateOf(0) }
    var cartRefresh by remember { mutableIntStateOf(0) }
    var payInvoiceId by remember { mutableStateOf<String?>(null) }
    var lastBackExitAt by remember { mutableLongStateOf(0L) }
    var accountSettingsOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val wishlistStore: WishlistStore = viewModel(factory = WishlistStore.factory(rpc))

    fun openAccount(dest: ProfileDest = ProfileDest.Hub) {
        profileDest = dest
        overlay = ShellOverlay.Account
    }

    fun openTrack(jobId: String?, token: String?, resetFake: Boolean = false) {
        if (resetFake && rpc is FakeRpcClient) {
            rpc.resetFakeTrackPoint()
        }
        trackJobId = jobId
        trackToken = token
        trackSession += 1
        openAccount(ProfileDest.Track)
    }

    fun openCatalog(seed: String? = null, oem: String? = null) {
        catalogSeed = seed
        catalogSeedSeq += 1
        if (oem != null) catalogOem = oem
        tab = ShellTab.Shop
        overlay = ShellOverlay.None
    }

    fun refreshCartBadge() {
        cartRefresh += 1
    }

    fun handleSystemBack(): Boolean {
        when (overlay) {
            ShellOverlay.None -> Unit
            ShellOverlay.Pay -> {
                payInvoiceId = null
                overlay = ShellOverlay.Cart
                return true
            }
            ShellOverlay.Orders -> {
                overlay = ShellOverlay.Cart
                return true
            }
            ShellOverlay.Categories -> {
                overlay = ShellOverlay.Menu
                return true
            }
            ShellOverlay.Account -> {
                if (profileDest != ProfileDest.Hub) {
                    profileDest = ProfileDest.Hub
                } else {
                    overlay = ShellOverlay.None
                }
                return true
            }
            ShellOverlay.Menu,
            ShellOverlay.Cart,
            ShellOverlay.SignIn,
            -> {
                overlay = ShellOverlay.None
                return true
            }
        }
        if (tab != ShellTab.Home) {
            tab = ShellTab.Home
            return true
        }
        val now = System.currentTimeMillis()
        if (now - lastBackExitAt < 2_000L) {
            (context as? ComponentActivity)?.finish()
        } else {
            lastBackExitAt = now
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    BackHandler {
        handleSystemBack()
    }

    // Dismiss SignIn overlay when the shared session becomes SignedIn.
    LaunchedEffect(authSessionViewModel) {
        val vm = authSessionViewModel ?: return@LaunchedEffect
        vm.gate.collect { gate ->
            if (gate is AuthGateState.SignedIn && overlay == ShellOverlay.SignIn) {
                overlay = ShellOverlay.None
            }
        }
    }

    LaunchedEffect(trackLaunch.seq) {
        if (trackLaunch.seq == 0 || trackLaunch.seq == lastLaunchSeq) return@LaunchedEffect
        lastLaunchSeq = trackLaunch.seq
        openTrack(
            jobId = trackLaunch.jobId,
            token = trackLaunch.token,
            resetFake = !liveRpc && trackLaunch.token == FakeRpcClient.SEED_TRACK_TOKEN,
        )
    }

    LaunchedEffect(partsLaunch.seq) {
        if (partsLaunch.seq == 0 || partsLaunch.seq == lastPartsSeq) return@LaunchedEffect
        lastPartsSeq = partsLaunch.seq
        openCatalog(oem = partsLaunch.oem)
    }

    LaunchedEffect(overlay, tab, cartRefresh) {
        try {
            val cart = rpc.getOpenCart()
            cartBadge = cart?.lines?.sumOf { it.qty.toInt().coerceAtLeast(1) } ?: 0
        } catch (_: Exception) {
            cartBadge = 0
        }
    }

    val bottomTabs = remember {
        ShellTab.entries.map { ShopBottomTab(it.name, it.label, it.icon) }
    }

    when (overlay) {
        ShellOverlay.Menu -> {
            HamburgerMenuOverlay(
                onAction = { action ->
                    when (action) {
                        is HamburgerMenuAction.Close -> overlay = ShellOverlay.None
                        is HamburgerMenuAction.OpenAllCategories -> {
                            overlay = ShellOverlay.Categories
                        }
                        is HamburgerMenuAction.OpenEpcBrowse -> {
                            // Customer EPC browsing is retired; staff EPC remains independent.
                            overlay = ShellOverlay.None
                        }
                        is HamburgerMenuAction.BrowseCategory -> {
                            openCatalog(seed = action.label)
                            overlay = ShellOverlay.None
                        }
                        HamburgerMenuAction.OpenDeals,
                        HamburgerMenuAction.OpenAbout,
                        HamburgerMenuAction.OpenContact,
                        HamburgerMenuAction.OpenStoreLocator -> Unit
                    }
                },
            )
        }
        ShellOverlay.Categories -> {
            CategoriesGridScreen(
                onBack = { overlay = ShellOverlay.None },
                onCategoryClick = { label ->
                    openCatalog(seed = label)
                    overlay = ShellOverlay.None
                },
            )
        }
        ShellOverlay.Cart -> {
            CartScreen(
                rpc = rpc,
                refreshKey = cartRefresh,
                onBack = {
                    refreshCartBadge()
                    overlay = ShellOverlay.None
                },
                onContinueShopping = {
                    refreshCartBadge()
                    overlay = ShellOverlay.None
                    tab = ShellTab.Shop
                },
                onPay = { invoiceId ->
                    payInvoiceId = invoiceId
                    refreshCartBadge()
                    overlay = ShellOverlay.Pay
                },
                onManageAddresses = { openAccount(ProfileDest.Addresses) },
                onManageOrders = { overlay = ShellOverlay.Orders },
            )
        }
        ShellOverlay.Pay -> {
            PayIntentScreen(
                rpc = rpc,
                initialInvoiceId = payInvoiceId,
                onBack = {
                    payInvoiceId = null
                    overlay = ShellOverlay.Cart
                },
            )
        }
        ShellOverlay.Orders -> {
            OrdersScreen(
                rpc = rpc,
                onBack = { overlay = ShellOverlay.Cart },
                onTrackDelivery = { jobId, token ->
                    openTrack(jobId = jobId, token = token)
                },
            )
        }
        ShellOverlay.Account -> {
            ProfileStack(
                dest = profileDest,
                onDest = { profileDest = it },
                onClose = { overlay = ShellOverlay.None },
                rpc = rpc,
                liveRpc = liveRpc,
                signedIn = signedIn,
                signedInEmail = signedInEmail,
                onSignOut = onSignOut,
                onSignIn = { overlay = ShellOverlay.SignIn },
                whatsappE164 = whatsappE164,
                mapsKeyPresent = mapsKeyPresent,
                trackToken = trackToken,
                trackJobId = trackJobId,
                trackSession = trackSession,
                onOpenTrack = { jobId, token ->
                    openTrack(
                        jobId = jobId,
                        token = token,
                        resetFake = !liveRpc && (
                            jobId == FakeRpcClient.SEED_ACTIVE_JOB_ID ||
                                token == FakeRpcClient.SEED_TRACK_TOKEN
                            ),
                    )
                },
                onDemoTrack = {
                    openTrack(
                        jobId = if (!liveRpc) FakeRpcClient.SEED_ACTIVE_JOB_ID else null,
                        token = if (!liveRpc) FakeRpcClient.SEED_TRACK_TOKEN else null,
                        resetFake = !liveRpc,
                    )
                },
                onOpenGarage = {
                    overlay = ShellOverlay.None
                    tab = ShellTab.Garage
                },
                onOpenProduct = { oem ->
                    overlay = ShellOverlay.None
                    openCatalog(oem = oem)
                },
            )
        }
        ShellOverlay.SignIn -> {
            SignInScreen(
                supabase = supabase,
                allowSkip = !liveRpc,
                onSkip = { overlay = ShellOverlay.None },
                sessionViewModel = authSessionViewModel,
                googleServerClientId = googleServerClientId,
            )
        }
        ShellOverlay.None -> {
            Scaffold(
                topBar = {
                    CustomerShellTopBar(
                        cartBadgeCount = cartBadge,
                        onOpenMenu = { overlay = ShellOverlay.Menu },
                        onOpenAccount = { openAccount() },
                        onOpenCart = { overlay = ShellOverlay.Cart },
                    )
                },
                bottomBar = {
                    CustomerPremiumBottomBar(
                        tabs = bottomTabs,
                        selectedKey = tab.name,
                        onSelect = { key ->
                            tab = ShellTab.valueOf(key)
                            accountSettingsOpen = false
                            overlay = ShellOverlay.None
                        },
                    )
                },
            ) { padding ->
                // weight(1f) bounds tab height so scrollable children are not measured
                // with infinite max height under this Column.
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    val tabMod = Modifier.weight(1f).fillMaxWidth()
                    when (tab) {
                        ShellTab.Home -> {
                            CatalogScreen(
                                rpc = rpc,
                                wishlistStore = wishlistStore,
                                onBack = { },
                                onOpenCart = { overlay = ShellOverlay.Cart },
                                onCartChanged = { refreshCartBadge() },
                                onManageVehicle = { tab = ShellTab.Garage },
                                onTrackOrder = { openAccount(ProfileDest.Track) },
                                initialOem = null,
                                showShellChrome = true,
                                landing = CatalogLanding.Home,
                                viewModelKey = "home",
                                camera = camera,
                                modifier = tabMod,
                            )
                        }
                        ShellTab.Shop -> {
                            CatalogScreen(
                                rpc = rpc,
                                wishlistStore = wishlistStore,
                                onBack = { tab = ShellTab.Home },
                                onOpenCart = { overlay = ShellOverlay.Cart },
                                onCartChanged = { refreshCartBadge() },
                                onManageVehicle = { tab = ShellTab.Garage },
                                onTrackOrder = { openAccount(ProfileDest.Track) },
                                initialOem = catalogOem,
                                initialCategorySeed = catalogSeed,
                                categorySeedSeq = catalogSeedSeq,
                                showShellChrome = true,
                                landing = CatalogLanding.Shop,
                                viewModelKey = "shop",
                                camera = camera,
                                modifier = tabMod,
                            )
                        }
                        ShellTab.Wishlist -> {
                            WishlistScreen(
                                rpc = rpc,
                                wishlistStore = wishlistStore,
                                onBack = { tab = ShellTab.Home },
                                onOpenProduct = { oem -> openCatalog(oem = oem) },
                                modifier = tabMod,
                            )
                        }
                        ShellTab.Garage -> {
                            GarageScreen(
                                rpc = rpc,
                                onBack = { tab = ShellTab.Home },
                                modifier = tabMod,
                            )
                        }
                        ShellTab.Account -> {
                            if (accountSettingsOpen) {
                                SettingsHubScreen(
                                    prefs = prefs,
                                    themeMode = themeMode,
                                    onThemeModeChange = onThemeModeChange,
                                    signedInEmail = signedInEmail,
                                    onSignOut = if (signedInEmail != null) onSignOut else null,
                                    onSignIn = if (signedInEmail == null) {
                                        { overlay = ShellOverlay.SignIn }
                                    } else {
                                        null
                                    },
                                    onOpenAccount = { accountSettingsOpen = false },
                                    onEditProfile = {
                                        accountSettingsOpen = false
                                        openAccount(ProfileDest.EditProfile)
                                    },
                                    rootModifier = tabMod,
                                )
                            } else {
                                PremiumAccountTab(
                                    signedInEmail = signedInEmail,
                                    liveRpc = liveRpc,
                                    onSignIn = { overlay = ShellOverlay.SignIn },
                                    onSignOut = onSignOut,
                                    onEditProfile = { openAccount(ProfileDest.EditProfile) },
                                    onOrders = { openAccount(ProfileDest.Orders) },
                                    onReturns = { openAccount(ProfileDest.Returns) },
                                    onLoyalty = { openAccount(ProfileDest.Loyalty) },
                                    onKits = { openAccount(ProfileDest.Kits) },
                                    onAddresses = { openAccount(ProfileDest.Addresses) },
                                    onPay = { openAccount(ProfileDest.Pay) },
                                    onGarage = {
                                        accountSettingsOpen = false
                                        tab = ShellTab.Garage
                                    },
                                    onCompare = { openAccount(ProfileDest.Compare) },
                                    onTrack = { openAccount(ProfileDest.Track) },
                                    onChat = { openAccount(ProfileDest.Chat) },
                                    onNotifications = { openAccount(ProfileDest.Notifications) },
                                    onCoupons = { openAccount(ProfileDest.Coupons) },
                                    onSettings = { accountSettingsOpen = true },
                                    modifier = tabMod,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileStack(
    dest: ProfileDest,
    onDest: (ProfileDest) -> Unit,
    onClose: () -> Unit,
    rpc: RpcClient,
    liveRpc: Boolean,
    signedIn: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    whatsappE164: String,
    mapsKeyPresent: Boolean,
    trackToken: String?,
    trackJobId: String?,
    trackSession: Int,
    onOpenTrack: (jobId: String?, token: String?) -> Unit,
    onDemoTrack: () -> Unit,
    onOpenGarage: () -> Unit,
    onOpenProduct: (oem: String) -> Unit,
) {
    when (dest) {
        ProfileDest.Hub -> ProfileHub(
            liveRpc = liveRpc,
            signedInEmail = signedInEmail,
            onSignOut = onSignOut,
            onSignIn = onSignIn,
            onOpen = onDest,
            onDemoTrack = onDemoTrack,
            onClose = onClose,
            onOpenGarage = onOpenGarage,
        )
        ProfileDest.EditProfile -> EditProfileScreen(
            rpc = rpc,
            onBack = { onDest(ProfileDest.Hub) },
        )
        ProfileDest.Returns -> ReturnsScreen(
            rpc = rpc,
            onBack = { onDest(ProfileDest.Hub) },
        )
        ProfileDest.Loyalty -> LoyaltyWalletScreen(
            rpc = rpc,
            onBack = { onDest(ProfileDest.Hub) },
        )
        ProfileDest.Kits -> KitsScreen(
            rpc = rpc,
            onBack = { onDest(ProfileDest.Hub) },
            onOpenProduct = onOpenProduct,
        )
        ProfileDest.Orders -> OrdersScreen(
            rpc = rpc,
            onBack = { onDest(ProfileDest.Hub) },
            onTrackDelivery = { jobId, token -> onOpenTrack(jobId, token) },
        )
        ProfileDest.Addresses -> AddressScreen(
            rpc = rpc,
            mapsKeyPresent = mapsKeyPresent,
            onBack = { onDest(ProfileDest.Hub) },
        )
        ProfileDest.Compare -> CompareScreen(
            rpc = rpc,
            isSignedIn = signedIn,
            onBack = { onDest(ProfileDest.Hub) },
        )
        ProfileDest.Pay -> PayIntentScreen(rpc = rpc, onBack = { onDest(ProfileDest.Hub) })
        ProfileDest.Chat -> ChatScreen(
            rpc = rpc,
            onBack = { onDest(ProfileDest.Hub) },
            whatsappE164Digits = whatsappE164,
        )
        ProfileDest.Track -> DeliveryTrackScreen(
            rpc = rpc,
            initialToken = trackToken,
            initialJobId = trackJobId,
            sessionKey = trackSession,
            onBack = { onDest(ProfileDest.Hub) },
        )
        ProfileDest.Notifications -> PremiumNotificationsScreen(
            onBack = { onDest(ProfileDest.Hub) },
        )
        ProfileDest.Coupons -> PremiumCouponsScreen(
            onBack = { onDest(ProfileDest.Hub) },
        )
    }
}

@Composable
private fun ProfileHub(
    liveRpc: Boolean,
    signedInEmail: String?,
    onSignOut: () -> Unit,
    onSignIn: () -> Unit,
    onOpen: (ProfileDest) -> Unit,
    onDemoTrack: () -> Unit,
    onClose: () -> Unit,
    onOpenGarage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("My Account", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = onClose) { Text("Close") }
        }
        Spacer(modifier = Modifier.height(16.dp))
        ShopProfileAvatar(
            initials = signedInEmail?.take(2) ?: if (liveRpc) "?" else "FK",
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            signedInEmail ?: if (liveRpc) "Guest" else "Fake mode",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.height(32.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            ShopProfileItemBox("Edit profile", Icons.Filled.AccountCircle) {
                onOpen(ProfileDest.EditProfile)
            }
            ShopProfileItemBox("My orders", Icons.Filled.ReceiptLong) { onOpen(ProfileDest.Orders) }
            ShopProfileItemBox("Returns", Icons.Filled.LocalShipping) { onOpen(ProfileDest.Returns) }
            ShopProfileItemBox("Loyalty wallet", Icons.Filled.Star) { onOpen(ProfileDest.Loyalty) }
            ShopProfileItemBox("Service kits", Icons.Filled.Build) { onOpen(ProfileDest.Kits) }
            ShopProfileItemBox("Manage address", Icons.Filled.LocationOn) { onOpen(ProfileDest.Addresses) }
            ShopProfileItemBox("Payment methods", Icons.Filled.CreditCard) { onOpen(ProfileDest.Pay) }
            ShopProfileItemBox("My garage", Icons.Filled.DirectionsCar, onClick = onOpenGarage)
            ShopProfileItemBox("Compare", Icons.Filled.CompareArrows) { onOpen(ProfileDest.Compare) }
            ShopProfileItemBox("Track delivery", Icons.Filled.LocalShipping, onClick = onDemoTrack)
            ShopProfileItemBox("Live chat", Icons.Filled.Chat) { onOpen(ProfileDest.Chat) }
            ShopProfileItemBox("Notifications", Icons.Filled.Notifications) { onOpen(ProfileDest.Notifications) }
            ShopProfileItemBox("My coupons", Icons.Filled.CardGiftcard, isLastItem = true) {
                onOpen(ProfileDest.Coupons)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        if (signedInEmail == null) {
            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) {
                Text("Sign in")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = onSignOut,
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            enabled = signedInEmail != null || !liveRpc,
        ) {
            Text("Sign out")
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}
