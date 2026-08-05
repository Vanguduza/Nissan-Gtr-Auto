package co.zw.nissangtr.bridges.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionsRouteFetcherParseTest {

    @Test
    fun parse_okRoute() {
        val encoded = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        val json = """
            {
              "status": "OK",
              "routes": [{
                "summary": "I-80",
                "overview_polyline": { "points": "$encoded" },
                "legs": [{
                  "distance": { "value": 1000 },
                  "duration": { "value": 120 }
                }]
              }]
            }
        """.trimIndent()
        val result = DirectionsRouteFetcher.parseDirectionsJson(json)
        assertTrue(result is RouteFetchResult.Ok)
        val route = (result as RouteFetchResult.Ok).route
        assertEquals(3, route.points.size)
        assertEquals(1000, route.distanceMeters)
        assertEquals(120, route.durationSeconds)
        assertEquals("I-80", route.summary)
    }

    @Test
    fun parse_requestDenied() {
        val json = """{"status":"REQUEST_DENIED","error_message":"bad key"}"""
        val result = DirectionsRouteFetcher.parseDirectionsJson(json)
        assertTrue(result is RouteFetchResult.Failed)
        assertTrue((result as RouteFetchResult.Failed).message.contains("bad key"))
    }
}
