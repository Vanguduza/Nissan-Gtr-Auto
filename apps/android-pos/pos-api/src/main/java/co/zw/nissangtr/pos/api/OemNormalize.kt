package co.zw.nissangtr.pos.api

/**
 * OEM / scan string normalize for search + hydrate.
 * Trim, uppercase, collapse internal whitespace, keep hyphen.
 */
object OemNormalize {
    fun normalize(raw: String): String =
        raw.trim()
            .uppercase()
            .replace(Regex("\\s+"), "")
}
