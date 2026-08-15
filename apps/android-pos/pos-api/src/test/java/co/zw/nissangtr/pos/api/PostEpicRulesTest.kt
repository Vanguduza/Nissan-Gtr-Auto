package co.zw.nissangtr.pos.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TillFloatRulesTest {
    @Test
    fun rejectsPettyCash1110() {
        val err = TillFloatRules.validateOpenAccount("1110")
        assertNotNull(err)
        assertTrue(err!!.contains("1110"))
        assertTrue(err.contains("1120"))
    }

    @Test
    fun acceptsCashSales1120() {
        assertNull(TillFloatRules.validateOpenAccount("1120"))
        assertNull(TillFloatRules.validateOpenAccount(" 1120 "))
    }

    @Test
    fun rejectsOtherAccounts() {
        assertNotNull(TillFloatRules.validateOpenAccount("1100"))
        assertNotNull(TillFloatRules.validateOpenAccount(""))
    }

    @Test
    fun openRequestValidated() {
        val req = OpenTillFloatRequest(
            accountCode = TillFloatRules.CASH_SALES_TILL,
            periodStart = "2026-08-15",
            periodEnd = "2026-08-15",
            openingBalance = 100.0,
        ).validated()
        assertEquals("1120", req.accountCode)
    }

    @Test(expected = IllegalArgumentException::class)
    fun openRequestRejects1110() {
        OpenTillFloatRequest(
            accountCode = "1110",
            periodStart = "2026-08-15",
            periodEnd = "2026-08-15",
            openingBalance = 50.0,
        ).validated()
    }
}

class ChassisChipLatchTest {
    @Test
    fun latchUppercasesChassis() {
        val latch = ChassisChipLatch.latchFromChip(" r35 ", engineCode = " vr38dett ")
        assertEquals("R35", latch!!.chassisCode)
        assertEquals("VR38DETT", latch.engineCode)
    }

    @Test
    fun blankChassisNull() {
        assertNull(ChassisChipLatch.latchFromChip("  "))
    }

    @Test
    fun normalizeDistinct() {
        val list = ChassisChipLatch.normalizeChipList(listOf("r35", "R35", " y62 ", "", "D22"))
        assertEquals(listOf("R35", "Y62", "D22"), list)
    }
}

class HandoffIntentParseTest {
    @Test
    fun parsesHandoffWithoutExposingSecretsInSummary() {
        val payload = HandoffIntentParse.parse(
            mapOf(
                HandoffExtras.HANDOFF to "1",
                HandoffExtras.STAFF_DISPLAY_NAME to "T Moyo",
                HandoffExtras.ACCESS_TOKEN to "secret-access-token-xyz",
                HandoffExtras.REFRESH_TOKEN to "secret-refresh-token-abc",
                HandoffExtras.TERMINAL_ID to "TILL-01",
            ),
        )
        assertTrue(payload.handoff)
        assertTrue(payload.canEstablishLiveSession)
        assertEquals("T Moyo", payload.staffDisplayName)
        val summary = payload.safeSummary()
        assertFalse(summary.contains("secret-access"))
        assertFalse(summary.contains("secret-refresh"))
        assertTrue(summary.contains("token=true"))
        assertTrue(summary.contains("staff=true"))
    }

    @Test
    fun withoutHandoffFlagCannotSkipLogin() {
        val payload = HandoffIntentParse.parse(
            mapOf(HandoffExtras.ACCESS_TOKEN to "tok"),
        )
        assertFalse(payload.handoff)
        assertFalse(payload.canEstablishLiveSession)
    }
}
