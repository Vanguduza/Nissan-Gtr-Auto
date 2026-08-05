package co.zw.nissangtr.customer.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressGeoTest {
    @Test
    fun embedAndParseRoundTrip() {
        val embedded = AddressGeo.embed("Flat 2", -17.8292, 31.0522)
        assertEquals("Flat 2\n#gtr_geo:-17.8292,31.0522", embedded)
        assertEquals(-17.8292 to 31.0522, AddressGeo.parse(embedded))
        assertEquals("Flat 2", AddressGeo.strip(embedded))
    }

    @Test
    fun parseMissingReturnsNull() {
        assertNull(AddressGeo.parse("no geo here"))
        assertNull(AddressGeo.parse(null))
    }
}
