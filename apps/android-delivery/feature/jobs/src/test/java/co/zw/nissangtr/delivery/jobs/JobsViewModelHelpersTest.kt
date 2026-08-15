package co.zw.nissangtr.delivery.jobs

import co.zw.nissangtr.bridges.maps.MapLatLng
import co.zw.nissangtr.delivery.rpc.DeliveryFailureReason
import co.zw.nissangtr.delivery.rpc.DeliveryJobSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JobsViewModelHelpersTest {

    @Test
    fun failureReasonsMatchRpcEnum() {
        val values = DeliveryFailureReason.entries.map { it.rpcValue }
        assertTrue(values.contains("customer_absent"))
        assertTrue(values.contains("refused"))
        assertTrue(values.contains("wrong_address"))
        assertTrue(values.contains("damaged"))
        assertTrue(values.contains("other"))
        assertEquals(5, values.size)
    }

    @Test
    fun resolveSelectedJobReturnsJobForActiveDoneAndFailed() {
        val active = sampleJob("a1", "dispatched")
        val done = sampleJob("d1", "completed")
        val failed = sampleJob("f1", "failed")
        val jobs = listOf(active, done, failed)

        assertEquals(active, resolveSelectedJob(JobsUiState(jobs = jobs, selectedJobId = "a1")))
        assertEquals(done, resolveSelectedJob(JobsUiState(jobs = jobs, selectedJobId = "d1")))
        assertEquals(failed, resolveSelectedJob(JobsUiState(jobs = jobs, selectedJobId = "f1")))
    }

    @Test
    fun resolveSelectedJobNullWhenUnsetOrMissing() {
        val job = sampleJob("a1", "dispatched")
        assertNull(resolveSelectedJob(JobsUiState(jobs = listOf(job), selectedJobId = null)))
        assertNull(resolveSelectedJob(JobsUiState(jobs = listOf(job), selectedJobId = "missing")))
        assertNull(resolveSelectedJob(JobsUiState(jobs = emptyList(), selectedJobId = "a1")))
    }

    @Test
    fun selectJobIdIsPreservedOnJobsUiStateCopy() {
        val before = JobsUiState(selectedJobId = null)
        val after = before.copy(selectedJobId = "job-42")
        assertNull(before.selectedJobId)
        assertEquals("job-42", after.selectedJobId)
        assertNotNull(resolveSelectedJob(after.copy(jobs = listOf(sampleJob("job-42", "pending")))))
    }

    @Test
    fun formatRouteGuidanceLabelPrefersOsrmEtaSource() {
        val label = formatRouteGuidanceLabel(
            etaSource = RouteEtaSource.OSRM,
            summary = "OSRM",
            distanceMeters = 1500,
            durationSeconds = 300,
        )
        assertTrue(label.startsWith("Driving"))
        assertTrue(label.contains("1.5 km"))
        assertTrue(label.contains("5 min"))
        assertFalse(label.contains("unconfigured"))
        assertFalse(label.contains("OSRM_URL"))
    }

    @Test
    fun formatRouteGuidanceLabelMarksGoogleDeprecated() {
        val label = formatRouteGuidanceLabel(
            etaSource = RouteEtaSource.GOOGLE_DIRECTIONS_DEPRECATED,
            summary = "I-80",
            distanceMeters = 800,
            durationSeconds = 120,
        )
        assertTrue(label.contains("Driving"))
        assertTrue(label.contains("I-80"))
        assertTrue(label.contains("800m"))
        assertFalse(label.contains("deprecated"))
    }

    @Test
    fun formatRouteGuidanceLabelStraightLineFallback() {
        val label = formatRouteGuidanceLabel(
            etaSource = RouteEtaSource.STRAIGHT_LINE,
            summary = null,
            distanceMeters = 2000,
            durationSeconds = 240,
        )
        assertTrue(label.startsWith("Approx."))
        assertTrue(label.contains("2.0 km"))
        assertFalse(label.contains("OSRM_URL"))
    }

    @Test
    fun straightLineRouteBuildsPolyline() {
        val route = straightLineRoute(
            MapLatLng(-17.83, 31.05),
            MapLatLng(-17.84, 31.06),
        )
        assertEquals(2, route.points.size)
        assertNotNull(route.distanceMeters)
        assertTrue(route.distanceMeters!! > 0)
        assertTrue(route.durationSeconds!! >= 60)
    }

    @Test
    fun jobDetailMapCaptionIsQuietWithoutRouteLabel() {
        val caption = jobDetailMapCaption(
            state = JobsUiState(
                mapLibreEnabled = true,
                osrmConfigured = false,
                mapsKeyPresent = false,
            ),
            showingMapLibre = true,
        )
        assertEquals("", caption)
        assertFalse(caption.contains("unconfigured"))
        assertFalse(caption.contains("OSRM_URL"))
        assertFalse(caption.contains("DEPRECATED"))
    }

    @Test
    fun jobDetailMapCaptionEchoesRouteLabelOnly() {
        val caption = jobDetailMapCaption(
            state = JobsUiState(
                mapLibreEnabled = true,
                osrmConfigured = true,
                routeLabel = "Driving · 1.2 km · 4 min",
                routeEtaSource = RouteEtaSource.OSRM,
            ),
            showingMapLibre = true,
        )
        assertEquals("Driving · 1.2 km · 4 min", caption)
        assertFalse(caption.contains("MapLibre SoR"))
    }

    private fun sampleJob(id: String, status: String) = DeliveryJobSummary(
        id = id,
        deliveryNoteId = "dn-$id",
        documentNumber = "DJ-$id",
        status = status,
        dropoffLat = -17.8,
        dropoffLng = 31.0,
        etaAt = null,
        etaSeconds = null,
        notes = null,
        routeSequence = 1,
        reattemptOf = null,
        failureReasonCode = null,
        podPhotoPath = null,
        podSignaturePath = null,
        assigneeUserId = null,
        settlement = null,
        dropoffAddressText = "12 Test St",
    )
}
