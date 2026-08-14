package co.zw.nissangtr.bridges.maps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OsrmRouteFetcherParseTest {

    @Test
    fun parse_okRoute() {
        val json =
            """
            {
              "code": "Ok",
              "routes": [{
                "distance": 1000.4,
                "duration": 120.6,
                "geometry": {
                  "type": "LineString",
                  "coordinates": [[31.0, -17.8], [31.1, -17.9], [31.2, -18.0]]
                }
              }]
            }
            """.trimIndent()
        val result = OsrmRouteFetcher.parseOsrmJson(json)
        assertTrue(result is RouteFetchResult.Ok)
        val route = (result as RouteFetchResult.Ok).route
        assertEquals(3, route.points.size)
        assertEquals(-17.8, route.points[0].latitude, 0.0001)
        assertEquals(31.0, route.points[0].longitude, 0.0001)
        assertEquals(1000, route.distanceMeters)
        assertEquals(121, route.durationSeconds)
        assertEquals("OSRM", route.summary)
    }

    @Test
    fun parse_notOk() {
        val result = OsrmRouteFetcher.parseOsrmJson("""{"code":"NoRoute"}""")
        assertTrue(result is RouteFetchResult.Failed)
    }
}
