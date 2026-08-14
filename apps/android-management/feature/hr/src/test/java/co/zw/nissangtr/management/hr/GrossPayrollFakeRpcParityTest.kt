package co.zw.nissangtr.management.hr

import co.zw.nissangtr.management.rpc.FakeRpcClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GrossPayrollFakeRpcParityTest {

    @Test
    fun hoursLinesAndManualDeduction_fakePath() = runBlocking {
        val rpc = FakeRpcClient()

        val hours = rpc.attendanceHoursInPeriod(
            employeeId = FakeRpcClient.FAKE_EMPLOYEE_ID,
            periodStart = "2026-08-01T00:00:00Z",
            periodEnd = "2026-08-14T23:59:59.999999999Z",
        )
        assertEquals(40.0, hours, 0.001)

        val lines = rpc.listOpenPayrollLines()
        assertTrue(lines.any { it.id == FakeRpcClient.FAKE_PAYROLL_LINE_ID })
        val seed = lines.first { it.id == FakeRpcClient.FAKE_PAYROLL_LINE_ID }
        assertEquals(500.0, seed.grossAmount, 0.001)
        assertEquals(475.0, seed.netAmount, 0.001)

        val dedId = rpc.addPayrollDeduction(
            payrollLineId = FakeRpcClient.FAKE_PAYROLL_LINE_ID,
            label = "Uniform levy",
            amount = 10.0,
        )
        assertTrue(dedId.isNotBlank())

        val after = rpc.listOpenPayrollLines()
            .first { it.id == FakeRpcClient.FAKE_PAYROLL_LINE_ID }
        assertEquals(35.0, after.deductionsAmount, 0.001)
        assertEquals(465.0, after.netAmount, 0.001)

        val deductions = rpc.listPayrollDeductions(FakeRpcClient.FAKE_PAYROLL_LINE_ID)
        assertTrue(deductions.any { it.label == "Uniform levy" && it.amount == 10.0 })
    }

    @Test
    fun periodDateHelpers_parseYmd() {
        val start = GrossPayrollViewModel.toPeriodStartIso("2026-08-01")
        val end = GrossPayrollViewModel.toPeriodEndIso("2026-08-14")
        assertTrue(start!!.startsWith("2026-08-01T00:00:00"))
        assertTrue(end!!.startsWith("2026-08-14T"))
        assertEquals(null, GrossPayrollViewModel.toPeriodStartIso("08/01/2026"))
    }
}
