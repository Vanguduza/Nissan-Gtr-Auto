package co.zw.nissangtr.pos.till

import co.zw.nissangtr.pos.api.FakePosClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TillSessionRetainHelpersTest {
    @Test
    fun ensureOpenCartNoopsWhenCartExists() = runBlocking {
        val client = FakePosClient()
        client.createCart("wh2-fake")
        val session = TillSession(client, TillLayoutMode.Compact)
        assertNull(session.ensureOpenCart())
        assertTrue(session.state.fake.session.cartId != null)
    }

    @Test
    fun defaultCategoryIsNullNotBrakes() {
        val session = TillSession(FakePosClient(), TillLayoutMode.Compact)
        assertNull(session.state.selectedCategory)
    }

    @Test
    fun categoryFacetsFromTiles() {
        val session = TillSession(FakePosClient(), TillLayoutMode.Compact)
        assertTrue(session.state.categoryFacets.isNotEmpty())
        assertTrue(session.state.categoryFacets.contains("Brakes"))
    }

    @Test
    fun setBannerSurvivesRefreshPattern() {
        val session = TillSession(FakePosClient(), TillLayoutMode.Compact)
        session.setBanner("Shop stock failed")
        assertEquals("Shop stock failed", session.state.banner)
    }
}
