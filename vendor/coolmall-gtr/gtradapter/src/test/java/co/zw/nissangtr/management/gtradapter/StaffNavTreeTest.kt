package co.zw.nissangtr.management.gtradapter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffNavTreeTest {
    @Test
    fun adminSeesAllNonPosModules() {
        val modules = StaffNavTree.filterModules(
            roles = listOf("admin"),
            moduleAccess = listOf("finance"),
            excludePos = true,
        )
        assertFalse(modules.any { it.id == StaffNavTree.POS_MODULE_ID })
        assertTrue(modules.any { it.id == "warehouse" })
        assertTrue(modules.any { it.id == "finance" })
        assertTrue(modules.any { it.id == "hr" })
        assertEquals(StaffNavTree.MODULES.size - 1, modules.size)
    }

    @Test
    fun warehouseRoleSeesWarehouseNotFinance() {
        val modules = StaffNavTree.filterModules(
            roles = listOf("warehouse"),
            moduleAccess = null,
            excludePos = true,
        )
        assertTrue(modules.any { it.id == "warehouse" })
        assertFalse(modules.any { it.id == "finance" })
        assertFalse(modules.any { it.id == StaffNavTree.POS_MODULE_ID })
    }

    @Test
    fun moduleAccessNarrowsNonAdmin() {
        val modules = StaffNavTree.filterModules(
            roles = listOf("warehouse", "finance"),
            moduleAccess = listOf("warehouse"),
            excludePos = true,
        )
        assertEquals(listOf("warehouse"), modules.map { it.id })
    }

    @Test
    fun emptyModuleAccessFallsBackToRoles() {
        val modules = StaffNavTree.filterModules(
            roles = listOf("finance"),
            moduleAccess = emptyList(),
            excludePos = true,
        )
        assertTrue(modules.any { it.id == "finance" })
        assertFalse(modules.any { it.id == "warehouse" })
    }

    @Test
    fun prefersPosHomeSalesOnly() {
        assertTrue(StaffNavTree.prefersPosHome(listOf("sales")))
        assertFalse(StaffNavTree.prefersPosHome(listOf("sales", "warehouse")))
        assertFalse(StaffNavTree.prefersPosHome(listOf("admin")))
    }

    @Test
    fun financeAndProcurementLeavesMatchWebTabs() {
        val finance = StaffNavTree.MODULES.first { it.id == "finance" }
        assertEquals(13, finance.children.size)
        val proc = StaffNavTree.MODULES.first { it.id == "procurement" }
        assertTrue(proc.children.any { it.href == "/procurement/grn" })
        val leaves = StaffNavTree.filterLeaves(finance, listOf("finance"))
        assertEquals(13, leaves.size)
    }
}
