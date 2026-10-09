package co.zw.nissangtr.catalogapk.discovery

object CloudflareDetector {
    fun looksLikeChallenge(statusCode: Int, body: String, headers: Map<String, String>): Boolean {
        if (statusCode == 403 || statusCode == 503) {
            val joined = headers.entries.joinToString(" ") { "${it.key}:${it.value}" }.lowercase()
            if (joined.contains("cf-") || joined.contains("cloudflare")) return true
        }
        val lower = body.lowercase()
        return "just a moment" in lower ||
            "cf-browser-verification" in lower ||
            "cdn-cgi/challenge" in lower ||
            ("cloudflare" in lower && "challenge" in lower) ||
            "attention required" in lower && "cloudflare" in lower
    }
}
