package co.zw.nissangtr.customer.compare

import android.content.Context
import co.zw.nissangtr.customer.rpc.CompareItem
import co.zw.nissangtr.customer.rpc.RpcNames

/**
 * Guest compare tray — SharedPreferences OEM list (web `compare-selection` localStorage parity).
 * Auth users sync via list/add/remove compare RPCs.
 */
object GuestCompareStore {
    private const val PREFS = "gtr_customer_compare"
    private const val KEY = "gtr.compare.oems"

    fun readOems(context: Context): List<String> {
        val raw = prefs(context).getString(KEY, null)
            ?.split('\u001e')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        return normalize(raw)
    }

    fun writeOems(context: Context, oems: List<String>): List<String> {
        val next = normalize(oems).take(RpcNames.MAX_COMPARE_ITEMS)
        prefs(context).edit().putString(KEY, next.joinToString("\u001e")).apply()
        return next
    }

    fun addOem(context: Context, oem: String): List<String> {
        val needle = oem.trim()
        require(needle.isNotEmpty()) { "OEM required." }
        val list = readOems(context).toMutableList()
        if (list.any { it.equals(needle, ignoreCase = true) }) return list
        require(list.size < RpcNames.MAX_COMPARE_ITEMS) {
            "Compare holds up to ${RpcNames.MAX_COMPARE_ITEMS} SKUs. Remove one first."
        }
        list.add(0, needle)
        return writeOems(context, list)
    }

    fun removeOem(context: Context, oem: String): List<String> {
        val needle = oem.trim()
        val next = readOems(context).filter { !it.equals(needle, ignoreCase = true) }
        return writeOems(context, next)
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY).apply()
    }

    /** Map guest OEMs into [CompareItem] rows (synthetic ids) for matrix UI. */
    fun asCompareItems(context: Context): List<CompareItem> =
        readOems(context).mapIndexed { idx, oem ->
            CompareItem(
                id = "00000000-0000-4000-8000-%012d".format(idx + 1),
                stockItemId = "00000000-0000-4000-8000-%012d".format(idx + 100),
                oemPartNumber = oem,
                description = null,
                createdAt = null,
            )
        }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun normalize(oems: List<String>): List<String> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<String>()
        for (raw in oems) {
            val oem = raw.trim()
            if (oem.isEmpty()) continue
            val key = oem.lowercase()
            if (!seen.add(key)) continue
            out.add(oem)
        }
        return out
    }
}
