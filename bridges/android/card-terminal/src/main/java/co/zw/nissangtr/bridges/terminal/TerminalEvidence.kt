package co.zw.nissangtr.bridges.terminal

/**
 * `gtr-card-terminal-evidence-v1`: the terminal answer as the server re-builds it before checking the
 * signature. [canonicalJson] must equal the server's `JSON.stringify(payload)` byte for byte: same
 * key order, values trimmed, blanks as null.
 */
data class TerminalEvidence(
    val attemptId: String,
    val deviceId: String,
    val observedAtIso: String,
    val result: TerminalResult,
) {
    fun canonicalJson(): String {
        fun s(v: String?): String = v?.trim()?.takeIf { it.isNotEmpty() }?.let(::quote) ?: "null"
        val r = result
        return buildString {
            append('{')
            append("\"version\":").append(quote("gtr-card-terminal-evidence-v1"))
            append(",\"attempt_id\":").append(quote(attemptId.trim()))
            append(",\"device_id\":").append(quote(deviceId.trim()))
            append(",\"observed_at\":").append(quote(observedAtIso.trim()))
            append(",\"outcome\":").append(quote(r.outcome.wire))
            append(",\"terminal_transaction_id\":").append(s(r.transactionId))
            append(",\"rrn\":").append(s(r.rrn))
            append(",\"authorization_code\":").append(s(r.authorizationCode))
            append(",\"card_last4\":").append(s(r.cardLast4))
            append(",\"card_scheme\":").append(s(r.cardScheme))
            append(",\"response_code\":").append(s(r.responseCode))
            append(",\"response_message\":").append(s(r.responseMessage))
            append('}')
        }
    }

    companion object {
        /** ECMAScript JSON.stringify string quoting. */
        fun quote(value: String): String = buildString {
            append('"')
            for (c in value) {
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\b' -> append("\\b")
                    '\u000C' -> append("\\f")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                }
            }
            append('"')
        }
    }
}
