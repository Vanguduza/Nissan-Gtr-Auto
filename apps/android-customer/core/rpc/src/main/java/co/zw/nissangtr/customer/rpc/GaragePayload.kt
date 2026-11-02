package co.zw.nissangtr.customer.rpc

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Own-customer authorization stays server-side; no customer/profile id is accepted. */
internal fun GarageVehicleInput.toRpcBody(): JsonObject = buildJsonObject {
    fun nullable(name: String, value: String?) {
        if (value.isNullOrBlank()) put(name, JsonNull) else put(name, value.trim())
    }
    nullable("p_id", id)
    nullable("p_make", make)
    nullable("p_model", model)
    nullable("p_generation", generation)
    nullable("p_engine", engine)
    nullable("p_vin", vin)
    put("p_is_primary", isPrimary)
    nullable("p_chassis_code", chassisCode)
    nullable("p_model_slug", modelSlug)
    nullable("p_vehicle_master_id", vehicleMasterId)
}
