package co.zw.nissangtr.management.rpc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManagementHomeRolesTest {

    @Test
    fun salesOnly_prefersPos() {
        assertTrue(ManagementHomeRoles.prefersPosHome(listOf("sales")))
        assertEquals(
            ManagementHomeLanding.Pos,
            ManagementHomeRoles.resolveLanding(listOf("sales")),
        )
    }

    @Test
    fun hubRoles_neverPreferPos() {
        listOf("admin", "warehouse", "finance", "hr", "dispatcher").forEach { role ->
            assertFalse("$role should land on hub", ManagementHomeRoles.prefersPosHome(listOf(role)))
            assertEquals(
                ManagementHomeLanding.Hub,
                ManagementHomeRoles.resolveLanding(listOf(role)),
            )
        }
    }

    @Test
    fun hubWinsOverSales_whenMultiRole() {
        assertFalse(ManagementHomeRoles.prefersPosHome(listOf("sales", "admin")))
        assertFalse(ManagementHomeRoles.prefersPosHome(listOf("sales", "warehouse")))
        assertFalse(ManagementHomeRoles.prefersPosHome(listOf("sales", "finance")))
        assertFalse(ManagementHomeRoles.prefersPosHome(listOf("sales", "hr")))
        assertFalse(ManagementHomeRoles.prefersPosHome(listOf("sales", "dispatcher")))
        assertEquals(
            ManagementHomeLanding.Hub,
            ManagementHomeRoles.resolveLanding(listOf("SALES", "Warehouse")),
        )
    }

    @Test
    fun emptyOrUnknown_failClosed() {
        assertFalse(ManagementHomeRoles.hasActiveStaffRole(emptyList()))
        assertFalse(ManagementHomeRoles.hasStaffAccess(listOf("")))
        assertFalse(ManagementHomeRoles.hasActiveStaffRole(listOf("customer", "ghost")))
        assertEquals(ManagementHomeLanding.Deny, ManagementHomeRoles.resolveLanding(emptyList()))
        assertEquals(
            ManagementHomeLanding.Deny,
            ManagementHomeRoles.resolveLanding(listOf("inactive", "bogus")),
        )
        assertFalse(ManagementHomeRoles.prefersPosHome(emptyList()))
    }

    @Test
    fun normalize_trimsAndDedupesKnown() {
        assertEquals(
            listOf("admin", "sales"),
            ManagementHomeRoles.normalize(listOf(" Admin ", "sales", "ADMIN", "unknown")),
        )
    }

    @Test
    fun moduleAllowed_adminBypass_andEmptyAccessShowsAll() {
        assertTrue(
            ManagementHomeRoles.moduleAllowed("pos", listOf("admin"), listOf("warehouse")),
        )
        assertTrue(
            ManagementHomeRoles.moduleAllowed("finance", listOf("sales"), emptyList()),
        )
        assertFalse(
            ManagementHomeRoles.moduleAllowed("finance", listOf("sales"), listOf("pos")),
        )
        assertTrue(
            ManagementHomeRoles.moduleAllowed("pos", listOf("sales"), listOf("POS")),
        )
    }

    @Test
    fun defaultLanding_overridesRoleHeuristic_butStillFailClosed() {
        assertEquals(
            ManagementHomeLanding.Hub,
            ManagementHomeRoles.resolveLanding(listOf("sales"), "hub"),
        )
        assertEquals(
            ManagementHomeLanding.Pos,
            ManagementHomeRoles.resolveLanding(listOf("warehouse"), "pos"),
        )
        assertEquals(
            ManagementHomeLanding.Deny,
            ManagementHomeRoles.resolveLanding(emptyList(), "pos"),
        )
        assertEquals(
            ManagementHomeLanding.Pos,
            ManagementHomeRoles.resolveLanding(listOf("sales"), null),
        )
    }
}
