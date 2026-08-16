package co.zw.nissangtr.management.gtradapter.live

import co.zw.nissangtr.management.gtradapter.GtrOpsCatalog
import co.zw.nissangtr.management.gtradapter.GtrOpsRow
import co.zw.nissangtr.management.gtradapter.GtrStaffOpsAdapter
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.bodyAsText
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class LiveGtrStaffOpsAdapter(
    private val client: SupabaseClient,
) : GtrStaffOpsAdapter {

    override suspend fun listLeaf(href: String): Result<List<GtrOpsRow>> = runCatching {
        val spec = GtrOpsCatalog.spec(href)
            ?: error("Unknown desk $href")
        when {
            spec.table != null -> selectTable(spec.table, spec.order)
            href == "/staff/crm/product-pages" -> rpcArray("list_staff_product_pages", buildJsonObject {
                put("p_query", JsonNull)
                put("p_limit", 100)
            })
            href == "/staff/logistics/prep" -> rpcArray("list_online_prep_queue", buildJsonObject {})
            href == "/staff/fleet" -> rpcArray("list_fleet_vehicles", buildJsonObject {})
            href == "/staff/chat" -> rpcArray("list_staff_chat_threads", buildJsonObject {})
            href == "/staff/finance?tab=exchange-rate" ->
                rpcArray("list_zig_exchange_rates", buildJsonObject {})
            else -> emptyList()
        }
    }

    override suspend fun runAction(
        href: String,
        actionId: String,
        rowId: String?,
        fields: Map<String, String>,
    ): Result<String> = runCatching {
        val spec = GtrOpsCatalog.spec(href) ?: error("Unknown desk $href")
        val action = spec.actions.find { it.id == actionId } ?: error("Unknown action $actionId")
        if (action.needsRow && rowId.isNullOrBlank()) error("Select a row")
        if (spec.neverAutoPo && actionId.contains("auto", ignoreCase = true)) {
            error("AI never auto-creates POs")
        }
        if (action.edge != null) {
            val payload = buildJsonObject {
                fields.forEach { (k, v) ->
                    val trimmed = v.trim()
                    if (trimmed.isNotEmpty()) put(k, trimmed)
                }
                if (action.edge == "analytics-insights") {
                    put("include_narrative", true)
                    put("kpi_set", "ops_sales_v1")
                }
                if (action.edge == "stores-insights") {
                    put("include_narrative", true)
                }
            }
            val response = client.functions.invoke(action.edge, payload)
            return@runCatching response.bodyAsText().take(400).ifBlank { "ok" }
        }
        val rpc = action.rpc ?: error("No RPC")
        val payload = buildJsonObject {
            if (action.idParam != null && !rowId.isNullOrBlank()) {
                put(action.idParam, rowId)
            }
            spec.fields.forEach { field ->
                val raw = fields[field.key]?.trim().orEmpty()
                if (raw.isNotEmpty()) {
                    putRpcValue(field.rpcParam, raw)
                }
            }
            // payroll line ids array
            val lineIds = fields["payroll_line_ids"]?.trim().orEmpty()
            if (lineIds.isNotEmpty() && rpc == "fund_payroll_lines") {
                put(
                    "p_payroll_line_ids",
                    JsonArray(lineIds.split(",").map { JsonPrimitive(it.trim()) }),
                )
            }
        }
        val result = client.postgrest.rpc(rpc, payload)
        result.data.trim().ifBlank { "ok" }.take(500)
    }

    private suspend fun selectTable(table: String, order: String): List<GtrOpsRow> {
        val result = client.from(table).select {
            order(order, Order.DESCENDING)
            limit(40)
        }
        return parseRows(result.data)
    }

    private suspend fun rpcArray(rpc: String, args: JsonObject): List<GtrOpsRow> {
        val result = client.postgrest.rpc(rpc, args)
        return parseRows(result.data)
    }

    private fun parseRows(raw: String): List<GtrOpsRow> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "null") return emptyList()
        val el = Json.parseToJsonElement(trimmed)
        val arr = when (el) {
            is JsonArray -> el
            is JsonObject -> JsonArray(listOf(el))
            else -> return listOf(GtrOpsRow("result", trimmed.take(80), "", ""))
        }
        return arr.mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val id = firstString(o, "id", "stock_item_id", "invoice_id", "thread_id", "payroll_line_id")
                ?: return@mapNotNull null
            val title = firstString(
                o,
                "document_number",
                "oem_part_number",
                "code",
                "display_name",
                "full_name",
                "title",
                "plate",
                "email",
                "name",
            ) ?: id
            val status = firstString(o, "status", "kind", "decision").orEmpty()
            val subtitle = firstString(o, "description", "memo", "notes", "catalog_title", "body")
                ?: o.entries.take(3).joinToString(" · ") { "${it.key}=${it.value.jsonPrimitiveOrEmpty()}" }
            GtrOpsRow(id = id, title = title, subtitle = subtitle.take(160), status = status)
        }
    }

    private fun firstString(o: JsonObject, vararg keys: String): String? {
        for (k in keys) {
            val v = o[k]?.jsonPrimitive?.contentOrNull?.trim()
            if (!v.isNullOrEmpty()) return v
        }
        return null
    }

    private fun JsonElement.jsonPrimitiveOrEmpty(): String =
        (this as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun kotlinx.serialization.json.JsonObjectBuilder.putRpcValue(key: String, raw: String) {
        when {
            raw.equals("true", true) || raw.equals("false", true) ->
                put(key, raw.equals("true", true))
            raw.toDoubleOrNull() != null && raw.contains('.').not() && raw.length < 12 ->
                raw.toLongOrNull()?.let { put(key, it) } ?: put(key, raw)
            raw.toDoubleOrNull() != null -> put(key, raw.toDouble())
            else -> put(key, raw)
        }
    }
}
