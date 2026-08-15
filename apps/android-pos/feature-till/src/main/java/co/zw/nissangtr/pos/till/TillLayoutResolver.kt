package co.zw.nissangtr.pos.till

/**
 * Window-size → till chrome mode (§15.1).
 * Expanded (≥840dp): finder 0.58 · ticket 0.37 · rail 0.05.
 * Compact: finder full + sticky ticket bar.
 */
enum class TillLayoutMode {
    Expanded,
    Compact,
}

object TillLayoutResolver {
    /** Material3 WindowWidthSizeClass.Expanded threshold. */
    const val EXPANDED_MIN_WIDTH_DP: Int = 840

    fun isExpanded(widthDp: Int): Boolean = widthDp >= EXPANDED_MIN_WIDTH_DP

    fun resolve(widthDp: Int): TillLayoutMode =
        if (isExpanded(widthDp)) TillLayoutMode.Expanded else TillLayoutMode.Compact
}
