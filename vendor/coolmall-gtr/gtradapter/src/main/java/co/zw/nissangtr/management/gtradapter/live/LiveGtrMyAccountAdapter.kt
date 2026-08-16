package co.zw.nissangtr.management.gtradapter.live

import co.zw.nissangtr.management.gtradapter.GtrMyAccountAdapter
import co.zw.nissangtr.management.gtradapter.GtrPayslipHistoryRow
import co.zw.nissangtr.management.gtradapter.GtrStaffProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Live My Account — web `staff-account.ts` RPCs (profile + payslip history).
 * Photo upload / branded PDF deferred.
 */
class LiveGtrMyAccountAdapter(
    private val client: SupabaseClient,
) : GtrMyAccountAdapter {

    override suspend fun getMyStaffProfile(): Result<GtrStaffProfile> = runCatching {
        val el = client.postgrest.rpc("get_my_staff_profile").decodeAs<JsonObject>()
        parseProfile(el)
    }

    override suspend fun updateMyStaffProfile(
        phoneE164: String?,
        address: String?,
        email: String?,
        syncAuthEmail: Boolean,
    ): Result<GtrStaffProfile> = runCatching {
        val trimmedEmail = email?.trim().orEmpty()
        if (syncAuthEmail && trimmedEmail.isNotEmpty()) {
            client.auth.updateUser { this.email = trimmedEmail }
        }
        val el = client.postgrest.rpc(
            "update_my_staff_profile",
            buildJsonObject {
                if (phoneE164 != null) {
                    val p = phoneE164.trim()
                    if (p.isEmpty()) put("p_phone_e164", JsonNull) else put("p_phone_e164", p)
                }
                if (address != null) {
                    val a = address.trim()
                    if (a.isEmpty()) put("p_address", JsonNull) else put("p_address", a)
                }
                if (email != null) {
                    if (trimmedEmail.isEmpty()) put("p_email", JsonNull)
                    else put("p_email", trimmedEmail)
                }
            },
        ).decodeAs<JsonObject>()
        parseProfile(el)
    }

    override suspend fun listMyPayslipHistory(): Result<List<GtrPayslipHistoryRow>> =
        runCatching {
            val el = client.postgrest.rpc("list_my_payslip_history").decodeAs<JsonArray>()
            parsePayslips(el)
        }

    companion object {
        internal fun parseProfile(root: JsonObject): GtrStaffProfile =
            GtrStaffProfile(
                hasEmployee = root["has_employee"]?.jsonPrimitive?.booleanOrNull == true,
                userId = root.stringOrNull("user_id"),
                employeeId = root.stringOrNull("employee_id"),
                employeeCode = root.stringOrNull("employee_code"),
                fullName = root.stringOrNull("full_name"),
                email = root.stringOrNull("email"),
                phoneE164 = root.stringOrNull("phone_e164"),
                address = root.stringOrNull("address"),
                moduleAccess = root.stringList("module_access"),
                staffRoles = root.stringList("staff_roles"),
            )

        internal fun parseProfile(raw: String): GtrStaffProfile =
            parseProfile(Json.parseToJsonElement(raw).jsonObject)

        internal fun parsePayslips(arr: JsonArray): List<GtrPayslipHistoryRow> =
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val lineId = o.stringOrNull("payroll_line_id") ?: return@mapNotNull null
                val currency = o.stringOrNull("currency").orEmpty().ifBlank { "USD" }
                GtrPayslipHistoryRow(
                    payrollLineId = lineId,
                    payrollRunId = o.stringOrNull("payroll_run_id").orEmpty(),
                    periodStart = o.stringOrNull("period_start").orEmpty(),
                    periodEnd = o.stringOrNull("period_end").orEmpty(),
                    documentNumber = o.stringOrNull("document_number"),
                    currency = if (currency == "ZIG" || currency == "USD") currency else "USD",
                    grossAmount = o["gross_amount"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    deductionsAmount = o["deductions_amount"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    netAmount = o["net_amount"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
                    funded = o["funded"]?.jsonPrimitive?.booleanOrNull == true,
                )
            }

        internal fun parsePayslips(raw: String): List<GtrPayslipHistoryRow> {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || trimmed == "null") return emptyList()
            return parsePayslips(Json.parseToJsonElement(trimmed).jsonArray)
        }

        private fun JsonObject.stringOrNull(key: String): String? =
            this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        private fun JsonObject.stringList(key: String): List<String> {
            val el = this[key] ?: return emptyList()
            return when (el) {
                is JsonArray -> el.mapNotNull { it.jsonPrimitive.contentOrNull }
                else -> emptyList()
            }
        }
    }
}
