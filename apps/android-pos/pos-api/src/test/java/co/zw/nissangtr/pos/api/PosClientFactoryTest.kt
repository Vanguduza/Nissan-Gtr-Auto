package co.zw.nissangtr.pos.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PosClientFactoryTest {
    @Test
    fun isLive_requiresUrlKeyAndNotForcedFake() {
        assertFalse(PosClientFactory.isLive("", "anon"))
        assertFalse(PosClientFactory.isLive("https://example.supabase.co", ""))
        assertFalse(
            PosClientFactory.isLive(
                "https://example.supabase.co",
                "anon",
                forceFake = true,
            ),
        )
        assertTrue(
            PosClientFactory.isLive(
                "https://gylrgwqyuiwkyykardwc.supabase.co",
                "anon-key",
            ),
        )
    }

    @Test
    fun create_returnsFakeWhenMissingCredsOrForced() {
        assertTrue(PosClientFactory.create("", "") is FakePosClient)
        assertTrue(
            PosClientFactory.create(
                "https://gylrgwqyuiwkyykardwc.supabase.co",
                "anon-key",
                forceFake = true,
            ) is FakePosClient,
        )
    }
}
