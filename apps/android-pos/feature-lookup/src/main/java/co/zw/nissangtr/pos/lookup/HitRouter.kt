package co.zw.nissangtr.pos.lookup

import co.zw.nissangtr.pos.api.CatalogHit
import co.zw.nissangtr.pos.api.CatalogHitDto
import co.zw.nissangtr.pos.api.OemNormalize
import co.zw.nissangtr.pos.api.PartHit
import co.zw.nissangtr.pos.api.PncHit
import co.zw.nissangtr.pos.api.VehicleHit
import co.zw.nissangtr.pos.api.VehicleLatch
import co.zw.nissangtr.pos.api.toTyped

/**
 * §16.2 hit router — part → oems hydrate; vehicle → latch + SHOP STOCK; pnc → EPC section.
 * Never adds vehicle/PNC rows to the cart.
 */
sealed class HitRouteAction {
    data class HydrateOems(val oems: List<String>) : HitRouteAction()
    data class LatchVehicle(
        val latch: VehicleLatch,
        val switchToShopStock: Boolean = true,
    ) : HitRouteAction()
    data class OpenEpcSection(
        val pncCode: String,
        val switchToEpc: Boolean = true,
    ) : HitRouteAction()
    data object Ignore : HitRouteAction()
}

object HitRouter {

    fun route(hits: List<CatalogHit>): List<HitRouteAction> {
        val actions = mutableListOf<HitRouteAction>()
        val oems = mutableListOf<String>()
        for (hit in hits) {
            when (hit) {
                is PartHit -> {
                    val oem = OemNormalize.normalize(hit.oemPartNumber)
                    if (oem.isNotEmpty()) oems.add(oem)
                }
                is VehicleHit -> {
                    val chassis = hit.chassisCode?.trim().orEmpty()
                    if (chassis.isNotEmpty()) {
                        actions.add(
                            HitRouteAction.LatchVehicle(
                                latch = VehicleLatch(
                                    chassisCode = chassis.uppercase(),
                                    engineCode = hit.engineCode?.trim()?.uppercase(),
                                    modelVariant = hit.modelVariant,
                                    vinPrefix = hit.vinPrefix,
                                    productionYear = hit.productionYear,
                                ),
                            ),
                        )
                    }
                }
                is PncHit -> {
                    val pnc = hit.pncCode.trim()
                    if (pnc.isNotEmpty()) {
                        actions.add(HitRouteAction.OpenEpcSection(pncCode = pnc))
                    }
                }
            }
        }
        if (oems.isNotEmpty()) {
            actions.add(0, HitRouteAction.HydrateOems(oems.distinct()))
        }
        return actions.ifEmpty { listOf(HitRouteAction.Ignore) }
    }

    fun routeDtos(dtos: List<CatalogHitDto>): List<HitRouteAction> =
        route(dtos.mapNotNull { it.toTyped() })
}
