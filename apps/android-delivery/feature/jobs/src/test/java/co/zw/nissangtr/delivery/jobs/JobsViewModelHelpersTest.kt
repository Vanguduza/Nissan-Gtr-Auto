package co.zw.nissangtr.delivery.jobs

import co.zw.nissangtr.delivery.rpc.DeliveryFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun formatRouteGuidanceLabelPrefersOsrmEtaSource() {
        val label = formatRouteGuidanceLabel(
            etaSource = RouteEtaSource.OSRM,
            summary = "OSRM",
            distanceMeters = 1500,
            durationSeconds = 300,
        )
        assertTrue(label.startsWith("eta_source=osrm"))
        assertTrue(label.contains("1.5 km"))
        assertTrue(label.contains("5 min"))
        assertFalse(label.contains("google_directions"))
    }

    @Test
    fun formatRouteGuidanceLabelMarksGoogleDeprecated() {
        val label = formatRouteGuidanceLabel(
            etaSource = RouteEtaSource.GOOGLE_DIRECTIONS_DEPRECATED,
            summary = "I-80",
            distanceMeters = 800,
            durationSeconds = 120,
        )
        assertTrue(label.contains("eta_source=google_directions (deprecated)"))
        assertTrue(label.contains("I-80"))
        assertTrue(label.contains("800m"))
    }

    @Test
    fun jobDetailMapCaptionMapLibreAndOsrm() {
        val caption = jobDetailMapCaption(
            JobsUiState(
                mapLibreEnabled = true,
                osrmConfigured = true,
                mapsKeyPresent = true,
                routeEtaSource = RouteEtaSource.OSRM,
            ),
        )
        assertTrue(caption.contains("MapLibre SoR"))
        assertTrue(caption.contains("OSRM distance/ETA preferred"))
        assertTrue(caption.contains("eta_source=osrm"))
        assertFalse(caption.contains("DEPRECATED Google"))
    }

    @Test
    fun jobDetailMapCaptionGoogleFallbackWhenFlagOff() {
        val caption = jobDetailMapCaption(
            JobsUiState(
                mapLibreEnabled = false,
                osrmConfigured = false,
                mapsKeyPresent = true,
            ),
        )
        assertTrue(caption.contains("DEPRECATED Google Maps fallback"))
        assertTrue(caption.contains("Google Directions (deprecated)"))
    }
}
