package co.zw.nissangtr.management.gtradapter

import co.zw.nissangtr.management.gtradapter.live.LiveGtrMyAccountAdapter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        val byChassis = wh.listMasterStock(chassisCode = "Y61").getOrThrow()
        assertEquals(all.size, byChassis.size)
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

    @Test
    fun myAccountFakeReturnsProfileAndPayslips() = runBlocking {
        val session = GtrStaffSession()
        session.update(
            GtrStaffContext(
                userId = "fake-staff",
                isStaff = true,
                roles = listOf("admin"),
                moduleAccess = null,
                mustChangePassword = false,
            ),
        )
        val account = FakeGtrMyAccountAdapter(session)
        val profile = account.getMyStaffProfile().getOrThrow()
        assertEquals("E1001", profile.employeeCode)
        assertTrue(profile.hasEmployee)

        val updated = account.updateMyStaffProfile(phoneE164 = "+263770000000").getOrThrow()
        assertEquals("+263770000000", updated.phoneE164)

        val slips = account.listMyPayslipHistory().getOrThrow()
        assertTrue(slips.isNotEmpty())
        assertEquals("USD", slips.first().currency)
    }

    @Test
    fun idleLockTriggersAfterTimeout() {
        val lock = GtrIdleLockController(idleMs = 50)
        lock.setEnabled(true)
        Thread.sleep(60)
        lock.tick()
        assertTrue(lock.locked.value)
        lock.unlock()
        assertFalse(lock.locked.value)
    }
}

class LiveAdapterParseTest {
    @Test
    fun parseProfileAndPayslipsFromJson() {
        val profile = LiveGtrMyAccountAdapter.parseProfile(
            """
            {
              "has_employee": true,
              "employee_code": "E2002",
              "full_name": "Ada",
              "email": "ada@gtr.test",
              "phone_e164": "+26377111",
              "address": "Harare",
              "module_access": ["warehouse"],
              "staff_roles": ["warehouse", "sales"]
            }
            """.trimIndent(),
        )
        assertEquals("E2002", profile.employeeCode)
        assertEquals(listOf("warehouse"), profile.moduleAccess)
        assertEquals(2, profile.staffRoles.size)

        val slips = LiveGtrMyAccountAdapter.parsePayslips(
            """
            [{
              "payroll_line_id": "pl-9",
              "payroll_run_id": "pr-9",
              "period_start": "2026-06-01",
              "period_end": "2026-06-30",
              "currency": "ZIG",
              "net_amount": 100.5,
              "funded": true
            }]
            """.trimIndent(),
        )
        assertEquals(1, slips.size)
        assertEquals("ZIG", slips.first().currency)
        assertEquals(100.5, slips.first().netAmount, 0.001)
    }
}

class GtrSupabaseConfigTest {
    @Test
    fun useLiveRequiresUrlKeyAndNotForceFake() {
        val live = object : GtrSupabaseConfig {
            override val supabaseUrl = "https://example.supabase.co"
            override val supabaseAnonKey = "anon"
            override val forceFake = false
        }
        assertTrue(live.useLive())

        val forced = object : GtrSupabaseConfig {
            override val supabaseUrl = "https://example.supabase.co"
            override val supabaseAnonKey = "anon"
            override val forceFake = true
        }
        assertFalse(forced.useLive())
        assertFalse(FakeGtrSupabaseConfig.useLive())
    }
}
