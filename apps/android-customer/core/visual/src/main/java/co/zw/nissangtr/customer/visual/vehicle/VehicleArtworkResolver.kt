package co.zw.nissangtr.customer.visual.vehicle

import co.zw.nissangtr.customer.rpc.SelectedFitmentVehicle
import java.text.Normalizer
import java.util.Locale

/**
 * Presentation-only mapping from the already-selected backend fitment identity to vehicle artwork.
 *
 * IMPORTANT:
 * - Does not determine fitment.
 * - Does not alter the selected vehicle.
 * - Does not call EPC or any backend.
 * - Never substitutes a different named model when no mapping exists.
 */
object VehicleArtworkResolver {

    data class VehicleArtwork(
        val label: String,
        /** Relative to Android `src/main/assets/`. */
        val assetPath: String,
    )

    private data class Rule(
        val artworkLabel: String,
        val assetPath: String,
        val modelAliases: List<String>,
        val generationAliases: List<String> = emptyList(),
        val bodyHints: List<String> = emptyList(),
        val priority: Int,
    )

    private val rules: List<Rule> = listOf(
        Rule(
            artworkLabel = "Navara D23",
            assetPath = "vehicles/nissan_navara_d23.webp",
            modelAliases = listOf("navara"),
            generationAliases = listOf("d23"),
            bodyHints = listOf(),
            priority = 420,
        ),
        Rule(
            artworkLabel = "GT-R R35",
            assetPath = "vehicles/nissan_gt_r_r35.webp",
            modelAliases = listOf("gt r", "gtr"),
            generationAliases = listOf("r35"),
            bodyHints = listOf(),
            priority = 400,
        ),
        Rule(
            artworkLabel = "GT-R R34",
            assetPath = "vehicles/nissan_gt_r_r34.webp",
            modelAliases = listOf("gt r", "gtr", "skyline"),
            generationAliases = listOf("r34", "bnr34"),
            bodyHints = listOf(),
            priority = 400,
        ),
        Rule(
            artworkLabel = "GT-R R33",
            assetPath = "vehicles/nissan_gt_r_r33.webp",
            modelAliases = listOf("gt r", "gtr", "skyline"),
            generationAliases = listOf("r33", "bcnr33"),
            bodyHints = listOf(),
            priority = 400,
        ),
        Rule(
            artworkLabel = "GT-R R32",
            assetPath = "vehicles/nissan_gt_r_r32.webp",
            modelAliases = listOf("gt r", "gtr", "skyline"),
            generationAliases = listOf("r32", "bnr32"),
            bodyHints = listOf(),
            priority = 400,
        ),
        Rule(
            artworkLabel = "Navara D22",
            assetPath = "vehicles/nissan_navara_d22.webp",
            modelAliases = listOf("navara", "hardbody", "pickup"),
            generationAliases = listOf("d22"),
            bodyHints = listOf(),
            priority = 400,
        ),
        Rule(
            artworkLabel = "Navara D40",
            assetPath = "vehicles/nissan_navara_d40.webp",
            modelAliases = listOf("navara"),
            generationAliases = listOf("d40"),
            bodyHints = listOf(),
            priority = 400,
        ),
        Rule(
            artworkLabel = "350Z",
            assetPath = "vehicles/nissan_350z.webp",
            modelAliases = listOf("350z"),
            generationAliases = listOf("z33"),
            bodyHints = listOf(),
            priority = 350,
        ),
        Rule(
            artworkLabel = "370Z",
            assetPath = "vehicles/nissan_370z.webp",
            modelAliases = listOf("370z"),
            generationAliases = listOf("z34"),
            bodyHints = listOf(),
            priority = 350,
        ),
        Rule(
            artworkLabel = "Silvia S13",
            assetPath = "vehicles/nissan_silvia_s13.webp",
            modelAliases = listOf("silvia"),
            generationAliases = listOf("s13", "ps13"),
            bodyHints = listOf(),
            priority = 350,
        ),
        Rule(
            artworkLabel = "Silvia S14",
            assetPath = "vehicles/nissan_silvia_s14.webp",
            modelAliases = listOf("silvia", "200sx"),
            generationAliases = listOf("s14", "cs14"),
            bodyHints = listOf(),
            priority = 350,
        ),
        Rule(
            artworkLabel = "Silvia S15",
            assetPath = "vehicles/nissan_silvia_s15.webp",
            modelAliases = listOf("silvia"),
            generationAliases = listOf("s15"),
            bodyHints = listOf(),
            priority = 350,
        ),
        Rule(
            artworkLabel = "180SX",
            assetPath = "vehicles/nissan_180sx.webp",
            modelAliases = listOf("180sx"),
            generationAliases = listOf("rps13", "krps13", "rs13"),
            bodyHints = listOf(),
            priority = 350,
        ),
        Rule(
            artworkLabel = "Fairlady Z Z33",
            assetPath = "vehicles/nissan_fairlady_z_z33.webp",
            modelAliases = listOf("fairlady z", "fairlady"),
            generationAliases = listOf("z33", "hz33"),
            bodyHints = listOf(),
            priority = 340,
        ),
        Rule(
            artworkLabel = "Fairlady Z Z34",
            assetPath = "vehicles/nissan_fairlady_z_z34.webp",
            modelAliases = listOf("fairlady z", "fairlady"),
            generationAliases = listOf("z34", "hz34"),
            bodyHints = listOf(),
            priority = 340,
        ),
        Rule(
            artworkLabel = "Skyline V35",
            assetPath = "vehicles/nissan_skyline_v35.webp",
            modelAliases = listOf("skyline"),
            generationAliases = listOf("v35", "pv35", "cpv35", "nv35", "hv35"),
            bodyHints = listOf(),
            priority = 320,
        ),
        Rule(
            artworkLabel = "Skyline V36",
            assetPath = "vehicles/nissan_skyline_v36.webp",
            modelAliases = listOf("skyline"),
            generationAliases = listOf("v36", "pv36", "kv36", "ckv36", "nv36"),
            bodyHints = listOf(),
            priority = 320,
        ),
        Rule(
            artworkLabel = "Skyline V37",
            assetPath = "vehicles/nissan_skyline_v37.webp",
            modelAliases = listOf("skyline"),
            generationAliases = listOf("v37", "hv37", "hnv37", "yv37", "zv37"),
            bodyHints = listOf(),
            priority = 320,
        ),
        Rule(
            artworkLabel = "Skyline V38",
            assetPath = "vehicles/nissan_skyline_v38.webp",
            modelAliases = listOf("skyline"),
            generationAliases = listOf("v38"),
            bodyHints = listOf(),
            priority = 320,
        ),
        Rule(
            artworkLabel = "X-Trail T30",
            assetPath = "vehicles/nissan_x_trail_t30.webp",
            modelAliases = listOf("x trail", "xtrail"),
            generationAliases = listOf("t30"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "X-Trail T31",
            assetPath = "vehicles/nissan_x_trail_t31.webp",
            modelAliases = listOf("x trail", "xtrail"),
            generationAliases = listOf("t31"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "X-Trail T32",
            assetPath = "vehicles/nissan_x_trail_t32.webp",
            modelAliases = listOf("x trail", "xtrail"),
            generationAliases = listOf("t32", "nt32", "hnt32", "ht32"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "X-Trail T33",
            assetPath = "vehicles/nissan_x_trail_t33.webp",
            modelAliases = listOf("x trail", "xtrail"),
            generationAliases = listOf("t33", "nt33", "snt33"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Murano Z50",
            assetPath = "vehicles/nissan_murano_z50.webp",
            modelAliases = listOf("murano"),
            generationAliases = listOf("z50", "pz50", "pnz50"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Murano Z51",
            assetPath = "vehicles/nissan_murano_z51.webp",
            modelAliases = listOf("murano"),
            generationAliases = listOf("z51", "tz51", "tnz51", "pnz51"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Murano Z52",
            assetPath = "vehicles/nissan_murano_z52.webp",
            modelAliases = listOf("murano"),
            generationAliases = listOf("z52"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Pathfinder R51",
            assetPath = "vehicles/nissan_pathfinder_r51.webp",
            modelAliases = listOf("pathfinder"),
            generationAliases = listOf("r51"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Pathfinder R52",
            assetPath = "vehicles/nissan_pathfinder_r52.webp",
            modelAliases = listOf("pathfinder"),
            generationAliases = listOf("r52"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Patrol Y60",
            assetPath = "vehicles/nissan_patrol_y60.webp",
            modelAliases = listOf("patrol", "safari"),
            generationAliases = listOf("y60", "gq"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Patrol Y61",
            assetPath = "vehicles/nissan_patrol_y61.webp",
            modelAliases = listOf("patrol", "safari"),
            generationAliases = listOf("y61", "gu"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Serena C24",
            assetPath = "vehicles/nissan_serena_c24.webp",
            modelAliases = listOf("serena"),
            generationAliases = listOf("c24", "pc24", "pnc24", "tc24", "tnc24"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Serena C25",
            assetPath = "vehicles/nissan_serena_c25.webp",
            modelAliases = listOf("serena"),
            generationAliases = listOf("c25", "cc25", "nc25", "cnc25"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Serena C26",
            assetPath = "vehicles/nissan_serena_c26.webp",
            modelAliases = listOf("serena"),
            generationAliases = listOf("c26", "fc26", "fnc26", "hc26", "hnc26"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Serena C27",
            assetPath = "vehicles/nissan_serena_c27.webp",
            modelAliases = listOf("serena"),
            generationAliases = listOf("c27", "gc27", "gfc27", "hfc27"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Elgrand E50",
            assetPath = "vehicles/nissan_elgrand_e50.webp",
            modelAliases = listOf("elgrand"),
            generationAliases = listOf("e50", "ale50", "alwe50", "ape50", "apwe50"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Elgrand E51",
            assetPath = "vehicles/nissan_elgrand_e51.webp",
            modelAliases = listOf("elgrand"),
            generationAliases = listOf("e51", "ne51", "me51", "mne51"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "Elgrand E52",
            assetPath = "vehicles/nissan_elgrand_e52.webp",
            modelAliases = listOf("elgrand"),
            generationAliases = listOf("e52", "ne52", "te52", "tne52"),
            bodyHints = listOf(),
            priority = 300,
        ),
        Rule(
            artworkLabel = "NP300",
            assetPath = "vehicles/nissan_np300.webp",
            modelAliases = listOf("np300"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 260,
        ),
        Rule(
            artworkLabel = "Pulsar Hatch",
            assetPath = "vehicles/nissan_pulsar_hatch.webp",
            modelAliases = listOf("pulsar"),
            generationAliases = listOf(),
            bodyHints = listOf("hatch", "hatchback"),
            priority = 260,
        ),
        Rule(
            artworkLabel = "Tiida Hatch",
            assetPath = "vehicles/nissan_tiida_hatch.webp",
            modelAliases = listOf("tiida"),
            generationAliases = listOf(),
            bodyHints = listOf("hatch", "hatchback"),
            priority = 260,
        ),
        Rule(
            artworkLabel = "Versa Hatch",
            assetPath = "vehicles/nissan_versa_hatch.webp",
            modelAliases = listOf("versa"),
            generationAliases = listOf(),
            bodyHints = listOf("hatch", "hatchback"),
            priority = 260,
        ),
        Rule(
            artworkLabel = "Sunny Hatch",
            assetPath = "vehicles/nissan_sunny_hatch.webp",
            modelAliases = listOf("sunny"),
            generationAliases = listOf(),
            bodyHints = listOf("hatch", "hatchback"),
            priority = 260,
        ),
        Rule(
            artworkLabel = "Primera Wagon",
            assetPath = "vehicles/nissan_primera_wagon.webp",
            modelAliases = listOf("primera"),
            generationAliases = listOf(),
            bodyHints = listOf("wagon", "estate"),
            priority = 260,
        ),
        Rule(
            artworkLabel = "Frontier",
            assetPath = "vehicles/nissan_frontier.webp",
            modelAliases = listOf("frontier"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 250,
        ),
        Rule(
            artworkLabel = "200SX Silvia",
            assetPath = "vehicles/nissan_200sx_silvia.webp",
            modelAliases = listOf("200sx"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 230,
        ),
        Rule(
            artworkLabel = "Skyline GT-R",
            assetPath = "vehicles/nissan_skyline_gt_r.webp",
            modelAliases = listOf("skyline gt r", "skyline gtr"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 220,
        ),
        Rule(
            artworkLabel = "Almera",
            assetPath = "vehicles/nissan_almera.webp",
            modelAliases = listOf("almera"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Altima",
            assetPath = "vehicles/nissan_altima.webp",
            modelAliases = listOf("altima"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Bluebird",
            assetPath = "vehicles/nissan_bluebird.webp",
            modelAliases = listOf("bluebird"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Cefiro",
            assetPath = "vehicles/nissan_cefiro.webp",
            modelAliases = listOf("cefiro"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Cima",
            assetPath = "vehicles/nissan_cima.webp",
            modelAliases = listOf("cima"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Fuga",
            assetPath = "vehicles/nissan_fuga.webp",
            modelAliases = listOf("fuga"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Gloria",
            assetPath = "vehicles/nissan_gloria.webp",
            modelAliases = listOf("gloria"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Laurel",
            assetPath = "vehicles/nissan_laurel.webp",
            modelAliases = listOf("laurel"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Maxima",
            assetPath = "vehicles/nissan_maxima.webp",
            modelAliases = listOf("maxima"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Primera",
            assetPath = "vehicles/nissan_primera.webp",
            modelAliases = listOf("primera"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Pulsar",
            assetPath = "vehicles/nissan_pulsar.webp",
            modelAliases = listOf("pulsar"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Sentra",
            assetPath = "vehicles/nissan_sentra.webp",
            modelAliases = listOf("sentra"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Silvia Sedan",
            assetPath = "vehicles/nissan_silvia_sedan.webp",
            modelAliases = listOf("silvia sedan"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Stanza",
            assetPath = "vehicles/nissan_stanza.webp",
            modelAliases = listOf("stanza"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Sunny",
            assetPath = "vehicles/nissan_sunny.webp",
            modelAliases = listOf("sunny"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Sylphy",
            assetPath = "vehicles/nissan_sylphy.webp",
            modelAliases = listOf("sylphy", "bluebird sylphy"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Teana",
            assetPath = "vehicles/nissan_teana.webp",
            modelAliases = listOf("teana"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Tiida Sedan",
            assetPath = "vehicles/nissan_tiida_sedan.webp",
            modelAliases = listOf("tiida", "tiida latio", "latio"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Versa",
            assetPath = "vehicles/nissan_versa.webp",
            modelAliases = listOf("versa"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Vertex",
            assetPath = "vehicles/nissan_vertex.webp",
            modelAliases = listOf("vertex"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Avenir Sedan",
            assetPath = "vehicles/nissan_avenir_sedan.webp",
            modelAliases = listOf("avenir sedan"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Presea",
            assetPath = "vehicles/nissan_presea.webp",
            modelAliases = listOf("presea"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Cedric",
            assetPath = "vehicles/nissan_cedric.webp",
            modelAliases = listOf("cedric"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "March Micra",
            assetPath = "vehicles/nissan_march_micra.webp",
            modelAliases = listOf("march", "micra"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Note",
            assetPath = "vehicles/nissan_note.webp",
            modelAliases = listOf("note"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Tiida Hatch",
            assetPath = "vehicles/nissan_tiida_hatch.webp",
            modelAliases = listOf("tiida hatch"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Pulsar Hatch",
            assetPath = "vehicles/nissan_pulsar_hatch.webp",
            modelAliases = listOf("pulsar hatch"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Leaf",
            assetPath = "vehicles/nissan_leaf.webp",
            modelAliases = listOf("leaf"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Juke",
            assetPath = "vehicles/nissan_juke.webp",
            modelAliases = listOf("juke"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Kicks",
            assetPath = "vehicles/nissan_kicks.webp",
            modelAliases = listOf("kicks"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Versa Hatch",
            assetPath = "vehicles/nissan_versa_hatch.webp",
            modelAliases = listOf("versa hatch"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Sunny Hatch",
            assetPath = "vehicles/nissan_sunny_hatch.webp",
            modelAliases = listOf("sunny hatch"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Silvia Hatch",
            assetPath = "vehicles/nissan_silvia_hatch.webp",
            modelAliases = listOf("silvia hatch"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Prairie Hatch",
            assetPath = "vehicles/nissan_prairie_hatch.webp",
            modelAliases = listOf("prairie", "liberty"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Terrano",
            assetPath = "vehicles/nissan_terrano.webp",
            modelAliases = listOf("terrano", "mistral"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Avenir Wagon",
            assetPath = "vehicles/nissan_avenir_wagon.webp",
            modelAliases = listOf("avenir"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Stagea",
            assetPath = "vehicles/nissan_stagea.webp",
            modelAliases = listOf("stagea"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Wingroad",
            assetPath = "vehicles/nissan_wingroad.webp",
            modelAliases = listOf("wingroad"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Primera Wagon",
            assetPath = "vehicles/nissan_primera_wagon.webp",
            modelAliases = listOf("primera wagon"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Expert",
            assetPath = "vehicles/nissan_expert.webp",
            modelAliases = listOf("expert"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "AD Wagon",
            assetPath = "vehicles/nissan_ad_wagon.webp",
            modelAliases = listOf("ad wagon", "ad van", "ad expert"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Lafesta",
            assetPath = "vehicles/nissan_lafesta.webp",
            modelAliases = listOf("lafesta"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Quest",
            assetPath = "vehicles/nissan_quest.webp",
            modelAliases = listOf("quest"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Titan",
            assetPath = "vehicles/nissan_titan.webp",
            modelAliases = listOf("titan"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "NV200",
            assetPath = "vehicles/nissan_nv200.webp",
            modelAliases = listOf("nv200"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "NV350 Caravan",
            assetPath = "vehicles/nissan_nv350_caravan.webp",
            modelAliases = listOf("nv350", "caravan", "homy"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Vanette",
            assetPath = "vehicles/nissan_vanette.webp",
            modelAliases = listOf("vanette"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Atlas",
            assetPath = "vehicles/nissan_atlas.webp",
            modelAliases = listOf("atlas", "condor"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Civilian",
            assetPath = "vehicles/nissan_civilian.webp",
            modelAliases = listOf("civilian"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Cabstar",
            assetPath = "vehicles/nissan_cabstar.webp",
            modelAliases = listOf("cabstar"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Dayz",
            assetPath = "vehicles/nissan_dayz.webp",
            modelAliases = listOf("dayz"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Roox",
            assetPath = "vehicles/nissan_roox.webp",
            modelAliases = listOf("roox"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
        Rule(
            artworkLabel = "Clipper",
            assetPath = "vehicles/nissan_clipper.webp",
            modelAliases = listOf("clipper"),
            generationAliases = listOf(),
            bodyHints = listOf(),
            priority = 200,
        ),
    ).sortedByDescending { it.priority }

    /**
     * Resolve artwork after [SelectedFitmentVehicle] has already been established by existing fitment logic.
     *
     * [bodyHint] is optional and should only be supplied when the existing catalog genuinely knows body
     * type (e.g. hatch/wagon). It must never be guessed from engine/VIN.
     */
    fun resolve(
        vehicle: SelectedFitmentVehicle?,
        bodyHint: String? = null,
    ): VehicleArtwork? {
        if (vehicle == null) return null

        val make = normalize(vehicle.make.orEmpty())
        if (make.isNotBlank() && make !in setOf("nissan", "datsun", "infiniti")) return null

        val model = normalize(vehicle.model)
        val generation = normalize(vehicle.generation)
        val body = normalize(bodyHint.orEmpty())

        // 1. Generation/chassis-specific artwork always wins.
        rules.firstOrNull { rule ->
            rule.generationAliases.isNotEmpty() &&
                rule.modelAliases.any { alias -> containsAlias(model, alias) } &&
                rule.generationAliases.any { alias -> containsAlias(generation, alias) || containsAlias(model, alias) }
        }?.let { return it.toArtwork() }

        // 2. Body-specific visual variant, only when body metadata is genuinely supplied.
        if (body.isNotBlank()) {
            rules.firstOrNull { rule ->
                rule.bodyHints.isNotEmpty() &&
                    rule.modelAliases.any { alias -> containsAlias(model, alias) } &&
                    rule.bodyHints.any { hint -> containsAlias(body, hint) }
            }?.let { return it.toArtwork() }
        }

        // 3. Exact/family aliases.
        rules.firstOrNull { rule ->
            rule.generationAliases.isEmpty() &&
                rule.bodyHints.isEmpty() &&
                rule.modelAliases.any { alias -> containsAlias(model, alias) }
        }?.let { return it.toArtwork() }

        // 4. No wrong-car substitution. Caller renders the generic Nissan silhouette.
        return null
    }

    private fun Rule.toArtwork() = VehicleArtwork(
        label = artworkLabel,
        assetPath = assetPath,
    )

    internal fun normalize(raw: String): String {
        val ascii = Normalizer.normalize(raw, Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .replace(Regex("\\s+"), " ")

        return ascii
            .removePrefix("nissan ")
            .removePrefix("datsun ")
            .removePrefix("infiniti ")
            .trim()
    }

    private fun containsAlias(haystack: String, rawAlias: String): Boolean {
        val alias = normalize(rawAlias)
        if (alias.isBlank()) return false
        if (haystack == alias) return true
        // Space-bound token sequence prevents `note` accidentally matching unrelated strings.
        return " $haystack ".contains(" $alias ")
    }
}
