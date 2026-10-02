package co.zw.nissangtr.pos.domain.model

/** Header cascade levels (Blueprint §8). Make is never part of the cascade (owner D2, delta D-013). */
data class VehicleModel(val slug: String, val name: String)

data class VehicleGeneration(val chassisCode: String, val label: String)

/** A confirmed vehicle: the session fitment context that filters discovery until cleared (§8.3). */
data class VehicleSelection(
    val modelSlug: String,
    val modelName: String,
    val generation: String,
    val chassisCode: String,
    val engineCode: String,
) {
    val label: String get() = listOf(modelName, chassisCode, engineCode).filter(String::isNotBlank).joinToString(" ")
}

/** Cascade field state. Selecting a level enables and resets the levels to its right (§8.1). */
data class VehicleCascade(
    val models: List<VehicleModel> = emptyList(),
    val generations: List<VehicleGeneration> = emptyList(),
    val engines: List<String> = emptyList(),
    val model: VehicleModel? = null,
    val generation: VehicleGeneration? = null,
    val engine: String? = null,
) {
    val generationEnabled: Boolean get() = model != null
    val engineEnabled: Boolean get() = generation != null

    fun selection(): VehicleSelection? {
        val m = model ?: return null
        val g = generation ?: return null
        val e = engine ?: return null
        return VehicleSelection(m.slug, m.name, g.label, g.chassisCode, e)
    }
}
