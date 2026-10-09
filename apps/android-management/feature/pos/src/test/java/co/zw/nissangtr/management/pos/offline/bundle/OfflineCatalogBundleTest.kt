package co.zw.nissangtr.management.pos.offline.bundle

import co.zw.nissangtr.management.rpc.FakeRpcClient
import co.zw.nissangtr.management.rpc.RpcClient
import co.zw.nissangtr.management.rpc.VehicleMasterEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.net.URI

/**
 * The tablet reader against the sample bundle written by the builder's own test
 * (`data-pipeline/tests/test_pos_offline_bundle.py`, `POS_OFFLINE_FIXTURE_OUT`), so the builder and
 * the tablet agree on the format.
 */
class OfflineCatalogBundleTest {
    @get:Rule val tmp = TemporaryFolder()

    private val fixture = File(javaClass.getResource("/pos-offline-bundle/manifest.json")!!.toURI()).parentFile
    private val manifest: JsonObject = Json.parseToJsonElement(File(fixture, "manifest.json").readText()).jsonObject

    /** The signed manifest as the gateway returns it: every file with a download link. */
    private fun signed(): JsonObject = JsonObject(
        manifest + ("files" to JsonArray(manifest["files"]!!.jsonArray.map { f ->
            val o = f.jsonObject
            JsonObject(o + ("url" to JsonPrimitive("fixture://" + o["path"]!!.jsonPrimitive.content)))
        })),
    )

    private class FakeHttp(private val dir: File) : BundleHttp {
        val requests = mutableListOf<Pair<String, Long>>()
        /** Serves only this many bytes of the named file once, then drops the connection. */
        var cutOnce: Pair<String, Int>? = null
        var corruptOnce: String? = null

        override fun open(url: String, fromByte: Long): BundleHttp.Response {
            val name = url.removePrefix("fixture://")
            requests += name to fromByte
            var bytes = File(dir, name).readBytes()
            if (corruptOnce == name) {
                corruptOnce = null
                bytes = bytes.copyOf().also { it[0] = (it[0] + 1).toByte() }
            }
            val cut = cutOnce
            if (cut != null && cut.first == name) {
                cutOnce = null
                val partial = bytes.copyOfRange(0, cut.second)
                return BundleHttp.Response(200, object : java.io.InputStream() {
                    var i = 0
                    override fun read(): Int = if (i < partial.size) partial[i++].toInt() and 0xFF else throw IOException("connection reset")
                })
            }
            return if (fromByte > 0) {
                BundleHttp.Response(206, bytes.copyOfRange(fromByte.toInt(), bytes.size).inputStream())
            } else {
                BundleHttp.Response(200, bytes.inputStream())
            }
        }
    }

    private fun downloaded(http: FakeHttp = FakeHttp(fixture)): OfflineCatalogBundle = runBlocking {
        OfflineCatalogBundle(tmp.newFolder("bundle"), http).also {
            it.init()
            it.download { signed() }
        }
    }

    @Test
    fun fnvMatchesTheBuilder() {
        assertEquals(0x811C9DC5.toInt(), OfflineCatalogBundle.fnv1a32(""))
        assertEquals(0xE40C292C.toInt(), OfflineCatalogBundle.fnv1a32("a"))
        assertEquals(0xBF9CF968.toInt(), OfflineCatalogBundle.fnv1a32("foobar"))
    }

    @Test
    fun interruptedFileResumesFromWhereItStopped() = runBlocking {
        val http = FakeHttp(fixture).apply { cutOnce = "pack-0001.bin" to 40 }
        val bundle = OfflineCatalogBundle(tmp.newFolder("bundle"), http)
        bundle.init()
        try {
            bundle.download { signed() }
            fail("expected the dropped connection to stop the download")
        } catch (_: IOException) {
        }
        assertEquals(OfflineCatalogBundle.Phase.Paused, bundle.status.value.phase)
        assertFalse(bundle.ready)
        http.requests.clear()
        bundle.download { signed() }
        assertTrue(bundle.ready)
        assertEquals("resumed with a range request", "pack-0001.bin" to 40L, http.requests.first())
        assertFalse("finished files are not fetched again", http.requests.any { it.first == "pack-0000.bin" })
        // A fresh instance reads the saved state.
        val reopened = OfflineCatalogBundle(File(tmp.root, "bundle"), http)
        reopened.init()
        assertTrue(reopened.ready)
        assertEquals(OfflineCatalogBundle.Phase.Ready, reopened.status.value.phase)
    }

