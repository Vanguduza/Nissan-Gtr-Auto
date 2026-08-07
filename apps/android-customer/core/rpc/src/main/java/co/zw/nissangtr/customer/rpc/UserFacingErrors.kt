package co.zw.nissangtr.customer.rpc

/**
 * Maps raw GoTrue / PostgREST exception text (often includes URL + headers) to short UI copy.
 */
object UserFacingErrors {
    fun from(throwable: Throwable?, fallback: String = "Something went wrong"): String {
        val raw = buildString {
            append(throwable?.message.orEmpty())
            append(' ')
            append(throwable?.cause?.message.orEmpty())
            append(' ')
            append(throwable?.toString().orEmpty())
        }
        return fromRaw(raw, fallback)
    }

    fun fromRaw(raw: String?, fallback: String = "Something went wrong"): String {
        val t = raw.orEmpty()
        val lower = t.lowercase()
        return when {
            "invalid_credentials" in lower ||
                "invalid login credentials" in lower -> "Invalid credentials"
            "email not confirmed" in lower || "email_not_confirmed" in lower ->
                "Confirm your email, then sign in again"
            "user already registered" in lower || "already been registered" in lower ->
                "An account with this email already exists"
            "customer profile required" in lower ||
                "customer_profile_required" in lower ||
                ("_current_customer_id" in lower && "null" in lower) ->
                "Account setup incomplete. Sign out and sign in again, or contact support."
            "signup is disabled" in lower || "signups not allowed" in lower ->
                "Sign-up is disabled. Use Google or contact support."
            "network" in lower || "unable to resolve host" in lower || "timeout" in lower ->
                "Network error. Check your connection and try again."
            t.isBlank() -> fallback
            // Strip dumped HTTP blobs (URL + Authorization headers from supabase-kt)
            "http://" in lower || "https://" in lower || "authorization" in lower ->
                friendlySnippet(t) ?: fallback
            t.length > 160 -> friendlySnippet(t) ?: fallback
            else -> t.trim().take(160)
        }
    }

    private fun friendlySnippet(t: String): String? {
        val firstLine = t.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        if (firstLine.isEmpty()) return null
        if ("http" in firstLine.lowercase() || "authorization" in firstLine.lowercase()) {
            // Prefer leading error code token before URL dump
            val code = Regex("""([a-z_]+)\s*\(""", RegexOption.IGNORE_CASE)
                .find(firstLine)?.groupValues?.getOrNull(1)
            return when (code?.lowercase()) {
                "invalid_credentials" -> "Invalid credentials"
                else -> "Request failed. Please try again."
            }
        }
        return firstLine.take(120)
    }
}
