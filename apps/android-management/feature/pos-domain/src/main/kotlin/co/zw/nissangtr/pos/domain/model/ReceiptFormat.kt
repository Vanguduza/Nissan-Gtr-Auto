package co.zw.nissangtr.pos.domain.model

/*
 * GTR counter receipt, format v1 — the layout both POS clients print. Source of truth and
 * conformance fixture: packages/shared/src/pos/receipt.ts and
 * packages/shared/fixtures/pos-receipt/v1.json; ReceiptFormatTest reproduces that fixture exactly.
 */

const val RECEIPT_FORMAT_VERSION = 1
const val THERMAL_COLUMNS = 42

/** Printed labels; `{0}` is the value. The tablet fills these from string resources. */
data class ReceiptLabels(
    val brand: String,
    val strap: String,
    val invoice: String,
    val servedBy: String,
    val customer: String,
    val vehicle: String,
    val subtotal: String,
    val discount: String,
    val total: String,
    val cashGiven: String,
    val change: String,
    val offline: String,
    val thanks: String,
    val tenders: Map<Tender, String>,
)

data class ReceiptRow(val left: String, val right: String = "", val strong: Boolean = false)

/** `US$ 1,250.00` / `ZiG 3,400.50`. */
fun formatReceiptMoney(money: Money): String {
    val symbol = when (money.currency.code) {
        "USD" -> "US$"
        "ZIG" -> "ZiG"
        else -> money.currency.code
    }
    val abs = kotlin.math.abs(money.minor)
    val whole = (abs / 100).toString().reversed().chunked(3).joinToString(",").reversed()
    return "$symbol ${if (money.minor < 0) "-" else ""}$whole.${(abs % 100).toString().padStart(2, '0')}"
}

fun formatReceiptQty(qty: Double): String =
    if (qty % 1.0 == 0.0) qty.toLong().toString() else qty.toString()

private fun String.fill(value: String) = replace("{0}", value)

/** [issuedAtLocal]: shop-local wall time `yyyy-MM-ddTHH:mm[...]`. */
fun receiptRows(r: Receipt, labels: ReceiptLabels, issuedAtLocal: String = r.issuedAtIso): List<ReceiptRow> {
    val money = ::formatReceiptMoney
    return buildList {
        add(ReceiptRow(labels.brand, strong = true))
        add(ReceiptRow(labels.strap))
        add(ReceiptRow(""))
        add(ReceiptRow(labels.invoice.fill(r.documentNumber ?: r.invoiceId.take(8)), strong = true))
        add(ReceiptRow(issuedAtLocal.replace('T', ' ').take(16)))
        r.operatorName?.takeIf { it.isNotEmpty() }?.let { add(ReceiptRow(labels.servedBy.fill(it))) }
        r.customerName?.takeIf { it.isNotEmpty() }?.let { add(ReceiptRow(labels.customer.fill(it))) }
        r.vehicleLabel?.takeIf { it.isNotEmpty() }?.let { add(ReceiptRow(labels.vehicle.fill(it))) }
        add(ReceiptRow(""))
        r.lines.forEach { l ->
            add(ReceiptRow(l.name))
            add(ReceiptRow("  ${l.oemPartNumber}"))
            add(ReceiptRow("  ${formatReceiptQty(l.qty)} × ${money(l.unitPrice)}", money(l.lineTotal)))
        }
        add(ReceiptRow(""))
        add(ReceiptRow(labels.subtotal, money(r.subtotal)))
        add(ReceiptRow(labels.discount, money(r.discount)))
        add(ReceiptRow(labels.total, money(r.total), strong = true))
        r.tenders.forEach { add(ReceiptRow(labels.tenders.getValue(it.tender), money(it.amount))) }
        r.cashGiven?.let { add(ReceiptRow(labels.cashGiven, money(it))) }
        r.change?.let { add(ReceiptRow(labels.change, money(it), strong = true)) }
        add(ReceiptRow(""))
        if (r.offline) add(ReceiptRow(labels.offline, strong = true))
        add(ReceiptRow(labels.thanks))
    }
}

/**
 * Lays a row out in [width] columns. Text-only rows word-wrap (a long part name is never lost);
 * rows with an amount keep it flush right and cut the label to fit.
 */
fun thermalLines(left: String, right: String, width: Int = THERMAL_COLUMNS): List<String> {
    fun len(t: String) = t.codePointCount(0, t.length)
    fun take(t: String, n: Int) = t.substring(0, t.offsetByCodePoints(0, minOf(n, len(t))))
    fun drop(t: String, n: Int) = t.substring(t.offsetByCodePoints(0, minOf(n, len(t))))
    if (right.isEmpty()) {
        if (len(left) <= width) return listOf(left)
        val indent = left.takeWhile { it == ' ' }
        val out = mutableListOf<String>()
        var line = ""
        for (word in left.trim().split(Regex("\\s+"))) {
            val next = if (line.isNotEmpty()) "$line $word" else "$indent$word"
            if (len(next) <= width) {
                line = next
            } else {
                if (line.isNotEmpty()) out += line
                var rest = "$indent$word"
                while (len(rest) > width) {
                    out += take(rest, width)
                    rest = indent + drop(rest, width)
                }
                line = rest
            }
        }
        if (line.isNotEmpty()) out += line
        return out
    }
    val room = maxOf(width - len(right) - 1, 1)
    val cut = take(left, room)
    return listOf(cut + " ".repeat(room - len(cut)) + " " + right)
}

fun thermalText(rows: List<ReceiptRow>, width: Int = THERMAL_COLUMNS): List<String> =
    rows.flatMap { thermalLines(it.left, it.right, width) }
