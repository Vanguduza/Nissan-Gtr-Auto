package co.zw.nissangtr.pos.domain.gateway

import co.zw.nissangtr.pos.domain.model.CartProjection
import co.zw.nissangtr.pos.domain.model.CatalogPart
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Operator
import co.zw.nissangtr.pos.domain.model.PopularPin
import co.zw.nissangtr.pos.domain.model.VehicleGeneration
import co.zw.nissangtr.pos.domain.model.VehicleModel
import co.zw.nissangtr.pos.domain.model.VehicleSelection
import co.zw.nissangtr.pos.domain.result.PosResult

/**
 * Narrow, feature-owned gateways (Blueprint §10.2, ARCH-04). Declared here, implemented in
 * `pos-data`, faked in tests. Business outcomes come back as [PosResult], never as exceptions.
 */
interface CatalogGateway {
    /** Fitment-aware search (`search_pos_vehicle_spares`); [vehicle] narrows results when set. */
    suspend fun search(query: String, vehicle: VehicleSelection?): PosResult<List<CatalogPart>>

    /** Server-ranked best sellers (`list_pos_popular_spares`). */
    suspend fun bestSellers(): PosResult<List<CatalogPart>>
}

interface FitmentGateway {
    suspend fun models(): PosResult<List<VehicleModel>>
    suspend fun generations(model: VehicleModel): PosResult<List<VehicleGeneration>>
    suspend fun engines(model: VehicleModel, generation: VehicleGeneration): PosResult<List<String>>
}

interface CartGateway {
    suspend fun open(currency: CurrencyCode): PosResult<CartProjection>
    suspend fun addLine(cartId: String, part: CatalogPart, qty: Double): PosResult<CartProjection>
    suspend fun setQuantity(cartId: String, lineId: String, qty: Double): PosResult<CartProjection>
    suspend fun removeLine(cartId: String, lineId: String): PosResult<CartProjection>
    suspend fun setVehicle(cartId: String, vehicle: VehicleSelection?): PosResult<Unit>
}

/** Operator pins and the D1 best-seller hide list (owner-only RLS on the server). */
interface PinGateway {
    suspend fun pins(): PosResult<List<PopularPin>>
    suspend fun pin(pin: PopularPin): PosResult<Unit>
    suspend fun unpin(pin: PopularPin): PosResult<Unit>
    suspend fun hiddenBestSellers(): PosResult<Set<String>>
    suspend fun hideBestSeller(stockItemId: String): PosResult<Unit>
    suspend fun unhideBestSeller(stockItemId: String): PosResult<Unit>
}

interface SessionGateway {
    suspend fun operator(): PosResult<Operator>
}
