package co.zw.nissangtr.management.pos

/**
 * CoolMall-inspired tablet dual-pane breakpoint — catalog left / cart right.
 * Below this width the skeleton stacks (phone fallback).
 */
object PosDualPaneLayout {
    const val MIN_WIDTH_DP: Int = 700

    fun useTwoPane(maxWidthDp: Float): Boolean = maxWidthDp >= MIN_WIDTH_DP
}
