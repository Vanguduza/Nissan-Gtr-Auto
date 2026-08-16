package co.zw.nissangtr.management.gtradapter

/**
 * Web `/staff/account` — RPCs `get_my_staff_profile`, `update_my_staff_profile`,
 * `list_my_payslip_history`. Photo Storage / branded PDF remain follow-ups.
 */
interface GtrMyAccountAdapter {
    suspend fun getMyStaffProfile(): Result<GtrStaffProfile>

    suspend fun updateMyStaffProfile(
        phoneE164: String? = null,
        address: String? = null,
        email: String? = null,
        syncAuthEmail: Boolean = false,
    ): Result<GtrStaffProfile>

    suspend fun listMyPayslipHistory(): Result<List<GtrPayslipHistoryRow>>
}

data class GtrStaffProfile(
    val hasEmployee: Boolean,
    val userId: String? = null,
    val employeeId: String? = null,
    val employeeCode: String? = null,
    val fullName: String? = null,
    val email: String? = null,
    val phoneE164: String? = null,
    val address: String? = null,
    val moduleAccess: List<String> = emptyList(),
    val staffRoles: List<String> = emptyList(),
)

data class GtrPayslipHistoryRow(
    val payrollLineId: String,
    val payrollRunId: String = "",
    val periodStart: String,
    val periodEnd: String,
    val documentNumber: String? = null,
    val currency: String = "USD",
    val grossAmount: Double = 0.0,
    val deductionsAmount: Double = 0.0,
    val netAmount: Double = 0.0,
    val funded: Boolean = false,
)

class FakeGtrMyAccountAdapter(
    private val session: GtrStaffSession,
) : GtrMyAccountAdapter {
    @Volatile
    private var profile = GtrStaffProfile(
        hasEmployee = true,
        userId = "fake-staff",
        employeeId = "emp-fake",
        employeeCode = "E1001",
        fullName = "Fake Staff",
        email = "fake@local.test",
        phoneE164 = "+263771000001",
        address = "Harare",
        moduleAccess = emptyList(),
        staffRoles = listOf("admin"),
    )

    private val payslips = listOf(
        GtrPayslipHistoryRow(
            payrollLineId = "pl-1",
            payrollRunId = "pr-1",
            periodStart = "2026-07-01",
            periodEnd = "2026-07-31",
            documentNumber = "PS-2026-07",
            currency = "USD",
            grossAmount = 500.0,
            deductionsAmount = 20.0,
            netAmount = 480.0,
            funded = true,
        ),
    )

    override suspend fun getMyStaffProfile(): Result<GtrStaffProfile> {
        val roles = session.context?.roles.orEmpty()
        if (roles.isNotEmpty()) {
            profile = profile.copy(staffRoles = roles)
        }
        return Result.success(profile)
    }

    override suspend fun updateMyStaffProfile(
        phoneE164: String?,
        address: String?,
        email: String?,
        syncAuthEmail: Boolean,
    ): Result<GtrStaffProfile> {
        profile = profile.copy(
            phoneE164 = phoneE164 ?: profile.phoneE164,
            address = address ?: profile.address,
            email = email ?: profile.email,
        )
        return Result.success(profile)
    }

    override suspend fun listMyPayslipHistory(): Result<List<GtrPayslipHistoryRow>> =
        Result.success(payslips)
}
