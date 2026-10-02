package co.zw.nissangtr.customer.visual

import androidx.annotation.DrawableRes

/**
 * The service-kit card photo for each catalogue model family (`list_customer_vehicle_master`
 * model_family), graded dark with red accents like the GT-R shot. Photos are from Wikimedia
 * Commons; authors and licences are in `assets/vehicle_photo_credits.txt`.
 */
object ExpressModelArt {
    @DrawableRes
    val Default: Int = R.drawable.pos_hero_car

    private val byFamily: Map<String, Int> = mapOf(
        "180sx" to R.drawable.kit_180sx,
        "200sx" to R.drawable.kit_200sx,
        "240sx" to R.drawable.kit_240sx,
        "280zx" to R.drawable.kit_280zx,
        "300zx" to R.drawable.kit_300zx,
        "310/310gx" to R.drawable.kit_310_310gx,
        "350z" to R.drawable.kit_350z,
        "370z" to R.drawable.kit_370z,
        "altima" to R.drawable.kit_altima,
        "armada" to R.drawable.kit_armada,
        "atlas/condor" to R.drawable.kit_atlas_condor,
        "auster/stanza" to R.drawable.kit_auster_stanza,
        "avenir" to R.drawable.kit_avenir,
        "axxess" to R.drawable.kit_axxess,
        "bassara" to R.drawable.kit_bassara,
        "be" to R.drawable.kit_be,
        "bluebird" to R.drawable.kit_bluebird,
        "bluebird sylphy" to R.drawable.kit_bluebird_sylphy,
        "caravan/homy" to R.drawable.kit_caravan_homy,
        "cedric/gloria" to R.drawable.kit_cedric_gloria,
        "cefiro" to R.drawable.kit_cefiro,
        "cima" to R.drawable.kit_cima,
        "civilian" to R.drawable.kit_civilian,
        "clipper" to R.drawable.kit_clipper,
        "crew" to R.drawable.kit_crew,
        "cube" to R.drawable.kit_cube,
        "cube/cube cubic" to R.drawable.kit_cube_cube_cubic,
        "datsun" to R.drawable.kit_datsun,
        "datsun 810" to R.drawable.kit_datsun_810,
        "dualis" to R.drawable.kit_dualis,
        "elgrand" to R.drawable.kit_elgrand,
        "exa" to R.drawable.kit_exa,
        "fairlady z" to R.drawable.kit_fairlady_z,
        "figaro" to R.drawable.kit_figaro,
        "frontier" to R.drawable.kit_frontier,
        "fuga" to R.drawable.kit_fuga,
        "hypermini" to R.drawable.kit_hypermini,
        "infiniti q45" to R.drawable.kit_infiniti_q45,
        "lafesta" to R.drawable.kit_lafesta,
        "largo" to R.drawable.kit_largo,
        "laurel" to R.drawable.kit_laurel,
        "laurel spirit" to R.drawable.kit_laurel_spirit,
        "leopard" to R.drawable.kit_leopard,
        "march" to R.drawable.kit_march,
        "maxima" to R.drawable.kit_maxima,
        "micra c+c" to R.drawable.kit_micra_c_c,
        "mistral" to R.drawable.kit_mistral,
        "moco" to R.drawable.kit_moco,
        "murano" to R.drawable.kit_murano,
        "gt-r" to R.drawable.pos_hero_car,
        "van" to R.drawable.kit_nissan_van,
        "note" to R.drawable.kit_note,
        "otti" to R.drawable.kit_otti,
        "pao" to R.drawable.kit_pao,
        "passat (volkswagen)" to R.drawable.kit_passat_volkswagen,
        "pathfinder" to R.drawable.kit_pathfinder,
        "pickup" to R.drawable.kit_pickup,
        "pino" to R.drawable.kit_pino,
        "prairie/liberty" to R.drawable.kit_prairie_liberty,
        "presage" to R.drawable.kit_presage,
        "presea" to R.drawable.kit_presea,
        "president" to R.drawable.kit_president,
        "primera" to R.drawable.kit_primera,
        "pulsar" to R.drawable.kit_pulsar,
        "pulsar/langley/liberta villa" to R.drawable.kit_pulsar_langley_liberta_villa,
        "quest" to R.drawable.kit_quest,
        "r`nessa" to R.drawable.kit_rnessa,
        "rasheen" to R.drawable.kit_rasheen,
        "rogue" to R.drawable.kit_rogue,
        "s-cargo" to R.drawable.kit_s_cargo,
        "safari" to R.drawable.kit_safari,
        "santana (volkswagen)" to R.drawable.kit_santana_volkswagen,
        "sentra/200sx" to R.drawable.kit_sentra_200sx,
        "serena" to R.drawable.kit_serena,
        "silvia" to R.drawable.kit_silvia,
        "silvia/gazelle" to R.drawable.kit_silvia_gazelle,
        "skyline" to R.drawable.kit_skyline,
        "stagea" to R.drawable.kit_stagea,
        "stanza" to R.drawable.kit_stanza,
        "sunny truck" to R.drawable.kit_sunny_truck,
        "sunny/luchino" to R.drawable.kit_sunny_luchino,
        "teana" to R.drawable.kit_teana,
        "terrano" to R.drawable.kit_terrano,
        "terrano regulus" to R.drawable.kit_terrano_regulus,
        "tiida/tiida latio" to R.drawable.kit_tiida_tiida_latio,
        "tino" to R.drawable.kit_tino,
        "titan" to R.drawable.kit_titan,
        "truck-hardbody" to R.drawable.kit_truck_hardbody,
        "vanette" to R.drawable.kit_vanette,
        "versa" to R.drawable.kit_versa,
        "wingroad/ad/expert" to R.drawable.kit_wingroad_ad_expert,
        "x-trail" to R.drawable.kit_x_trail,
        "xterra" to R.drawable.kit_xterra,
    )

    /** Longest names first, so "cube/cube cubic" wins over "cube"; a prefix must end at a space. */
    private val byLength = byFamily.keys.sortedByDescending { it.length }

    /** The photo for a selected vehicle's model ("Nissan Bluebird", "Nissan Nissan GT-R", …). */
    @DrawableRes
    fun forModel(model: String?): Int {
        val m = normalize(model ?: return Default)
        byFamily[m]?.let { return it }
        return byLength.firstOrNull { m.startsWith("$it ") }?.let(byFamily::getValue) ?: Default
    }

    private fun normalize(value: String): String = value.trim().lowercase().replace(Regex("^(nissan\\s+)+"), "").trim()
}
