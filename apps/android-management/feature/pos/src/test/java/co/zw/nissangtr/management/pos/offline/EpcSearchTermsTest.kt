package co.zw.nissangtr.management.pos.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpcSearchTermsTest {
    @Test fun tokenizerIndexesOemPncDescriptionAndCategoryWords() {
        val terms = epcSearchTerms(
            "15208-65F0E",
            "11010A",
            "Engine oil filter",
            "Lubrication / Filters",
        )
        assertTrue("15208" in terms)
        assertTrue("65f0e" in terms)
        assertTrue("11010a" in terms)
        assertTrue("engine" in terms)
        assertTrue("filter" in terms)
        assertTrue("lubrication" in terms)
        assertFalse("/" in terms)
    }

    @Test fun tokenizerIsBoundedDistinctAndIgnoresSingleCharacters() {
        val terms = epcSearchTerms("a A aa AA bb bb")
        assertEquals(listOf("aa", "bb"), terms)
    }
}
