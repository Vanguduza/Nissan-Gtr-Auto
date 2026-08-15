package co.zw.nissangtr.pos.till

import co.zw.nissangtr.pos.api.AddDecision
import co.zw.nissangtr.pos.api.AddLineRules
import co.zw.nissangtr.pos.api.CatalogSearchMode
import co.zw.nissangtr.pos.api.ChassisChipLatch
import co.zw.nissangtr.pos.api.FakePosClient
import co.zw.nissangtr.pos.api.FitmentRules
import co.zw.nissangtr.pos.api.LineClass
import co.zw.nissangtr.pos.api.ListTillItemsRequest
import co.zw.nissangtr.pos.api.PosClient
import co.zw.nissangtr.pos.api.TicketCta
import co.zw.nissangtr.pos.api.TillItem
import co.zw.nissangtr.pos.api.TillItemsSource
import co.zw.nissangtr.pos.api.VehicleLatch
import co.zw.nissangtr.pos.lookup.FinderMode
import co.zw.nissangtr.pos.lookup.HitRouteAction
import co.zw.nissangtr.pos.lookup.HitRouter

/**
 * P1 till session — latch sticky, hit routing, add/quote rules.
 * Re-badges via [FitmentRules] on latch change without refetch.
 */
class TillSession(
    private val client: PosClient,
    initialLayout: TillLayoutMode,
) {
    var state: TillUiState =
        tillUiStateFromFake(client.fakeTillState(), initialLayout)
        private set

    val cta: TicketCta get() = state.fake.ticket.cta

    fun clearLatch() {
        state = state.copy(
            fake = state.fake.copy(latch = null),
            banner = null,
        )
    }

    fun setFinderMode(mode: FinderMode) {
        state = state.copy(finderMode = mode)
    }

    fun setSearchQuery(q: String) {
        state = state.copy(searchQuery = q)
    }

    fun setCategory(cat: String?) {
        state = state.copy(selectedCategory = cat)
    }

    fun toggleInStock() {
        state = state.copy(inStockOnly = !state.inStockOnly)
    }

    fun setLayout(mode: TillLayoutMode) {
        if (state.layoutMode != mode) {
            state = state.copy(layoutMode = mode)
        }
    }

    /**
     * Apply search hits (§16.2). Latch sticky; tiles re-badged on next compose
     * without refetch of chassis/engine arrays.
     */
    suspend fun applySearchHits(mode: CatalogSearchMode, query: String) {
        if (query.isBlank()) return
        val response = client.searchCatalog(mode, query)
        val actions = HitRouter.routeDtos(response.results)
        for (action in actions) {
            when (action) {
                is HitRouteAction.HydrateOems -> {
                    val tiles = client.listTillItems(
                        ListTillItemsRequest(
                            warehouseId = state.fake.session.warehouseId,
                            source = TillItemsSource.OEMS,
                            inStockOnly = false,
                            oems = action.oems,
                        ),
                    )
                    state = state.copy(
                        fake = state.fake.copy(tiles = tiles),
                        finderMode = FinderMode.SCAN_OEM,
                        selectedOem = tiles.firstOrNull()?.oemPartNumber,
                        banner = null,
                    )
                }
                is HitRouteAction.LatchVehicle -> {
                    state = state.copy(
                        fake = state.fake.copy(latch = action.latch),
                        finderMode = if (action.switchToShopStock) {
                            FinderMode.SHOP_STOCK
                        } else {
                            state.finderMode
                        },
                        banner = null,
                    )
                    // Shop stock refresh optional; re-badge uses existing tile arrays.
                    if (action.switchToShopStock) {
                        loadShopStock()
                    }
                }
                is HitRouteAction.OpenEpcSection -> {
                    val tiles = client.listTillItems(
                        ListTillItemsRequest(
                            warehouseId = state.fake.session.warehouseId,
                            source = TillItemsSource.SECTION,
                            inStockOnly = state.inStockOnly,
                            pncCode = action.pncCode,
                        ),
                    )
                    state = state.copy(
                        fake = state.fake.copy(tiles = tiles),
                        finderMode = if (action.switchToEpc) FinderMode.EPC else state.finderMode,
                        epcSectionPnc = action.pncCode,
                        selectedOem = tiles.firstOrNull()?.oemPartNumber,
                        banner = null,
                    )
                }
                HitRouteAction.Ignore -> Unit
            }
        }
    }

    suspend fun loadShopStock() {
        val latch = state.latch
        val tiles = client.listTillItems(
            ListTillItemsRequest(
                warehouseId = state.fake.session.warehouseId,
                source = TillItemsSource.SHOP_STOCK,
                inStockOnly = state.inStockOnly,
                category = state.selectedCategory,
                chassisCode = latch?.chassisCode,
                engineCode = latch?.engineCode,
            ),
        )
        state = state.copy(fake = state.fake.copy(tiles = tiles))
    }

    /**
     * Add tile under §16.3 rules. Returns reject message or null on success.
     * [autoConfirmVerify] true for Fake one-tap when VERIFY (confirm sheet Later).
     */
    suspend fun tryAddTile(
        item: TillItem,
        qty: Int = 1,
        verifyConfirmed: Boolean = false,
        forceQuote: Boolean = false,
        autoConfirmVerify: Boolean = true,
    ): String? {
        state = state.copy(selectedOem = item.oemPartNumber)
        val decision = AddLineRules.decide(
            item = item,
            latch = state.latch,
            qty = qty,
            verifyConfirmed = verifyConfirmed,
            forceQuote = forceQuote,
            autoConfirmVerify = autoConfirmVerify,
        )
        when (decision) {
            is AddDecision.Reject -> {
                state = state.copy(
                    banner = decision.supersessionBanner ?: decision.message,
                )
                return decision.message
            }
            is AddDecision.Allow -> {
                if (decision.supersessionBanner != null) {
                    state = state.copy(banner = decision.supersessionBanner)
                }
                val cartId = state.fake.session.cartId ?: return "No open cart"
                val quoteOnly = decision.lineClass == LineClass.QUOTE_ONLY
                val ticket = client.addCartLine(cartId, decision.item, qty, quoteOnly)
                state = state.copy(
                    fake = state.fake.copy(ticket = ticket),
                    banner = decision.supersessionBanner,
                )
                return null
            }
        }
    }

    suspend fun runQuoteCta(): String? {
        if (state.fake.ticket.cta != TicketCta.QUOTE) return "CTA is PAY"
        val cartId = state.fake.session.cartId ?: return "No cart"
        val result = client.createQuotationAndPark(cartId)
        state = state.copy(
            fake = state.fake.copy(
                ticket = result.ticket,
                session = state.fake.session.copy(cartId = result.newCartId),
                statusLabel = "Quoted ${result.quotation.documentNumber} · cart parked",
            ),
            banner = "Created ${result.quotation.documentNumber}",
        )
        return null
    }

    suspend fun park(): String? {
        val cartId = state.fake.session.cartId ?: return "No cart"
        if (state.fake.ticket.lines.isEmpty()) return "Empty ticket"
        val ref = client.parkCart(cartId)
        val fresh = client.fakeTillState()
        state = state.copy(
            fake = state.fake.copy(
                ticket = fresh.ticket,
                session = fresh.session.copy(staffName = state.fake.session.staffName),
                statusLabel = "Parked ${ref.cartId}",
                online = fresh.online,
            ),
            banner = "Parked cart",
            boundCustomerId = null,
        )
        return null
    }

    suspend fun resume(cartId: String): String? {
        val ticket = client.resumeCart(cartId)
        state = state.copy(
            fake = state.fake.copy(
                ticket = ticket,
                session = state.fake.session.copy(cartId = cartId),
                statusLabel = "Resumed $cartId",
            ),
            banner = "Resumed cart",
        )
        return null
    }

    suspend fun voidTicket(managerPin: String = "mgr-ok"): String? {
        val cartId = state.fake.session.cartId ?: return "No cart"
        val ticket = client.voidCart(cartId, managerPin)
        state = state.copy(
            fake = state.fake.copy(ticket = ticket, statusLabel = "Voided"),
            banner = "Ticket voided",
        )
        return null
    }

    suspend fun applyDiscount(percent: Int, managerPin: String = "mgr-ok"): String? {
        val cartId = state.fake.session.cartId ?: return "No cart"
        val ticket = client.applyCartDiscount(cartId, percent, managerPin)
        state = state.copy(
            fake = state.fake.copy(ticket = ticket),
            banner = "Discount $percent%",
        )
        return null
    }

    suspend fun bindCustomer(customerId: String?): String? {
        val cartId = state.fake.session.cartId ?: return "No cart"
        client.bindCustomer(cartId, customerId)
        state = state.copy(
            boundCustomerId = customerId,
            banner = if (customerId == null) "Walk-in" else "Customer bound",
        )
        return null
    }

    fun setOnline(online: Boolean) {
        (client as? FakePosClient)?.setOnline(online)
        state = state.copy(
            fake = state.fake.copy(
                online = online,
                statusLabel = if (online) "Online · Fake" else "Offline · cash only",
            ),
        )
    }

    fun applyCheckoutResult(result: co.zw.nissangtr.pos.api.CheckoutResult) {
        val fresh = client.fakeTillState()
        if (result.paid) {
            state = state.copy(
                fake = state.fake.copy(
                    ticket = fresh.ticket,
                    session = fresh.session.copy(staffName = state.fake.session.staffName),
                    statusLabel = "Paid ${result.invoiceId}",
                    online = fresh.online,
                ),
                banner = "Sale ${result.invoiceId}",
                boundCustomerId = null,
            )
        } else {
            state = state.copy(
                banner = "On hold ${result.invoiceId} · needs finance (not paid)",
                fake = state.fake.copy(statusLabel = "On hold · credit"),
            )
        }
    }

    fun applySyncBanner(label: String, online: Boolean = true, tiles: List<TillItem>? = null) {
        state = state.copy(
            fake = state.fake.copy(
                statusLabel = label,
                online = online,
                tiles = tiles ?: state.fake.tiles,
            ),
        )
    }

    fun badgeFor(item: TillItem) = FitmentRules.badge(item, state.latch)

    /** Test helper — set latch without search. */
    fun latchForTest(latch: VehicleLatch?) {
        state = state.copy(fake = state.fake.copy(latch = latch))
    }

    fun setChassisShortcuts(chips: List<co.zw.nissangtr.pos.api.ChassisShortcut>) {
        state = state.copy(chassisShortcuts = chips)
    }

    /**
     * Chassis shortcut chip → latch + shop-stock filter.
     * Pure latch via [ChassisChipLatch]; caller refreshes tiles.
     */
    suspend fun latchChassisChip(chip: co.zw.nissangtr.pos.api.ChassisShortcut) {
        val latch = ChassisChipLatch.latchFromChip(
            chassisCode = chip.chassisCode,
            engineCode = chip.engineCode,
            modelVariant = chip.modelVariant,
        ) ?: return
        state = state.copy(
            fake = state.fake.copy(latch = latch),
            finderMode = FinderMode.SHOP_STOCK,
            banner = null,
        )
        loadShopStock()
    }
}
