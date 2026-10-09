package co.zw.nissangtr.bridges.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectionsRouteFetcherParseTest {

    @Test
    fun parse_okRoute() {
        val json = """
            {
              "code": "Ok",
              "routes": [{
                "distance": 1000.4,
                "duration": 120.8,
                "geometry": {
                  "type": "LineString",
                  "coordinates": [[31.05,-17.83],[31.06,-17.84],[31.07,-17.85]]
                }
              }]
            }
        """.trimIndent()
        val result = DirectionsRouteFetcher.parseDirectionsJson(json)
        assertTrue(result is RouteFetchResult.Ok)
        val route = (result as RouteFetchResult.Ok).route
        assertEquals(3, route.points.size)
        assertEquals(1000, route.distanceMeters)
        assertEquals(120, route.durationSeconds)
        assertEquals("OSRM", route.summary)
    }

    @Test
    fun parse_noRoute() {
        val json = """{"code":"NoRoute","message":"No route found"}"""
        val result = DirectionsRouteFetcher.parseDirectionsJson(json)
        assertTrue(result is RouteFetchResult.Failed)
        assertTrue((result as RouteFetchResult.Failed).message.contains("No route found"))
    }
}
