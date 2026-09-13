package co.zw.nissangtr.pos.ui.harness

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/**
 * Canonical reference sizes defined by Blueprint §11.2.
 * Every visual gate runs across these 5 sizes.
 */
enum class ReferenceSize(val dpSize: DpSize, val label: String) {
    ExpandedCanonical(DpSize(1280.dp, 800.dp), "1280x800_expanded_canonical"),
    ExpandedLegacy(DpSize(1024.dp, 768.dp), "1024x768_expanded_legacy"),
    TabletPortrait(DpSize(800.dp, 1280.dp), "800x1280_tablet_portrait"),
    CompactStandard(DpSize(412.dp, 915.dp), "412x915_compact_standard"),
    CompactMinimum(DpSize(360.dp, 800.dp), "360x800_compact_minimum")
}
