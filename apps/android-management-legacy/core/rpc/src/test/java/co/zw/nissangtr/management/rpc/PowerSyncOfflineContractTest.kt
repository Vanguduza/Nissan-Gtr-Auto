package co.zw.nissangtr.management.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSyncOfflineContractTest {
    @Test
    fun sync_eligible_includes_pos_and_excludes_journal() {
        assertTrue(PowerSyncOfflineContract.isSyncEligible("pos_carts"))
        assertTrue(PowerSyncOfflineContract.isSyncEligible("delivery_notes"))
        assertTrue(PowerSyncOfflineContract.isSyncEligible("stock_reconciliations"))
        assertFalse(PowerSyncOfflineContract.isSyncEligible("journal_entries"))
        assertTrue(
            "journal_entries" in PowerSyncOfflineContract.FORBIDDEN_UPLOAD_TABLES,
        )
    }

    @Test
    fun assertNotForbiddenUpload_allows_pos() {
        PowerSyncOfflineContract.assertNotForbiddenUpload("pos_cart_lines")
    }

    @Test(expected = IllegalArgumentException::class)
    fun assertNotForbiddenUpload_rejects_journal() {
        PowerSyncOfflineContract.assertNotForbiddenUpload("journal_entries")
    }

    @Test
    fun rpc_intents_include_offline_pos_replay() {
        assertTrue(
            "replay_offline_pos_sale" in PowerSyncOfflineContract.RPC_INTENT_NAMES,
        )
        assertTrue(
            "checkout_pos_cart" in PowerSyncOfflineContract.RPC_INTENT_NAMES,
        )
    }

    @Test
    fun powerSyncClientFor_uses_fake_when_url_blank() {
        val c = powerSyncClientFor(PowerSyncEndpointConfig(url = null))
        assertEquals(PowerSyncMode.FAKE, c.mode)
        assertFalse(c.isLiveConfigured())
        assertFalse(c.isDatabaseOpen())
    }

    @Test
    fun powerSyncClientFor_live_when_url_set_but_does_not_open_without_context() {
        val c =
            powerSyncClientFor(
                PowerSyncEndpointConfig(url = "https://powersync.example"),
            )
        assertEquals(PowerSyncMode.LIVE, c.mode)
        assertTrue(c.isLiveConfigured())
        assertTrue(c is LivePowerSyncClient)
        // Unit tests skip live openDatabase (needs Android Context + real secrets).
        assertFalse(c.isDatabaseOpen())
    }

    @Test
    fun schema_tables_are_sync_eligible_and_exclude_journal() {
        assertTrue(GtrPowerSyncSchema.TABLE_NAMES.isNotEmpty())
        for (name in GtrPowerSyncSchema.TABLE_NAMES) {
            assertTrue(
                "schema table $name should be sync-eligible",
                PowerSyncOfflineContract.isSyncEligible(name),
            )
            assertFalse(
                "schema must not include $name",
                name in PowerSyncOfflineContract.FORBIDDEN_UPLOAD_TABLES,
            )
        }
        assertTrue("pos_carts" in GtrPowerSyncSchema.TABLE_NAMES)
        assertTrue("delivery_notes" in GtrPowerSyncSchema.TABLE_NAMES)
        assertTrue("stock_reconciliations" in GtrPowerSyncSchema.TABLE_NAMES)
    }

    @Test
    fun connector_upload_guard_rejects_journal_allows_pos() {
        assertFalse(
            GtrPowerSyncConnector.wouldAllowUpload(listOf("journal_entries")),
        )
        assertFalse(
            GtrPowerSyncConnector.wouldAllowUpload(
                listOf("pos_carts", "journal_entry_lines"),
            ),
        )
        assertTrue(
            GtrPowerSyncConnector.wouldAllowUpload(listOf("pos_carts", "delivery_notes")),
        )
    }

    @Test
    fun endpoint_fromProperties_trims_blank_to_unset() {
        val cfg =
            PowerSyncEndpointConfig.fromProperties(
                url = "  ",
                publicKey = "pk",
            )
        assertFalse(cfg.isLiveReady())
        assertEquals(null, cfg.url)
    }
}