    @Test
    fun damagedFileIsRejected() = runBlocking {
        val bundle = OfflineCatalogBundle(tmp.newFolder("bundle"), FakeHttp(fixture).apply { corruptOnce = "vehicles.json.gz" })
        bundle.init()
        try {
            bundle.download { signed() }
            fail("expected a checksum failure")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("damaged"))
        }
        assertFalse(bundle.ready)
        bundle.download { signed() }
        assertTrue(bundle.ready)
    }

    @Test
    fun answersTheCatalogueQuestions() {
        val bundle = downloaded()
        assertEquals(listOf("VM-gtr-1", "VM-nav-1"), bundle.vehicleMaster().map { it.id })
        assertEquals("gt-r", bundle.vehicleMaster().first().familySlug)

        val sections = bundle.sections("VM-gtr-1")["sections"]!!.jsonArray.map { it.jsonObject["section_id"]!!.jsonPrimitive.content }
        assertEquals(setOf("SEC-BRAKE", "SEC-ENGINE"), sections.toSet())

        val diagrams = bundle.diagrams("VM-gtr-1", "SEC-BRAKE", 200, 0)["diagrams"]!!.jsonArray.map { it.jsonObject }
        assertEquals(setOf("DG-pads", "DG-rotor"), diagrams.map { it["diagram_id"]!!.jsonPrimitive.content }.toSet())
        assertTrue(diagrams.all { it["image_ready"]!!.jsonPrimitive.content == "true" })

        val pads = bundle.diagramParts("DG-pads")["parts"]!!.jsonArray.map { it.jsonObject }
        assertEquals("41060-JF00A", pads.single()["display_oem_number"]!!.jsonPrimitive.content)
        assertNotNull("callout box travels with the row", pads.single()["bbox_x"])
        val rotor = bundle.diagramParts("DG-rotor")["parts"]!!.jsonArray
        assertEquals("shared section shard is filtered to this diagram", 1, rotor.size)

        val image = File(URI(bundle.diagramImage("DG-rotor")["signed_url"]!!.jsonPrimitive.content))
        assertTrue(image.readBytes().endsWith("rotor".toByteArray()))
        try {
            bundle.diagramImage("DG-oil")
            fail("no image published for this diagram")
        } catch (_: IOException) {
        }

        val stock = mapOf(
            "41060JF00A" to OfflineStockLine("si-pads", "41060-JF00A", "GT-R front pads", 89.0, 4.0, "USD"),
            "1520865F0E" to OfflineStockLine("si-oil", "15208-65F0E", "Oil filter", 12.0, 0.0, "USD"),
        )
        val all = bundle.vehicleStock("VM-gtr-1", "", stock, 100)["results"]!!.jsonArray.map { it.jsonObject }
        assertEquals(setOf("si-pads", "si-oil"), all.map { it["stock_item_id"]!!.jsonPrimitive.content }.toSet())
        val padsHit = bundle.vehicleStock("VM-gtr-1", "brake pad", stock, 100)["results"]!!.jsonArray.single().jsonObject
        assertEquals("si-pads", padsHit["stock_item_id"]!!.jsonPrimitive.content)
        assertEquals("89.0", padsHit["price"]!!.jsonObject["amount"]!!.jsonPrimitive.content)
        assertEquals("not stocked for this vehicle", 0, bundle.vehicleStock("VM-nav-1", "pad", stock, 100)["results"]!!.jsonArray.size)
    }

    @Test
    fun rpcClientUsesTheBundleOfflineAndWhenTheServerFails() = runBlocking {
        val bundle = downloaded()
        var online = false
        val server = object : RpcClient by FakeRpcClient() {
            var calls = 0
            override suspend fun listVehicleMaster(): List<VehicleMasterEntry> {
                calls++
                throw IOException("no route to host")
            }
            override suspend fun catalogLive(action: String, params: Map<String, String>): JsonObject {
                calls++
                throw IOException("no route to host")
            }
        }
        val rpc = OfflineCatalogRpcClient(server, bundle, { online }, { emptyMap() })
        assertEquals("VM-gtr-1", rpc.resolveVehicleMasterId("r35", "vr38dett"))
        assertEquals(2, rpc.catalogLive("staff-sections", mapOf("variant_id" to "VM-gtr-1"))["sections"]!!.jsonArray.size)
        assertEquals("offline: the server is not asked", 0, server.calls)

        online = true
        val parts = rpc.catalogLive("staff-diagram-parts", mapOf("diagram_id" to "DG-pads"))
        assertEquals(1, parts["parts"]!!.jsonArray.size)
        assertEquals("online: asked first, then the bundle", 1, server.calls)

        // Not a catalogue call: always the server's.
        try {
            rpc.catalogLive("health")
            fail("health is never answered from the bundle")
        } catch (_: IOException) {
        }
    }

    @Test
    fun notDownloadedMeansTheServerErrorStands() = runBlocking {
        val bundle = OfflineCatalogBundle(tmp.newFolder("empty"), FakeHttp(fixture)).apply { init() }
        val server = object : RpcClient by FakeRpcClient() {
            override suspend fun catalogLive(action: String, params: Map<String, String>): JsonObject = throw IOException("offline")
        }
        val rpc = OfflineCatalogRpcClient(server, bundle, { false }, { emptyMap() })
        try {
            rpc.catalogLive("staff-sections", mapOf("variant_id" to "VM-gtr-1"))
            fail("no bundle: the original error is reported")
        } catch (e: IOException) {
            assertEquals("offline", e.message)
        }
        assertNull(bundle.status.value.release)
    }

    @Test
    fun removeDeletesEverything() = runBlocking {
        val bundle = downloaded()
        bundle.remove()
        assertFalse(bundle.ready)
        assertEquals(OfflineCatalogBundle.Phase.None, bundle.status.value.phase)
        assertTrue(File(tmp.root, "bundle").listFiles().isNullOrEmpty())
    }

    /** A second build: the same files plus one new one, served from its own folder. */
    private fun secondBuild(): Pair<File, JsonObject> {
        val dir2 = tmp.newFolder("fixture-2")
        fixture.listFiles()!!.forEach { it.copyTo(File(dir2, it.name)) }
        val extra = "new in build two".toByteArray()
        File(dir2, "extra.txt").writeBytes(extra)
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(extra).joinToString("") { "%02x".format(it) }
        val files = signed()["files"]!!.jsonArray + JsonObject(
            mapOf("path" to JsonPrimitive("extra.txt"), "bytes" to JsonPrimitive(extra.size), "sha256" to JsonPrimitive(sha), "url" to JsonPrimitive("fixture://extra.txt")),
        )
        return dir2 to JsonObject(signed() + mapOf("build" to JsonPrimitive("build-two"), "release" to JsonPrimitive("release-two"), "files" to JsonArray(files)))
    }

    @Test
    fun updateIsStagedWhileTheCurrentBuildStaysInUse() = runBlocking {
        val first = downloaded()
        val (dir2, manifest2) = secondBuild()
        val http2 = FakeHttp(dir2).apply { cutOnce = "extra.txt" to 4 }
        val bundle = OfflineCatalogBundle(File(tmp.root, "bundle"), http2).apply { init() }
        assertTrue(first.ready)
        try {
            bundle.download { manifest2 }
            fail("expected the dropped connection to stop the update")
        } catch (_: IOException) {
        }
        val paused = bundle.status.value
        assertEquals(OfflineCatalogBundle.Phase.Paused, paused.phase)
        assertEquals("release-two", paused.release)
        assertEquals("the old build is still in use", "test-release", paused.activeRelease)
        assertTrue(bundle.ready)
        assertEquals(listOf("VM-gtr-1", "VM-nav-1"), bundle.vehicleMaster().map { it.id })
        assertEquals("unchanged files are linked, not downloaded", listOf("extra.txt" to 0L), http2.requests)

        http2.requests.clear()
        bundle.download { manifest2 }
        val ready = bundle.status.value
        assertEquals(OfflineCatalogBundle.Phase.Ready, ready.phase)
        assertEquals("release-two", ready.activeRelease)
        assertEquals("test-release", ready.previousRelease)
        assertEquals(listOf("extra.txt" to 4L), http2.requests)
        assertTrue("verified files are read-only", File(tmp.root, "bundle/builds/build-two/vehicles.json.gz").toPath().let {
            java.nio.file.Files.exists(it) && java.nio.file.attribute.PosixFilePermission.OWNER_WRITE !in java.nio.file.Files.getPosixFilePermissions(it)
        })
        assertEquals(listOf("VM-gtr-1", "VM-nav-1"), bundle.vehicleMaster().map { it.id })

        // Back to the previous build, and the switch survives a restart.
        bundle.rollback()
        assertEquals("test-release", bundle.status.value.activeRelease)
        assertEquals("release-two", bundle.status.value.previousRelease)
        val reopened = OfflineCatalogBundle(File(tmp.root, "bundle"), http2).apply { init() }
        assertEquals("test-release", reopened.status.value.activeRelease)
        assertEquals("release-two", reopened.status.value.previousRelease)
        assertTrue(reopened.ready)
    }

    @Test
    fun damagedActiveBuildFallsBackToThePreviousAtStartup() = runBlocking {
        downloaded()
        val (dir2, manifest2) = secondBuild()
        OfflineCatalogBundle(File(tmp.root, "bundle"), FakeHttp(dir2)).apply { init(); download { manifest2 } }
        // Only build two has this file (the others are shared with build one through hard links).
        val damaged = File(tmp.root, "bundle/builds/build-two/extra.txt")
        damaged.setWritable(true)
        damaged.writeBytes(ByteArray(3))
        val reopened = OfflineCatalogBundle(File(tmp.root, "bundle"), FakeHttp(dir2)).apply { init() }
        val st = reopened.status.value
        assertTrue(reopened.ready)
        assertEquals("test-release", st.activeRelease)
        assertNull(st.previousRelease)
        assertTrue(st.message!!.contains("damaged"))
        assertEquals(listOf("VM-gtr-1", "VM-nav-1"), reopened.vehicleMaster().map { it.id })
    }

    @Test
    fun rollbackWithoutAPreviousBuildIsRefused() = runBlocking {
        val bundle = downloaded()
        try {
            bundle.rollback()
            fail("nothing to go back to")
        } catch (_: IOException) {
        }
        assertTrue(bundle.ready)
    }

    @Test
    fun aBuildDownloadedBeforeStagingIsKept() = runBlocking {
        // Old layout: the files and a complete state.json straight in the folder.
        val dir = tmp.newFolder("bundle")
        fixture.listFiles()!!.filter { it.name != "manifest.json" }.forEach { it.copyTo(File(dir, it.name)) }
        val files = manifest["files"]!!.jsonArray
        val state = JsonObject(
            mapOf(
                "release" to JsonPrimitive("test-release"),
                "build" to manifest["build"]!!,
                "route_buckets" to manifest["route_buckets"]!!,
                "total_bytes" to manifest["total_bytes"]!!,
                "files" to files,
                "done" to JsonArray(files.map { it.jsonObject["path"]!! }),
                "complete" to JsonPrimitive(true),
            ),
        )
        File(dir, "state.json").writeText(state.toString())
        val bundle = OfflineCatalogBundle(dir, FakeHttp(fixture)).apply { init() }
        assertTrue(bundle.ready)
        assertEquals("test-release", bundle.status.value.activeRelease)
        assertEquals(listOf("VM-gtr-1", "VM-nav-1"), bundle.vehicleMaster().map { it.id })
        assertTrue(File(dir, "catalog.json").exists())
    }

    private fun ByteArray.endsWith(suffix: ByteArray) = size >= suffix.size && copyOfRange(size - suffix.size, size).contentEquals(suffix)
}
