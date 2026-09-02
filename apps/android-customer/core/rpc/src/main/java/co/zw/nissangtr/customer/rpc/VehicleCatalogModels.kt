package co.zw.nissangtr.customer.rpc

/**
 * Published customer vehicle-master row derived from the approved catalog_v2 release.
 * [id] is the canonical catalog vehicle key used for EPC-backed fitment validation.
 */
data class VehicleMasterRow(
    val id: String? = null,
    val vinPrefix: String? = null,
    val chassisCode: String,
    val engineCode: String? = null,
    val productionYear: Int? = null,
    val modelVariant: String,
)

/** Session fitment scope after Select vehicle confirm (cascade or VIN). */
data class SelectedFitmentVehicle(
    val make: String?,
    val model: String,
    val generation: String,
    val engine: String?,
    val vin: String? = null,
    val vinPrefix: String? = null,
    /** Canonical catalog_v2 customer vehicle key. */
    val vehicleMasterId: String? = null,
) {
    /** Compact bar label — model · chassis · engine (no technical EPC identifiers). */
    fun compactLabel(): String =
        listOfNotNull(
            model.trim().takeIf { it.isNotEmpty() },
            generation.trim().takeIf { it.isNotEmpty() },
            engine?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" · ").ifBlank {
            vin?.let { "VIN $it" }.orEmpty()
        }

    fun searchQuery(): String =
        listOfNotNull(model, generation, engine).joinToString(" ").trim()
}

/** Build cascading option lists from published [VehicleMasterRow]s only. */
object VehicleCascade {
    fun deriveMaker(row: VehicleMasterRow): String? {
        val variant = row.modelVariant.trim()
        when {
            variant.startsWith("DATSUN", ignoreCase = true) -> return "Datsun"
            variant.startsWith("Nissan", ignoreCase = true) -> return "Nissan"
            variant.startsWith("Infiniti", ignoreCase = true) -> return "Infiniti"
        }
        val vp = row.vinPrefix?.trim()?.uppercase().orEmpty()
        return when {
            vp.startsWith("JNK") || vp.startsWith("5N3") -> "Infiniti"
            vp.startsWith("JN") || vp.startsWith("SJN") || vp.startsWith("MNT") -> "Nissan"
            else -> null
        }
    }

    fun makers(rows: List<VehicleMasterRow>): List<String> =
        rows.mapNotNull { deriveMaker(it) }.distinct().sorted()

    fun models(rows: List<VehicleMasterRow>, maker: String): List<String> =
        rows.filter { deriveMaker(it) == maker }
            .map { it.modelVariant.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    fun generations(rows: List<VehicleMasterRow>, maker: String, model: String): List<String> =
        rows.filter { deriveMaker(it) == maker && it.modelVariant.trim() == model }
            .map { it.chassisCode.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

    fun engines(
        rows: List<VehicleMasterRow>,
        maker: String,
        model: String,
        generation: String,
    ): List<String> =
        rows.filter {
            deriveMaker(it) == maker &&
                it.modelVariant.trim() == model &&
                it.chassisCode.trim() == generation
        }
            .mapNotNull { it.engineCode?.trim()?.takeIf { e -> e.isNotEmpty() } }
            .distinct()
            .sorted()

    fun resolveVin(rows: List<VehicleMasterRow>, vinRaw: String): SelectedFitmentVehicle? {
        val vin = vinRaw.trim().uppercase()
        if (vin.length < 11) return null
        val needle = vin.take(11)
        val match = rows.firstOrNull { row ->
            val vp = row.vinPrefix?.trim()?.uppercase().orEmpty()
            vp.isNotEmpty() && needle.startsWith(vp)
        } ?: return null
        return SelectedFitmentVehicle(
            make = deriveMaker(match),
            model = match.modelVariant.trim(),
            generation = match.chassisCode.trim(),
            engine = match.engineCode?.trim()?.takeIf { it.isNotEmpty() },
            vin = vin,
            vinPrefix = match.vinPrefix,
            vehicleMasterId = match.id,
        )
    }

    fun fromCascade(
        maker: String,
        model: String,
        generation: String,
        engine: String?,
        rows: List<VehicleMasterRow>,
    ): SelectedFitmentVehicle? {
        val row = rows.firstOrNull {
            deriveMaker(it) == maker &&
                it.modelVariant.trim() == model &&
                it.chassisCode.trim() == generation &&
                (engine.isNullOrBlank() || it.engineCode?.trim() == engine)
        } ?: return null
        return SelectedFitmentVehicle(
            make = maker,
            model = model,
            generation = generation,
            engine = engine?.takeIf { it.isNotEmpty() } ?: row.engineCode?.trim(),
            vinPrefix = row.vinPrefix,
            vehicleMasterId = row.id,
        )
    }
}
