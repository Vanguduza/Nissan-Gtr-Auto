package co.zw.nissangtr.customer.rpc

/**
 * Live `vehicle_master` row — cascade source for maker → model → generation → engine.
 * Maker is derived from VIN WMI / variant brand prefixes present in the row (never invented).
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
) {
    /** Compact bar label — model · chassis · engine (no maker / Nissan tag). */
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

/** Build cascading option lists from live [VehicleMasterRow]s only. */
object VehicleCascade {
    /**
     * Brand labels matched at the start of [VehicleMasterRow.modelVariant] (longest first).
     * Parity with `apps/web/lib/vehicle-catalog.ts`.
     */
    private val variantBrandPrefixes = listOf(
        "Mercedes-Benz", "Land Rover", "Alfa Romeo", "Volkswagen", "Infiniti", "Datsun",
        "Nissan", "Toyota", "Lexus", "Honda", "Acura", "Mazda", "Mitsubishi", "Subaru",
        "Suzuki", "Daihatsu", "Isuzu", "Hino", "Hyundai", "Kia", "Genesis", "BMW", "Mini",
        "Smart", "Audi", "Skoda", "Seat", "Porsche", "Ford", "Lincoln", "Chevrolet",
        "Cadillac", "Buick", "GMC", "Jeep", "Dodge", "Chrysler", "Ram", "Volvo", "Jaguar",
        "Peugeot", "Citroen", "Renault", "Opel", "Fiat",
    ).sortedByDescending { it.length }

    /**
     * VIN WMI → maker (longest prefix first). Includes SJN / MNT / VSK / MDH / ADN
     * so Thai / SA / EU Nissan plants are not dropped from the Maker dropdown.
     */
    private val vinWmiMakers = listOf(
        "JNK" to "Infiniti", "5N3" to "Infiniti",
        "SJN" to "Nissan", "MNT" to "Nissan", "MDH" to "Nissan", "VSK" to "Nissan",
        "ADN" to "Nissan", "3N1" to "Nissan", "5N1" to "Nissan", "1N4" to "Nissan",
        "1N6" to "Nissan", "JN1" to "Nissan", "JN" to "Nissan",
        "JTD" to "Toyota", "JT2" to "Toyota", "JTE" to "Toyota", "JTM" to "Toyota",
        "4T1" to "Toyota", "5TD" to "Toyota", "2T1" to "Toyota", "MR0" to "Toyota",
        "JTJ" to "Lexus", "JTH" to "Lexus", "2T2" to "Lexus", "58A" to "Lexus",
        "JHM" to "Honda", "1HG" to "Honda", "2HG" to "Honda", "3CZ" to "Honda",
        "SHH" to "Honda", "JH4" to "Acura", "19U" to "Acura", "2HN" to "Acura",
        "JM1" to "Mazda", "JM3" to "Mazda", "1YV" to "Mazda", "3MZ" to "Mazda",
        "JA3" to "Mitsubishi", "JA4" to "Mitsubishi", "4A3" to "Mitsubishi",
        "6MM" to "Mitsubishi",
        "JF1" to "Subaru", "JF2" to "Subaru", "4S3" to "Subaru", "4S4" to "Subaru",
        "JS2" to "Suzuki", "JS3" to "Suzuki", "JSA" to "Suzuki", "TSM" to "Suzuki",
        "KMH" to "Hyundai", "KM8" to "Hyundai", "5NP" to "Hyundai", "5NM" to "Hyundai",
        "KNA" to "Kia", "KND" to "Kia", "5XY" to "Kia", "3KP" to "Kia",
        "WBA" to "BMW", "WBS" to "BMW", "WBY" to "BMW", "4US" to "BMW", "5UX" to "BMW",
        "WDD" to "Mercedes-Benz", "WDB" to "Mercedes-Benz", "4JG" to "Mercedes-Benz",
        "WAU" to "Audi", "WA1" to "Audi",
        "WVW" to "Volkswagen", "WV1" to "Volkswagen", "WV2" to "Volkswagen",
        "3VW" to "Volkswagen", "1VW" to "Volkswagen",
        "1FA" to "Ford", "1FT" to "Ford", "1FM" to "Ford", "WF0" to "Ford",
        "SAL" to "Land Rover", "SAJ" to "Jaguar",
    ).sortedByDescending { it.first.length }

    private val nissanModelToken = Regex(
        "^(MICRA|QASHQAI\\+?\\d*|JUKE|NAVARA|X-?TRAIL|PULSAR|PATROL|ALTIMA|SENTRA|" +
            "MAXIMA|LEAF|370Z|350Z|GT-?R|SKYLINE|ALMERA|TIIDA|TEANA|PATHFINDER|" +
            "MURANO|NP300|HARDBODY|CARAVAN|SYLPHY|PRIMERA|NOTE|CUBE)\\b",
        RegexOption.IGNORE_CASE,
    )

    fun deriveMaker(row: VehicleMasterRow): String? {
        val variant = row.modelVariant.trim()
        val upper = variant.uppercase()
        for (brand in variantBrandPrefixes) {
            if (upper.startsWith(brand.uppercase())) return brand
        }
        val vp = row.vinPrefix?.trim()?.uppercase().orEmpty()
        if (vp.isNotEmpty()) {
            for ((prefix, maker) in vinWmiMakers) {
                if (vp.startsWith(prefix)) return maker
            }
        }
        if (nissanModelToken.containsMatchIn(variant)) return "Nissan"
        return null
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
            vp.isNotEmpty() && (needle.startsWith(vp) || vp.startsWith(needle.take(vp.length.coerceAtMost(needle.length))))
        } ?: return null
        return SelectedFitmentVehicle(
            make = deriveMaker(match),
            model = match.modelVariant.trim(),
            generation = match.chassisCode.trim(),
            engine = match.engineCode?.trim()?.takeIf { it.isNotEmpty() },
            vin = vin,
            vinPrefix = match.vinPrefix,
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
        )
    }
}
