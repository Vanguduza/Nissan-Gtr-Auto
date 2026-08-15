package co.zw.nissangtr.pos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.api.CatalogSearchMode
import co.zw.nissangtr.pos.api.CheckoutReceiptContacts
import co.zw.nissangtr.pos.api.CloseTillFloatRequest
import co.zw.nissangtr.pos.api.CustomerRef
import co.zw.nissangtr.pos.api.FakePosClient
import co.zw.nissangtr.pos.api.FitmentRules
import co.zw.nissangtr.pos.api.HandoffExtras
import co.zw.nissangtr.pos.api.HandoffIntentParse
import co.zw.nissangtr.pos.api.ListTillItemsRequest
import co.zw.nissangtr.pos.api.LivePosClient
import co.zw.nissangtr.pos.api.MoneyCents
import co.zw.nissangtr.pos.api.OpenTillFloatRequest
import co.zw.nissangtr.pos.api.PosClient
import co.zw.nissangtr.pos.api.PosHandoffPayload
import co.zw.nissangtr.pos.api.TenderMode
import co.zw.nissangtr.pos.api.TicketCta
import co.zw.nissangtr.pos.api.TillFloatRules
import co.zw.nissangtr.pos.api.TillItem
import co.zw.nissangtr.pos.api.TillItemsSource
import co.zw.nissangtr.pos.customer.CustomerSelectSheet
import co.zw.nissangtr.pos.kiosk.KioskDevicePrefs
import co.zw.nissangtr.pos.kiosk.LockTaskController
import co.zw.nissangtr.pos.lookup.BridgeScanStub
import co.zw.nissangtr.pos.lookup.FinderMode
import co.zw.nissangtr.pos.lookup.FitmentInfoSheet
import co.zw.nissangtr.pos.lookup.PriceCheckSheet
import co.zw.nissangtr.pos.lookup.ScanDebouncer
import co.zw.nissangtr.pos.orders.ParkedOrdersSheet
import co.zw.nissangtr.pos.pay.LiveRailSettler
import co.zw.nissangtr.pos.pay.PaySheet
import co.zw.nissangtr.pos.pay.PaySheetUiState
import co.zw.nissangtr.pos.pay.TenderAllocator
import co.zw.nissangtr.pos.pay.TenderDraft
import co.zw.nissangtr.pos.pay.bridge.PosBridgeFactory
import co.zw.nissangtr.pos.pay.enabledWhen
import co.zw.nissangtr.pos.sync.InMemoryOfflineStore
import co.zw.nissangtr.pos.sync.OfflineStore
import co.zw.nissangtr.pos.sync.PosClientOfflineRemote
import co.zw.nissangtr.pos.sync.PosSyncManager
import co.zw.nissangtr.pos.sync.SqlCipherOfflineStore
import co.zw.nissangtr.pos.till.IdleLockController
import co.zw.nissangtr.pos.till.ReturnsSheet
import co.zw.nissangtr.pos.till.TillFloatSheet
import co.zw.nissangtr.pos.till.TillLayoutMode
import co.zw.nissangtr.pos.till.TillScreen
import co.zw.nissangtr.pos.till.TillSession
import co.zw.nissangtr.pos.ui.StaffLoginShell
import co.zw.nissangtr.ui.theme.GtrColors
import co.zw.nissangtr.ui.theme.GtrTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {

    private var liveScan: co.zw.nissangtr.pos.pay.bridge.LivePosScanBridge? = null
    private var lockTask: LockTaskController? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forceFake = BuildConfig.RPC_FORCE_FAKE
        val url = BuildConfig.SUPABASE_URL
        val anon = BuildConfig.SUPABASE_ANON_KEY
        val live = !forceFake && url.isNotBlank() && anon.isNotBlank()
        val client: PosClient = if (live) {
            LivePosClient(supabaseUrl = url, supabaseAnonKey = anon)
        } else if (forceFake) {
            FakePosClient()
        } else {
            LivePosClient() // no creds → Fake fallback
        }
        val liveClient = client as? LivePosClient
        val handoff = parseHandoff(intent)
        val bridges = PosBridgeFactory.create(this, forceFake = forceFake || !live)
        liveScan = bridges.liveScan
        bridges.liveScan?.attachActivity(this)
        bridges.livePrint?.attachActivity(this)

        val offlineStore: OfflineStore = if (forceFake || !live) {
            InMemoryOfflineStore()
        } else {
            SqlCipherOfflineStore(this)
        }

        val kioskPrefs = KioskDevicePrefs(this)
        lockTask = LockTaskController(this, kioskEnabled = true)
        if (kioskPrefs.lockTaskOnLaunch) {
            lockTask?.enterLockTaskIfAllowed()
        }

        setContent {
            GtrTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = GtrColors.Steel,
                ) {
                    val windowSize = calculateWindowSizeClass(this)
                    val layoutMode = when (windowSize.widthSizeClass) {
                        WindowWidthSizeClass.Expanded -> TillLayoutMode.Expanded
                        else -> TillLayoutMode.Compact
                    }
                    PosApp(
                        client = client,
                        liveClient = liveClient,
                        forceFake = forceFake || !live,
                        layoutMode = layoutMode,
                        offlineStore = offlineStore,
                        bridges = bridges,
                        handoff = handoff,
                        idleMs = kioskPrefs.idleMinutes * 60_000L,
                        onExitKiosk = { lockTask?.exitLockTask() },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        liveScan?.detachActivity()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        liveScan?.onPermissionResult()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        liveScan?.onScanActivityResult(resultCode, data)
    }

    companion object {
        fun parseHandoff(intent: Intent?): PosHandoffPayload {
            if (intent == null) {
                return HandoffIntentParse.parse(emptyMap())
            }
            val map = mapOf(
                HandoffExtras.HANDOFF to intent.getStringExtra(HandoffExtras.HANDOFF),
                HandoffExtras.STAFF_DISPLAY_NAME to
                    intent.getStringExtra(HandoffExtras.STAFF_DISPLAY_NAME),
                HandoffExtras.ACCESS_TOKEN to intent.getStringExtra(HandoffExtras.ACCESS_TOKEN),
                HandoffExtras.REFRESH_TOKEN to intent.getStringExtra(HandoffExtras.REFRESH_TOKEN),
                HandoffExtras.BRANCH_ID to intent.getStringExtra(HandoffExtras.BRANCH_ID),
                HandoffExtras.TERMINAL_ID to intent.getStringExtra(HandoffExtras.TERMINAL_ID),
                HandoffExtras.WAREHOUSE_ID to intent.getStringExtra(HandoffExtras.WAREHOUSE_ID),
            )
            return HandoffIntentParse.parse(map)
        }
    }
}

@Composable
fun PosApp(
    client: PosClient,
    liveClient: LivePosClient?,
    forceFake: Boolean,
    layoutMode: TillLayoutMode,
    offlineStore: OfflineStore,
    bridges: PosBridgeFactory.Bundle,
    handoff: PosHandoffPayload,
    idleMs: Long = IdleLockController.IDLE_MS,
    onExitKiosk: () -> Unit = {},
) {
    var signedIn by remember { mutableStateOf(false) }
    var staffName by remember { mutableStateOf("T. Moyo") }
    var lastInteractionMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var handoffTried by remember { mutableStateOf(false) }
    var openFloatPeriodId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(handoff, liveClient) {
        if (handoffTried) return@LaunchedEffect
        handoffTried = true
        if (liveClient?.usesLive == true && handoff.canEstablishLiveSession) {
            runCatching {
                liveClient.importAccessToken(
                    accessToken = handoff.accessToken!!,
                    refreshToken = handoff.refreshToken.orEmpty(),
                )
                val name = handoff.staffDisplayName ?: liveClient.currentUserEmail()
                    ?.substringBefore("@") ?: "Staff"
                liveClient.setStaffDisplayName(name)
                handoff.terminalId?.let { liveClient.setTerminalId(it) }
                handoff.warehouseId?.let { liveClient.setWarehouse(it) }
                staffName = name
                signedIn = true
                lastInteractionMs = System.currentTimeMillis()
            }
            // Tokens never logged — failures fall through to login.
        }
    }

    if (!signedIn) {
        StaffLoginShell(
            liveClient = liveClient,
            forceFake = forceFake,
            onSignIn = { name ->
                staffName = name.ifBlank { "T. Moyo" }
                signedIn = true
                lastInteractionMs = System.currentTimeMillis()
            },
        )
        return
    }

    LaunchedEffect(signedIn, idleMs) {
        while (isActive && signedIn) {
            delay(15_000L)
            if (IdleLockController.shouldLock(
                    lastInteractionMs,
                    System.currentTimeMillis(),
                    idleMs,
                )
            ) {
                signedIn = false
            }
        }
    }

    val scope = rememberCoroutineScope()
    val session = remember(client) { TillSession(client, layoutMode) }
    var ui by remember { mutableStateOf(session.state) }
    val syncManager = remember(client, offlineStore) {
        PosSyncManager(offlineStore, PosClientOfflineRemote(client))
    }

    var showPay by remember { mutableStateOf(false) }
    var payState by remember { mutableStateOf<PaySheetUiState?>(null) }
    var showCustomer by remember { mutableStateOf(false) }
    var customerQuery by remember { mutableStateOf("") }
    var customers by remember { mutableStateOf<List<CustomerRef>>(emptyList()) }
    var showOrders by remember { mutableStateOf(false) }
    var showPriceCheck by remember { mutableStateOf(false) }
    var priceCheckQuery by remember { mutableStateOf("") }
    var priceCheckItem by remember { mutableStateOf<TillItem?>(null) }
    var showFitment by remember { mutableStateOf(false) }
    var fitmentItem by remember { mutableStateOf<TillItem?>(null) }
    var showFloat by remember { mutableStateOf(false) }
    var floatBusy by remember { mutableStateOf(false) }
    var floatError by remember { mutableStateOf<String?>(null) }
    var showReturns by remember { mutableStateOf(false) }
    var returnsBusy by remember { mutableStateOf(false) }
    var returnsError by remember { mutableStateOf<String?>(null) }

    fun touch() {
        lastInteractionMs = System.currentTimeMillis()
    }

    fun refresh() {
        val banner = syncManager.banner(ui.fake.online)
        ui = session.state.copy(
            fake = session.state.fake.copy(
                session = session.state.fake.session.copy(staffName = staffName),
                statusLabel = banner.label,
            ),
        )
    }

    LaunchedEffect(layoutMode) {
        session.setLayout(layoutMode)
        refresh()
    }

    LaunchedEffect(client) {
        runCatching {
            val chips = client.listChassisShortcuts()
            session.setChassisShortcuts(chips)
            refresh()
        }
        runCatching {
            val snap = syncManager.syncNow(session.state.fake.session.warehouseId)
            session.applySyncBanner(snap.label, online = true)
            refresh()
        }
    }

    val debouncer = remember(scope) {
        ScanDebouncer(scope) { normalized ->
            if (normalized.isEmpty()) return@ScanDebouncer
            scope.launch {
                val mode = when (session.state.finderMode) {
                    FinderMode.SCAN_OEM -> CatalogSearchMode.PART
                    FinderMode.VIN_PNC -> inferVinPncMode(normalized)
                    FinderMode.SHOP_STOCK, FinderMode.EPC -> CatalogSearchMode.PART
                }
                session.applySearchHits(mode, normalized)
                refresh()
            }
        }
    }

    fun openPaySheet() {
        val ticket = session.state.fake.ticket
        val due = MoneyCents.majorToCents(ticket.subtotal)
        payState = PaySheetUiState(
            dueCents = due,
            currency = ticket.currency,
            online = session.state.fake.online,
            drafts = listOf(TenderDraft(TenderMode.CASH, due, railSettled = true)),
        )
        showPay = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { touch() }
            },
    ) {
        TillScreen(
            state = ui,
            onClearLatch = {
                touch()
                session.clearLatch()
                refresh()
            },
            onFinderMode = { mode ->
                touch()
                session.setFinderMode(mode)
                refresh()
                if (mode == FinderMode.SHOP_STOCK) {
                    scope.launch {
                        session.loadShopStock()
                        refresh()
                    }
                }
            },
            onSearchChange = { q ->
                touch()
                session.setSearchQuery(q)
                refresh()
                debouncer.onInput(q)
            },
            onCategory = { cat ->
                touch()
                session.setCategory(cat)
                refresh()
                scope.launch {
                    session.loadShopStock()
                    refresh()
                }
            },
            onInStockToggle = {
                touch()
                session.toggleInStock()
                refresh()
                scope.launch {
                    session.loadShopStock()
                    refresh()
                }
            },
            onTileClick = { item ->
                touch()
                if (item.supersededBy != null) {
                    fitmentItem = item
                    showFitment = true
                } else {
                    scope.launch {
                        session.tryAddTile(item)
                        refresh()
                    }
                }
            },
            onChassisChip = { chip ->
                touch()
                scope.launch {
                    session.latchChassisChip(chip)
                    refresh()
                }
            },
            onCustomer = {
                touch()
                showCustomer = true
                scope.launch {
                    customers = client.listCustomers(customerQuery)
                }
            },
            onOrders = {
                touch()
                showOrders = true
            },
            onPark = {
                touch()
                scope.launch {
                    session.park()
                    refresh()
                }
            },
            onVoid = {
                touch()
                scope.launch {
                    session.voidTicket()
                    refresh()
                }
            },
            onPay = {
                touch()
                scope.launch {
                    when (session.cta) {
                        TicketCta.QUOTE -> {
                            session.runQuoteCta()
                            refresh()
                        }
                        TicketCta.PAY -> openPaySheet()
                    }
                }
            },
            onScan = {
                touch()
                scope.launch {
                    val raw = bridges.scan.scanOnce()
                    val normalized = BridgeScanStub.onScanPayload(raw)
                    session.setFinderMode(FinderMode.SCAN_OEM)
                    session.setSearchQuery(normalized)
                    session.applySearchHits(CatalogSearchMode.PART, normalized)
                    refresh()
                }
            },
            onPrint = {
                touch()
                scope.launch {
                    val t = session.state.fake.ticket
                    val lines = buildList {
                        add("GTR POS RECEIPT")
                        add(
                            "${t.currency} ${
                                MoneyCents.centsToMajorString(MoneyCents.majorToCents(t.subtotal))
                            }",
                        )
                        t.lines.forEach { add("${it.oemPartNumber} x${it.qty}") }
                    }
                    bridges.print.printReceiptLines(lines)
                }
            },
            onDrawer = {
                touch()
                scope.launch { bridges.drawer.openDrawer() }
            },
            onSettings = {
                touch()
                showFloat = true
                floatError = null
            },
            onSync = {
                touch()
                scope.launch {
                    val banner = syncManager.syncNow(session.state.fake.session.warehouseId)
                    session.applySyncBanner(
                        label = banner.label,
                        online = true,
                        tiles = offlineStore.listCatalog().ifEmpty { null },
                    )
                    refresh()
                }
            },
            onPriceCheck = {
                touch()
                // Long-press Info also opens returns via settings rail; price check stays.
                showPriceCheck = true
                priceCheckQuery = ""
                priceCheckItem = null
            },
        )

        // Secondary: returns from Orders sheet path — also settings long-press alternative
        // Settings opens float; double-tap settings opens returns via rail Info secondary.
        // Expose returns via Orders dismiss area — add overlay button in orders.

        if (showPay) {
            payState?.let { ps ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PaySheet(
                        state = ps,
                        onAddMode = { mode ->
                            touch()
                            if (!mode.enabledWhen(ps.online)) return@PaySheet
                            payState = ps.copy(
                                drafts = ps.drafts + TenderDraft(
                                    mode = mode,
                                    tenderedCents = 0L,
                                    railSettled = !mode.isLiveRail,
                                ),
                                error = null,
                            )
                        },
                        onFillRest = { mode ->
                            touch()
                            if (!mode.enabledWhen(ps.online)) return@PaySheet
                            val fill = TenderAllocator.fillRest(ps.dueCents, ps.drafts, mode)
                            payState = ps.copy(drafts = ps.drafts + fill, error = null)
                        },
                        onSplitEqually = { modes ->
                            touch()
                            val allowed = modes.filter { it.enabledWhen(ps.online) }
                            if (allowed.isEmpty()) return@PaySheet
                            payState = ps.copy(
                                drafts = TenderAllocator.splitEqually(ps.dueCents, allowed)
                                    .map { d -> d.copy(railSettled = !d.mode.isLiveRail) },
                                error = null,
                            )
                        },
                        onTenderedChange = { index, cents ->
                            touch()
                            val next = ps.drafts.toMutableList()
                            if (index in next.indices) {
                                next[index] = next[index].copy(tenderedCents = cents)
                                payState = ps.copy(drafts = next, error = null)
                            }
                        },
                        onReceiptWhatsapp = { payState = ps.copy(receiptWhatsapp = it) },
                        onReceiptEmail = { payState = ps.copy(receiptEmail = it) },
                        onConfirm = {
                            touch()
                            scope.launch {
                                val cartId = session.state.fake.session.cartId ?: return@launch
                                val cur = payState ?: return@launch
                                payState = cur.copy(busy = true, error = null)
                                try {
                                    val result = LiveRailSettler.confirmCheckout(
                                        client = client,
                                        cartId = cartId,
                                        dueCents = cur.dueCents,
                                        drafts = cur.drafts,
                                        currency = cur.currency,
                                        online = cur.online,
                                        receipt = CheckoutReceiptContacts(
                                            email = cur.receiptEmail.ifBlank { null },
                                            whatsappE164 = cur.receiptWhatsapp.ifBlank { null },
                                        ),
                                    )
                                    session.applyCheckoutResult(result)
                                    refresh()
                                    showPay = false
                                    payState = null
                                } catch (e: Exception) {
                                    payState = cur.copy(
                                        busy = false,
                                        error = e.message ?: "Checkout failed",
                                    )
                                }
                            }
                        },
                        onDismiss = {
                            showPay = false
                            payState = null
                        },
                    )
                }
            }
        }

        if (showCustomer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                CustomerSelectSheet(
                    customers = customers,
                    query = customerQuery,
                    selectedId = ui.boundCustomerId,
                    onQueryChange = { q ->
                        touch()
                        customerQuery = q
                        scope.launch { customers = client.listCustomers(q) }
                    },
                    onSelect = { c ->
                        touch()
                        scope.launch {
                            session.bindCustomer(c?.id)
                            refresh()
                            showCustomer = false
                        }
                    },
                    onDismiss = { showCustomer = false },
                )
            }
        }

        if (showOrders) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                ParkedOrdersSheet(
                    parked = client.listParkedCarts(),
                    quotations = client.listQuotations(),
                    onResume = { ref ->
                        touch()
                        scope.launch {
                            session.resume(ref.cartId)
                            refresh()
                            showOrders = false
                        }
                    },
                    onOpenQuotation = { qt ->
                        touch()
                        scope.launch {
                            client.convertQuotationToCart(qt.id)
                            refresh()
                            showOrders = false
                        }
                    },
                    onReturns = {
                        showOrders = false
                        showReturns = true
                    },
                    onDismiss = { showOrders = false },
                )
            }
        }

        if (showPriceCheck) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                PriceCheckSheet(
                    query = priceCheckQuery,
                    item = priceCheckItem,
                    badge = priceCheckItem?.let {
                        FitmentRules.badge(it, ui.latch)
                    },
                    onQueryChange = { q ->
                        touch()
                        priceCheckQuery = q
                        scope.launch {
                            val items = client.listTillItems(
                                ListTillItemsRequest(
                                    warehouseId = ui.fake.session.warehouseId,
                                    source = TillItemsSource.OEMS,
                                    inStockOnly = false,
                                    oems = listOf(q),
                                ),
                            )
                            priceCheckItem = items.firstOrNull()
                                ?: offlineStore.findByOem(q)
                        }
                    },
                    onDismiss = { showPriceCheck = false },
                )
            }
        }

        if (showFitment) {
            fitmentItem?.let { item ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FitmentInfoSheet(
                        item = item,
                        badge = FitmentRules.badge(item, ui.latch),
                        latch = ui.latch,
                        onSwapSupersession = item.supersededBy?.let { oem ->
                            {
                                scope.launch {
                                    session.applySearchHits(CatalogSearchMode.PART, oem)
                                    refresh()
                                    showFitment = false
                                }
                            }
                        },
                        onDismiss = { showFitment = false },
                    )
                }
            }
        }

        if (showFloat) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                TillFloatSheet(
                    openPeriodId = openFloatPeriodId,
                    busy = floatBusy,
                    error = floatError,
                    onOpen = { opening ->
                        scope.launch {
                            floatBusy = true
                            floatError = null
                            try {
                                val day = LocalDate.now().toString()
                                val period = client.openTillFloat(
                                    OpenTillFloatRequest(
                                        accountCode = TillFloatRules.CASH_SALES_TILL,
                                        periodStart = day,
                                        periodEnd = day,
                                        openingBalance = opening,
                                    ),
                                )
                                openFloatPeriodId = period.id
                                showFloat = false
                            } catch (e: Exception) {
                                floatError = e.message ?: "Open float failed"
                            } finally {
                                floatBusy = false
                            }
                        }
                    },
                    onClose = { count ->
                        scope.launch {
                            val id = openFloatPeriodId ?: return@launch
                            floatBusy = true
                            floatError = null
                            try {
                                client.closeTillFloat(
                                    CloseTillFloatRequest(periodId = id, physicalCount = count),
                                )
                                openFloatPeriodId = null
                                showFloat = false
                            } catch (e: Exception) {
                                floatError = e.message ?: "Close float failed"
                            } finally {
                                floatBusy = false
                            }
                        }
                    },
                    onDismiss = {
                        showFloat = false
                        // Settings also exits Lock Task for maintenance.
                        onExitKiosk()
                    },
                )
            }
        }

        if (showReturns) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                ReturnsSheet(
                    busy = returnsBusy,
                    error = returnsError,
                    onSubmit = { invoiceId, reason ->
                        scope.launch {
                            returnsBusy = true
                            returnsError = null
                            try {
                                client.postPosRefund(invoiceId, reason)
                                showReturns = false
                            } catch (e: Exception) {
                                returnsError = e.message ?: "Refund failed"
                            } finally {
                                returnsBusy = false
                            }
                        }
                    },
                    onDismiss = { showReturns = false },
                )
            }
        }
    }
}

/** VIN/PNC tab picker (§16.2): 4–6 digit → pnc; JN/1N… → vin; else model. */
internal fun inferVinPncMode(q: String): CatalogSearchMode {
    val s = q.trim()
    if (s.length in 4..6 && s.all { it.isDigit() }) return CatalogSearchMode.PNC
    val upper = s.uppercase()
    if (upper.length in 11..17 &&
        (upper.startsWith("JN") || upper.startsWith("1N"))
    ) {
        return CatalogSearchMode.VIN
    }
    return CatalogSearchMode.MODEL
}

/** Expose bridge stub for rail/hardware later. */
@Suppress("unused")
fun normalizeBridgeScan(raw: String): String = BridgeScanStub.onScanPayload(raw)
