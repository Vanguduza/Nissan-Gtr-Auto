package co.zw.nissangtr.pos.data

import co.zw.nissangtr.management.rpc.CatalogSearchMode
import co.zw.nissangtr.management.rpc.PosCartLineSummary
import co.zw.nissangtr.management.rpc.PosPartMeta
import co.zw.nissangtr.management.rpc.PosPopularItemKind
import co.zw.nissangtr.management.rpc.PosPopularPin
import co.zw.nissangtr.management.rpc.PosSaleVehicleSelection
import co.zw.nissangtr.management.rpc.RpcClient
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.gateway.CartGateway
import co.zw.nissangtr.pos.domain.gateway.CatalogGateway
import co.zw.nissangtr.pos.domain.gateway.FitmentGateway
import co.zw.nissangtr.pos.domain.gateway.PinGateway
import co.zw.nissangtr.pos.domain.gateway.SessionGateway
import co.zw.nissangtr.pos.domain.model.CartLine
import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.Operator
import co.zw.nissangtr.pos.domain.model.PinKind
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.model.VehicleGeneration
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.model.VehicleSelection
import co.zw.nissangtr.pos.domain.result.PosResult
import kotlinx.coroutines.CancellationException
import java.io.IOException
import co.zw.nissangtr.management.rpc.CurrencyCode as RpcCurrency

/** Catalogue maker for the cascade; Make itself is never shown (owner decision D2). */
private const val MAKER = "nissan"

/**
 * Gateway implementations over the existing typed RPC client (Blueprint §10.2, Phase 5). Every
 * call returns a [PosResult]; transport and server refusals become [PosError] values here and
 * never escape as exceptions.
 */
class RpcPosGateways(private val rpc: RpcClient) {

    val session: SessionGateway = object : SessionGateway {
        override suspend fun operator() = call {
            val name = rpc.currentStaffDisplayName() ?: "Operator"
            val role = rpc.listMyStaffRoles().firstOrNull()?.replaceFirstChar { it.uppercase() } ?: "POS"
            Operator(name, role)
        }
    }

    // Model → generation → engine from the published vehicle master (the vehicles the full
    // catalogue serves), so every pick resolves to an R2-backed vehicle.
    val fitment: FitmentGateway = object : FitmentGateway {
        override suspend fun models() = call {
            rpc.listVehicleMaster()
                .distinctBy { it.familySlug }
                .map { VehicleModel(it.familySlug, it.modelFamily) }
                .sortedBy { it.name }
        }

        override suspend fun generations(model: VehicleModel) = call {
            rpc.listVehicleMaster()
                .filter { it.familySlug == model.slug }
                .groupBy { it.chassisCode }
                .map { (chassis, vehicles) ->
                    val years = vehicles.mapNotNull { it.yearLabel }.distinct()
                    VehicleGeneration(chassis, if (years.isEmpty()) chassis else "$chassis (${years.joinToString(", ")})")
                }
        }

        override suspend fun engines(model: VehicleModel, generation: VehicleGeneration) = call {
            rpc.listVehicleMaster()
                .filter { it.familySlug == model.slug && it.chassisCode == generation.chassisCode }
                .mapNotNull { it.engineCode?.takeIf(String::isNotBlank) }
                .distinct()
        }
    }

