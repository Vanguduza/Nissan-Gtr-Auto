package co.zw.nissangtr.management.gtradapter

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeGtrAdaptersTest {
    @Test
    fun staffSignInLoadsAdminContextAndHubExcludesPos() = runBlocking {
        val session = GtrStaffSession()
        val auth = FakeGtrStaffAuthAdapter(session)
        val hub = FakeGtrStaffHubAdapter()

        val signed = auth.signInWithStaffIdentifier("E1001", "password1")
        assertTrue(signed.isSuccess)

        val ctx = auth.loadStaffContext().getOrThrow()
        assertEquals(listOf("admin"), ctx.roles)
        assertTrue(ctx.isStaff)

        val modules = hub.filterModules(ctx)
        assertTrue(modules.none { it.id == StaffNavTree.POS_MODULE_ID })
        assertEquals("hub", hub.homeRoute(ctx))
    }

    @Test
    fun masterStockFiltersByOemQuery() = runBlocking {
        val wh = FakeGtrWarehouseAdapter()
        val all = wh.listMasterStock().getOrThrow()
        assertTrue(all.size >= 3)

        val filtered = wh.listMasterStock(query = "16546").getOrThrow()
        assertEquals(1, filtered.size)
        assertEquals("16546-6N200", filtered.first().oemPartNumber)
    }

    @Test
    fun changePasswordClearsMustChangeFlag() = runBlocking {
        val session = GtrStaffSession()
        session.update(
            GtrStaffContext(
                userId = "u1",
                isStaff = true,
                roles = listOf("warehouse"),
                moduleAccess = null,
                mustChangePassword = true,
            ),
        )
        val pwd = FakeGtrPasswordAdapter(session)
        assertTrue(pwd.changePassword("newpass12").isSuccess)
        assertEquals(false, session.context?.mustChangePassword)
    }
}
