package co.zw.nissangtr.management.pos.offline.bundle

import co.zw.nissangtr.management.rpc.VehicleMasterEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

/**
 * The complete catalogue on this tablet (format `gtr-pos-offline/1`, built by
 * `data-pipeline/scripts/build_pos_offline_bundle.py`): downloaded file by file into app-private
 * storage, each file resumed where it stopped and checked against its SHA-256 before it counts, then
 * read to answer the same questions as `catalog-live-r2` with no connection.
 */
class OfflineCatalogBundle(
    private val dir: File,
    private val http: BundleHttp = UrlConnectionBundleHttp,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    enum class Phase { None, Downloading, Paused, Ready }

    data class Status(
        val phase: Phase,
        val release: String? = null,
        val doneBytes: Long = 0,
        val totalBytes: Long = 0,
        val downloadedAtMs: Long? = null,
        val message: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()
    private val _status = MutableStateFlow(Status(Phase.None))
    val status: StateFlow<Status> = _status.asStateFlow()

    private var saved: Saved? = null
    private var vehicles: VehiclesFile? = null
    private val rowsCache = object : LinkedHashMap<String, List<JsonObject>>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<JsonObject>>?) = size > 4
    }
    private val routesCache = object : LinkedHashMap<String, JsonObject>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, JsonObject>?) = size > 8
    }

    val ready: Boolean get() = saved?.complete == true

    private data class FileEntry(val path: String, val bytes: Long, val sha256: String)
    private data class Saved(
        val release: String,
        val build: String,
        val routeBuckets: Int,
        val totalBytes: Long,
        val files: List<FileEntry>,
        val done: MutableSet<String>,
        var complete: Boolean,
        var downloadedAtMs: Long?,
    )

    private data class VehiclesFile(val vehicles: List<VehicleMasterEntry>, val shards: Map<String, List<Loc>>)
    private data class Loc(val pack: Int, val offset: Long, val length: Int)

    /** Loads what is already on the device. */
    suspend fun init() = withContext(Dispatchers.IO) {
        lock.withLock {
            saved = readState()
            publish(null)
        }
    }

    // ------------------------------------------------------------------------------- download

    /**
     * Downloads (or finishes) the published bundle. [fetchManifest] returns the signed manifest
     * (`catalog-live-r2` `offline-bundle`); it is asked again when the signed links expire.
     */
    suspend fun download(fetchManifest: suspend () -> JsonObject) = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                var manifest = fetchManifest()
                require(manifest.str("format") == FORMAT) { "This offline catalogue needs a newer version of the POS." }
                val build = manifest.str("build") ?: error("The offline catalogue manifest has no build.")
                val current = saved
                val state = if (current != null && current.build == build) {
                    current
                } else {
                    // A new build replaces the old one completely.
                    dir.deleteRecursively()
                    dir.mkdirs()
                    clearCaches()
                    Saved(
                        release = manifest.str("release").orEmpty(),
                        build = build,
                        routeBuckets = manifest["route_buckets"]?.jsonPrimitive?.intOrNull ?: 64,
                        totalBytes = manifest["total_bytes"]?.jsonPrimitive?.longOrNull ?: 0L,
                        files = manifest.files().map { FileEntry(it.str("path")!!, it.long("bytes"), it.str("sha256")!!) },
                        done = mutableSetOf(),
                        complete = false,
                        downloadedAtMs = null,
                    ).also { saved = it; writeState(it) }
                }
                if (!state.complete) {
                    _status.value = Status(Phase.Downloading, state.release, state.files.filter { it.path in state.done }.sumOf { it.bytes }, state.totalBytes)
                    for (file in state.files) {
                        if (file.path in state.done) continue
                        currentCoroutineContext().ensureActive()
                        val url = manifest.urlFor(file.path) ?: fetchManifest().also { manifest = it }.urlFor(file.path)
                            ?: error("No download link for ${file.path}.")
                        try {
                            fetchFile(file, url, state, currentCoroutineContext())
                        } catch (e: HttpStatusException) {
                            if (e.status != 403 && e.status != 400) throw e
                            // Signed links last a few hours; a long download asks for fresh ones.
                            manifest = fetchManifest()
                            fetchFile(file, manifest.urlFor(file.path) ?: throw e, state, currentCoroutineContext())
                        }
                        state.done += file.path
                        writeState(state)
                    }
                    state.complete = true
                    state.downloadedAtMs = clock()
                    writeState(state)
                }
                publish(null)
            } catch (e: CancellationException) {
                publish("Download paused.")
                throw e
            } catch (e: Exception) {
                publish(e.message ?: "Download failed.")
                throw e
            }
        }
    }

    private fun fetchFile(file: FileEntry, url: String, state: Saved, job: kotlin.coroutines.CoroutineContext) {
        val target = File(dir, file.path)
        val part = File(dir, file.path + ".part")
        val doneBefore = state.files.filter { it.path in state.done }.sumOf { it.bytes }
        var have = if (part.exists()) part.length() else 0L
        if (have > file.bytes) {
            part.delete()
            have = 0
        }
        if (have < file.bytes) {
            val response = http.open(url, have)
            val append = response.status == 206 && have > 0
            if (response.status != 200 && response.status != 206) {
                response.body.close()
                throw HttpStatusException(response.status, "Download failed (${response.status}) at ${file.path}.")
            }
            if (!append) have = 0
            response.body.use { input ->
                FileOutputStream(part, append).use { out ->
                    val buffer = ByteArray(256 * 1024)
                    var lastPublished = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        have += n
                        if (have - lastPublished > 2 * 1024 * 1024) {
                            lastPublished = have
                            _status.value = Status(Phase.Downloading, state.release, doneBefore + have, state.totalBytes)
                        }
                        job.ensureActive()
                    }
                }
            }
        }
        if (part.length() != file.bytes || sha256(part) != file.sha256) {
            part.delete()
            throw IOException("${file.path} arrived damaged. Continue the download to fetch it again.")
        }
        if (!part.renameTo(target)) throw IOException("Could not store ${file.path}.")
        _status.value = Status(Phase.Downloading, state.release, doneBefore + file.bytes, state.totalBytes)
    }

    /** Deletes the offline catalogue from this device. */
    suspend fun remove() = withContext(Dispatchers.IO) {
        lock.withLock {
            dir.deleteRecursively()
            saved = null
            clearCaches()
            publish(null)
        }
    }

    private fun publish(message: String?) {
        val s = saved
        _status.value = when {
            s == null -> Status(Phase.None, message = message)
            s.complete -> Status(Phase.Ready, s.release, s.totalBytes, s.totalBytes, s.downloadedAtMs)
            else -> Status(
                Phase.Paused,
                s.release,
                s.files.filter { it.path in s.done }.sumOf { it.bytes },
                s.totalBytes,
                message = message ?: "Download paused. Continue to finish it.",
            )
        }
    }

    private fun clearCaches() {
        vehicles = null
        rowsCache.clear()
        routesCache.clear()
    }

    private fun readState(): Saved? {
        val f = File(dir, STATE)
        if (!f.exists()) return null
        return runCatching {
            val o = json.parseToJsonElement(f.readText()).jsonObject
            Saved(
                release = o.str("release").orEmpty(),
                build = o.str("build")!!,
                routeBuckets = o["route_buckets"]!!.jsonPrimitive.intOrNull!!,
                totalBytes = o.long("total_bytes"),
                files = o["files"]!!.jsonArray.map { it.jsonObject }.map { FileEntry(it.str("path")!!, it.long("bytes"), it.str("sha256")!!) },
                done = o["done"]!!.jsonArray.map { it.jsonPrimitive.content }.toMutableSet(),
                complete = o["complete"]?.jsonPrimitive?.contentOrNull == "true",
                downloadedAtMs = o["downloaded_at_ms"]?.jsonPrimitive?.longOrNull,
            )
        }.getOrNull()
    }

    private fun writeState(s: Saved) {
        dir.mkdirs()
        val body = buildJsonObject {
            put("release", s.release)
            put("build", s.build)
            put("route_buckets", s.routeBuckets)
            put("total_bytes", s.totalBytes)
            put("files", buildJsonArray {
                s.files.forEach { f -> add(buildJsonObject { put("path", f.path); put("bytes", f.bytes); put("sha256", f.sha256) }) }
            })
            put("done", JsonArray(s.done.map(::JsonPrimitive)))
            put("complete", s.complete)
            s.downloadedAtMs?.let { put("downloaded_at_ms", it) }
        }
        val tmp = File(dir, "$STATE.tmp")
        tmp.writeText(body.toString())
        if (!tmp.renameTo(File(dir, STATE))) throw IOException("Could not save the offline catalogue state.")
    }

    // ---------------------------------------------------------------------------------- read

    private fun requireReady(): Saved = saved?.takeIf { it.complete }
        ?: throw IOException("The offline catalogue is not downloaded on this device.")

    private fun read(loc: Loc): ByteArray {
        RandomAccessFile(File(dir, packPath(loc.pack)), "r").use { raf ->
            val out = ByteArray(loc.length)
            raf.seek(loc.offset)
            raf.readFully(out)
            return out
        }
    }

    private fun rows(pages: List<Loc>): List<JsonObject> = pages.flatMap { loc ->
        gunzipText(read(loc)).lineSequence().filter { it.isNotBlank() }.map { json.parseToJsonElement(it).jsonObject }.toList()
    }

    private fun vehiclesFile(): VehiclesFile = vehicles ?: synchronized(this) {
        requireReady()
        val o = json.parseToJsonElement(gunzipText(File(dir, "vehicles.json.gz").readBytes())).jsonObject
        VehiclesFile(
            vehicles = o["vehicles"]!!.jsonArray.map { it.jsonObject }.map { v ->
                VehicleMasterEntry(
                    id = v.str("id")!!,
                    modelFamily = v.str("model_family").orEmpty(),
                    chassisCode = v.str("chassis_code").orEmpty(),
                    engineCode = v.str("engine_code"),
                    yearStart = v["year_start"]?.jsonPrimitive?.intOrNull,
                    yearEnd = v["year_end"]?.jsonPrimitive?.intOrNull,
                    salesRegion = v.str("sales_region"),
                )
            },
            shards = o["shards"]!!.jsonObject.mapValues { (_, pages) -> pages.jsonArray.map { it.toLoc() } },
        ).also { vehicles = it }
    }

    private fun vehicleRows(vehicleId: String): List<JsonObject> = synchronized(rowsCache) {
        rowsCache[vehicleId] ?: rows(vehiclesFile().shards[vehicleId] ?: throw IOException("This vehicle is not in the offline catalogue."))
            .also { rowsCache[vehicleId] = it }
    }

    private fun route(diagramId: String): JsonObject? {
        val path = "routes-%02d.json.gz".format(Integer.remainderUnsigned(fnv1a32(diagramId), requireReady().routeBuckets))
        val table = synchronized(routesCache) {
            routesCache[path] ?: json.parseToJsonElement(gunzipText(File(dir, path).readBytes())).jsonObject.also { routesCache[path] = it }
        }
        return table[diagramId] as? JsonObject
    }

    /** The published vehicle master (`list_customer_vehicle_master`). */
    fun vehicleMaster(): List<VehicleMasterEntry> = vehiclesFile().vehicles

    /** `staff-sections`: the vehicle's catalogue sections, as the live gateway answers. */
    fun sections(vehicleId: String): JsonObject {
        val map = LinkedHashMap<String, Pair<String, MutableMap<String, Int>>>()
        val diagrams = HashMap<String, MutableSet<String>>()
        for (row in vehicleRows(vehicleId)) {
            val id = row.str("section_id") ?: continue
            val entry = map.getOrPut(id) { (row.str("section_slug") ?: "") to HashMap() }
            val label = row.str("subcategory_name") ?: row.str("section_slug") ?: id
            entry.second[label] = (entry.second[label] ?: 0) + 1
            row.str("diagram_id")?.let { diagrams.getOrPut(id) { HashSet() } += it }
        }
        val sections = map.map { (id, v) ->
            val name = topLabel(v.second, v.first.ifEmpty { id })
            Triple(id, v.first.ifEmpty { id }, name)
        }.sortedBy { it.third }
        return buildJsonObject {
            put("sections", buildJsonArray {
                sections.forEach { (id, slug, name) ->
                    add(buildJsonObject {
                        put("section_id", id)
                        put("section_slug", slug)
                        put("display_name", name)
                        put("diagram_count", diagrams[id]?.size ?: 0)
                    })
                }
            })
        }
    }

    /** `staff-diagrams` for one section of the vehicle, paged like the live gateway. */
    fun diagrams(vehicleId: String, sectionId: String, limit: Int, offset: Int): JsonObject {
        val titles = LinkedHashMap<String, MutableMap<String, Int>>()
        for (row in vehicleRows(vehicleId)) {
            if (row.str("section_id") != sectionId) continue
            val id = row.str("diagram_id") ?: continue
            val title = row.str("category_name") ?: row.str("subcategory_name") ?: id
            val counts = titles.getOrPut(id) { HashMap() }
            counts[title] = (counts[title] ?: 0) + 1
        }
        val all = titles.map { (id, t) -> id to topLabel(t, id) }.sortedBy { it.second }
        return buildJsonObject {
            put("diagrams", buildJsonArray {
                all.drop(offset).take(limit).forEach { (id, title) ->
                    add(buildJsonObject {
                        put("diagram_id", id)
                        put("title", title)
                        put("name_en", title)
                        put("image_ready", route(id)?.get("i") != null)
                    })
                }
            })
            put("total", all.size)
        }
    }

    /** `staff-diagram-parts`: the diagram's part rows with their callout boxes. */
    fun diagramParts(diagramId: String): JsonObject {
        val r = route(diagramId) ?: throw IOException("This diagram is not in the offline catalogue.")
        var parts = rows(r["p"]?.jsonArray?.map { it.toLoc() }.orEmpty())
        if (r["f"] != null) parts = parts.filter { it.str("diagram_id") == diagramId }
        return buildJsonObject {
            put("row_count", parts.size)
            put("parts", JsonArray(parts))
        }
    }

    /** `diagram-image`: the image extracted to a local file, offered as a `file:` URL. */
    fun diagramImage(diagramId: String): JsonObject {
        val loc = route(diagramId)?.get("i")?.toLoc() ?: throw IOException("No diagram image in the offline catalogue for this diagram.")
        val images = File(dir, "images").apply { mkdirs() }
        val file = File(images, diagramId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".png")
        if (!file.exists() || file.length() != loc.length.toLong()) {
            val tmp = File(images, file.name + ".tmp")
            tmp.writeBytes(read(loc))
            tmp.renameTo(file)
        }
        return buildJsonObject {
            put("signed_url", file.toURI().toString())
            put("diagram_id", diagramId)
        }
    }

    /**
     * `customer-stock` / `customer-search`: the vehicle's catalogue rows (matching [query] when
     * given) joined to what the shop stocks ([stock], by normalised part number).
     */
    fun vehicleStock(vehicleId: String, query: String, stock: Map<String, OfflineStockLine>, limit: Int): JsonObject {
        val terms = query.lowercase().split(Regex("\\s+")).filter(String::isNotBlank)
        val seen = HashSet<String>()
        val results = buildJsonArray {
            var n = 0
            for (row in vehicleRows(vehicleId)) {
                if (n >= limit) break
                val key = normalizePart(row.str("normalized_oem_number") ?: row.str("display_oem_number"))
                if (key.isEmpty() || key in seen) continue
                val s = stock[key] ?: continue
                if (terms.isNotEmpty()) {
                    val hay = searchable(row)
                    if (!terms.all { hay.contains(it) }) continue
                }
                seen += key
                n++
                add(buildJsonObject {
                    put("stock_item_id", s.stockItemId)
                    put("name", s.description ?: row.str("name") ?: "Nissan part")
                    put("category", row.str("category_name"))
                    put("subcategory", row.str("subcategory_name"))
                    put("stock", buildJsonObject {
                        put("state", if (s.saleableQty <= 0.0) "backorder" else "in_stock")
                        put("qty", s.saleableQty)
                    })
                    put("price", buildJsonObject { put("amount", s.unitPrice); put("currency", s.currency) })
                    put("fitment_status", "VERIFIED_FIT")
                    put("vehicle_id", vehicleId)
                    put("internal_catalog_ref", s.oemPartNumber)
                })
            }
        }
        return buildJsonObject {
            put("source", "offline_bundle")
            put("vehicle_id", vehicleId)
            put("results", results)
        }
    }

    companion object {
        const val FORMAT = "gtr-pos-offline/1"
        private const val STATE = "state.json"

        fun packPath(index: Int) = "pack-%04d.bin".format(index)

        /** 32-bit FNV-1a over UTF-8, as the builder uses to place a diagram's route. */
        fun fnv1a32(text: String): Int {
            var h = 0x811C9DC5.toInt()
            for (b in text.toByteArray(Charsets.UTF_8)) {
                h = h xor (b.toInt() and 0xFF)
                h *= 0x01000193
            }
            return h
        }

        fun normalizePart(value: String?): String = value.orEmpty().uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }

        private fun topLabel(labels: Map<String, Int>, fallback: String): String =
            labels.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).firstOrNull()?.key ?: fallback

        private fun searchable(row: JsonObject): String = buildList {
            listOf("search_text", "name", "description", "category_name", "subcategory_name", "pnc_code", "display_oem_number")
                .forEach { k -> row.str(k)?.let(::add) }
            (row["aliases"] as? JsonArray)?.forEach { (it as? JsonPrimitive)?.contentOrNull?.let(::add) }
        }.joinToString(" ").lowercase()

        private fun gunzipText(bytes: ByteArray): String =
            if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
            } else {
                bytes.toString(Charsets.UTF_8)
            }

        private fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    md.update(buffer, 0, n)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }

        private fun JsonElement.toLoc(): Loc = jsonArray.let { Loc(it[0].jsonPrimitive.intOrNull!!, it[1].jsonPrimitive.longOrNull!!, it[2].jsonPrimitive.intOrNull!!) }
        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        private fun JsonObject.long(key: String): Long = this[key]?.jsonPrimitive?.longOrNull ?: 0L
        private fun JsonObject.files(): List<JsonObject> = this["files"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        private fun JsonObject.urlFor(path: String): String? = files().firstOrNull { it.str("path") == path }?.str("url")
    }
}

/** A stocked, priced part the till can sell (from the tablet's offline stock snapshot). */
data class OfflineStockLine(
    val stockItemId: String,
    val oemPartNumber: String,
    val description: String?,
    val unitPrice: Double,
    val saleableQty: Double,
    val currency: String,
)

class HttpStatusException(val status: Int, message: String) : IOException(message)

/** HTTP for the bundle files; [fromByte] > 0 asks for the rest of a partly downloaded file. */
fun interface BundleHttp {
    fun open(url: String, fromByte: Long): Response

    class Response(val status: Int, val body: InputStream)
}

object UrlConnectionBundleHttp : BundleHttp {
    override fun open(url: String, fromByte: Long): BundleHttp.Response {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        if (fromByte > 0) c.setRequestProperty("Range", "bytes=$fromByte-")
        val status = c.responseCode
        val body = if (status in 200..299) c.inputStream else (c.errorStream ?: java.io.ByteArrayInputStream(ByteArray(0)))
        return BundleHttp.Response(status, body)
    }
}