    val catalog: CatalogGateway = object : CatalogGateway {
        override suspend fun search(query: String, vehicle: VehicleSelection?) = call {
            val q = query.trim()
            // Full catalogue first: the R2 fitment shard for this exact vehicle, joined to shop stock.
            // R2 not serving yet (or vehicle not in the published master): Supabase fitment rows below.
            vehicle?.let { liveVehicleSearch(it, q) }?.let { return@call it }
            val hits = when {
                vehicle != null -> rpc.searchCatalogForVehicle(vehicle.toRpc(), q).parts
                q.length < 2 -> emptyList()
                else -> rpc.searchCatalog(CatalogSearchMode.PART, q).parts
            }
            val meta = rpc.hydratePosParts(hits.map { it.oemPartNumber }).associateBy { it.oemPartNumber.trim().uppercase() }
            hits.distinctBy { it.oemPartNumber.trim().uppercase() }.map { hit ->
                val m = meta[hit.oemPartNumber.trim().uppercase()]
                m?.toPart(hit.description) ?: CatalogPart(
                    stockItemId = null,
                    oemPartNumber = hit.oemPartNumber,
                    name = hit.description ?: hit.oemPartNumber,
                    price = null,
                    saleableQty = hit.saleableQty,
                    imageUrl = null,
                )
            }
        }

        /** Vehicle-filtered search over the full catalogue; null when it is not available. */
        private suspend fun liveVehicleSearch(vehicle: VehicleSelection, q: String): List<CatalogPart>? = try {
            rpc.resolveVehicleMasterId(vehicle.chassisCode, vehicle.engineCode)?.let { id ->
                val params = buildMap {
                    put("vehicle_id", id)
                    put("limit", "100")
                    if (q.isNotEmpty()) put("q", q)
                }
                rpc.catalogLive(if (q.isEmpty()) "customer-stock" else "customer-search", params)["results"]
                    ?.jsonArray.orEmpty()
                    .mapNotNull { it as? JsonObject }
                    .map { r ->
                        val price = r["price"] as? JsonObject
                        val stock = r["stock"] as? JsonObject
                        val name = r.text("name") ?: "Nissan part"
                        CatalogPart(
                            stockItemId = r.text("stock_item_id"),
                            oemPartNumber = r.text("internal_catalog_ref") ?: name,
                            name = name,
                            price = price?.let { p ->
                                p["amount"]?.jsonPrimitive?.doubleOrNull?.let { amount ->
                                    Money.ofMajor(amount, CurrencyCode((p.text("currency") ?: "USD").uppercase()))
                                }
                            },
                            saleableQty = stock?.get("qty")?.jsonPrimitive?.doubleOrNull,
                            imageUrl = null,
                        )
                    }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

        override suspend fun bestSellers() = call {
            rpc.listPosPopularSpares(days = 90, limit = 24).map { s ->
                CatalogPart(
                    stockItemId = s.stockItemId,
                    oemPartNumber = s.oemPartNumber,
                    name = s.description ?: s.oemPartNumber,
                    price = s.unitPrice?.let { Money.ofMajor(it, (s.currency ?: RpcCurrency.USD).toDomain()) },
                    saleableQty = s.saleableQty,
                    imageUrl = s.imageUrl,
                )
            }
        }
    }

    val cart: CartGateway = object : CartGateway {
        private var warehouseId: String? = null

        private suspend fun warehouse(): String = warehouseId ?: shopWarehouseId(rpc).also { warehouseId = it }

        override suspend fun open(currency: CurrencyCode) = call {
            val cartId = rpc.createPosCart(warehouseId = warehouse(), currency = currency.toRpc())
            projection(cartId, currency)
        }

        override suspend fun addLine(cartId: String, part: CatalogPart, qty: Double) = call {
            val ref = rpc.lookupStockItemByOem(part.oemPartNumber)
            rpc.addCartLine(cartId, ref.stockItemId, ref.uomId, qty)
            projection(cartId, part.price?.currency ?: CurrencyCode.USD)
        }

        override suspend fun setQuantity(cartId: String, lineId: String, qty: Double) = call {
            val line = rpc.listPosCartLines(cartId).firstOrNull { it.id == lineId }
                ?: throw PosFailure(PosError.BusinessRule("line_missing", "That line is no longer on the sale."))
            rpc.setPosCartLineQty(lineId, qty, line.unitPrice)
            projection(cartId, null)
        }

        override suspend fun removeLine(cartId: String, lineId: String) = call {
            rpc.deletePosCartLine(lineId)
            projection(cartId, null)
        }

        override suspend fun setVehicle(cartId: String, vehicle: VehicleSelection?) = call {
            rpc.setPosCartVehicle(cartId, vehicle?.toRpc())
            Unit
        }

        private suspend fun projection(cartId: String, currency: CurrencyCode?): CartProjection =
            cartProjection(cartId, rpc.listPosCartLines(cartId), currency ?: CurrencyCode.USD)
    }

    val pins: PinGateway = object : PinGateway {
        override suspend fun pins() = call { rpc.listPosPopularPins().map { it.toDomain() } }

        override suspend fun pin(pin: PopularPin) = call {
            rpc.upsertPosPopularPin(pin.toRpc())
            Unit
        }

        override suspend fun unpin(pin: PopularPin) = call {
            if (!rpc.deletePosPopularPin(pin.kind.toRpc(), pin.key)) {
                throw PosFailure(PosError.BusinessRule("pin_missing", "That pin was already removed."))
            }
        }

        override suspend fun hiddenBestSellers() = call { rpc.listPosHiddenBestsellers().toSet() }

        override suspend fun hideBestSeller(stockItemId: String) = call {
            if (!rpc.hidePosBestseller(stockItemId)) throw PosFailure(PosError.BusinessRule("hide_refused", ""))
        }

        override suspend fun unhideBestSeller(stockItemId: String) = call {
            rpc.unhidePosBestseller(stockItemId)
            Unit
        }
    }
}

/** The shop's selling warehouse: `MAIN`, else the first active one. Carts and tills share it. */
internal suspend fun shopWarehouseId(rpc: RpcClient): String {
    val all = rpc.listWarehouses()
    val chosen = all.firstOrNull { it.code.equals("MAIN", ignoreCase = true) } ?: all.firstOrNull()
        ?: throw PosFailure(PosError.BusinessRule("no_warehouse", "No active warehouse is set up for this shop."))
    return chosen.id
}

/** A business outcome raised inside a gateway body; converted to [PosResult.Err] by [call]. */
class PosFailure(val error: PosError) : RuntimeException(error.toString())

internal suspend fun <T> call(block: suspend () -> T): PosResult<T> = try {
    PosResult.Ok(block())
} catch (e: CancellationException) {
    throw e
} catch (e: PosFailure) {
    PosResult.Err(e.error)
} catch (e: IOException) {
    PosResult.Err(PosError.Transient(retryable = true, message = e.message.orEmpty()))
} catch (e: Exception) {
    PosResult.Err(classify(e))
}

/** Server refusals carry the database's own message (RAISE EXCEPTION texts are operator-readable). */
internal fun classify(e: Exception): PosError {
    val msg = e.message.orEmpty()
    val lower = msg.lowercase()
    return when {
        "timeout" in lower || "unable to resolve host" in lower || "failed to connect" in lower ->
            PosError.Transient(retryable = true, message = msg)
        // Blind close: the count is out and the server wants a reason before it accepts it.
        "variance reason required" in lower -> PosError.BusinessRule("variance_reason_required", "")
        "required" in lower && ("role" in lower || "approval" in lower) ->
            PosError.BusinessRule("forbidden", serverMessage(msg))
        // Stock-ledger internals (batch ids, FIFO shortfalls) are reworded, never shown raw.
        "insufficient" in lower && ("qty" in lower || "stock" in lower) ->
            PosError.BusinessRule("insufficient_stock", "")
        "permission denied" in lower -> PosError.BusinessRule("forbidden", "")
        else -> PosError.BusinessRule("server", serverMessage(msg))
    }
}

/** First line of a PostgREST error, without the transport preamble. */
private fun serverMessage(raw: String): String =
    raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.removePrefix("ERROR:")?.trim()?.take(200).orEmpty()

internal fun cartProjection(cartId: String, lines: List<PosCartLineSummary>, currency: CurrencyCode): CartProjection {
    val mapped = lines.map { l ->
        CartLine(
            lineId = l.id,
            stockItemId = l.stockItemId,
            oemPartNumber = l.oemPartNumber.orEmpty(),
            name = l.description ?: l.oemPartNumber.orEmpty(),
            qty = l.qty,
            unitPrice = Money.ofMajor(l.unitPrice, currency),
            lineTotal = Money.ofMajor(l.lineTotal, currency),
            isCoreCharge = l.isCoreCharge,
            imageUrl = l.imageUrl,
        )
    }
    val total = Money(mapped.sumOf { it.lineTotal.minor }, currency)
    return CartProjection(cartId, currency, mapped, subtotal = total, discount = Money.zero(currency), total = total)
}

private fun PosPartMeta.toPart(fallbackName: String?) = CatalogPart(
    stockItemId = stockItemId,
    oemPartNumber = oemPartNumber,
    name = description ?: fallbackName ?: oemPartNumber,
    price = unitPrice?.let { Money.ofMajor(it, (currency ?: RpcCurrency.USD).toDomain()) },
    saleableQty = saleableQty,
    imageUrl = imageUrl,
)

internal fun RpcCurrency.toDomain(): CurrencyCode = CurrencyCode(rpcValue.uppercase())

internal fun CurrencyCode.toRpc(): RpcCurrency =
    RpcCurrency.entries.firstOrNull { it.rpcValue.equals(code, ignoreCase = true) } ?: RpcCurrency.USD

internal fun VehicleSelection.toRpc() = PosSaleVehicleSelection(
    modelSlug = modelSlug,
    modelName = modelName,
    generation = generation,
    chassisCode = chassisCode,
    engineCode = engineCode,
)

private fun PinKind.toRpc(): PosPopularItemKind = when (this) {
    PinKind.PART -> PosPopularItemKind.PART
    PinKind.MODEL -> PosPopularItemKind.MODEL
    PinKind.CATEGORY -> PosPopularItemKind.CATEGORY
    PinKind.SUBCATEGORY -> PosPopularItemKind.SUBCATEGORY
}

private fun PosPopularPin.toDomain() = PopularPin(
    kind = when (kind) {
        PosPopularItemKind.PART -> PinKind.PART
        PosPopularItemKind.MODEL -> PinKind.MODEL
        PosPopularItemKind.CATEGORY -> PinKind.CATEGORY
        PosPopularItemKind.SUBCATEGORY -> PinKind.SUBCATEGORY
    },
    key = itemKey,
    label = label,
    subtitle = subtitle,
    searchQuery = searchQuery,
    oemPartNumber = oemPartNumber,
    imageUrl = imageUrl,
    modelSlug = modelSlug,
    categoryName = categoryName,
)

private fun PopularPin.toRpc() = PosPopularPin(
    kind = kind.toRpc(),
    itemKey = key,
    label = label,
    subtitle = subtitle,
    searchQuery = searchQuery,
    makerSlug = MAKER.takeIf { modelSlug != null },
    modelSlug = modelSlug,
    categoryName = categoryName,
    oemPartNumber = oemPartNumber,
    imageUrl = imageUrl,
)

private fun JsonObject.text(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

