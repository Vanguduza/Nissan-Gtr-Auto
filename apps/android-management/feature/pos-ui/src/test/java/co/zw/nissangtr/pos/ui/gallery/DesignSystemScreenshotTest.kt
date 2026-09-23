package co.zw.nissangtr.pos.ui.gallery

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import co.zw.nissangtr.pos.design.primitives.GlassTreatment
import co.zw.nissangtr.pos.design.theme.PosDensity
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.design.theme.PosWindowClass
import co.zw.nissangtr.pos.ui.harness.ReferenceSize
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Screenshot certification suite for POS Design System (Blueprint §11.2, §11.3 / CERT-01, CERT-04).
 * Captures ComponentGallery at all 5 reference sizes, both color schemes, and both glass capability treatments.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignSystemScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // 1. Reference Size Matrix (Light Scheme, API 31+ Glass)
    @Test
    @Config(qualifiers = "+w1280dp-h800dp")
    fun `gallery_1280x800_expanded_light_blur`() {
        captureGallery(
            size = ReferenceSize.ExpandedCanonical,
            windowClass = PosWindowClass.Expanded,
            darkTheme = false,
            glass = GlassTreatment.Api31Blur,
            name = "gallery_1280x800_expanded_light_blur"
        )
    }

    @Test
    @Config(qualifiers = "+w1280dp-h800dp")
    fun `gallery_1280x800_expanded_light_fallback`() {
        captureGallery(
            size = ReferenceSize.ExpandedCanonical,
            windowClass = PosWindowClass.Expanded,
            darkTheme = false,
            glass = GlassTreatment.FallbackPre31,
            name = "gallery_1280x800_expanded_light_fallback"
        )
    }

    @Test
    @Config(qualifiers = "+w1280dp-h800dp")
    fun `gallery_1280x800_expanded_dark`() {
        captureGallery(
            size = ReferenceSize.ExpandedCanonical,
            windowClass = PosWindowClass.Expanded,
            darkTheme = true,
            glass = GlassTreatment.Api31Blur,
            name = "gallery_1280x800_expanded_dark"
        )
    }

    @Test
    @Config(qualifiers = "+w1024dp-h768dp")
    fun `gallery_1024x768_expanded_legacy`() {
        captureGallery(
            size = ReferenceSize.ExpandedLegacy,
            windowClass = PosWindowClass.Expanded,
            darkTheme = false,
            glass = GlassTreatment.Api31Blur,
            name = "gallery_1024x768_expanded_legacy"
        )
    }

    @Test
    @Config(qualifiers = "+w800dp-h1280dp")
    fun `gallery_800x1280_tablet_portrait`() {
        captureGallery(
            size = ReferenceSize.TabletPortrait,
            windowClass = PosWindowClass.Medium,
            darkTheme = false,
            glass = GlassTreatment.Api31Blur,
            name = "gallery_800x1280_tablet_portrait"
        )
    }

    @Test
    @Config(qualifiers = "+w412dp-h915dp")
    fun `gallery_412x915_compact_standard`() {
        captureGallery(
            size = ReferenceSize.CompactStandard,
            windowClass = PosWindowClass.CompactPortrait,
            darkTheme = false,
            glass = GlassTreatment.FallbackPre31,
            name = "gallery_412x915_compact_standard"
        )
    }

    @Test
    @Config(qualifiers = "+w360dp-h800dp")
    fun `gallery_360x800_compact_minimum`() {
        captureGallery(
            size = ReferenceSize.CompactMinimum,
            windowClass = PosWindowClass.CompactPortrait,
            darkTheme = false,
            glass = GlassTreatment.FallbackPre31,
            name = "gallery_360x800_compact_minimum"
        )
    }

    private fun captureGallery(
        size: ReferenceSize,
        windowClass: PosWindowClass,
        darkTheme: Boolean,
        glass: GlassTreatment,
        name: String,
    ) {
        composeTestRule.setContent {
            PosTheme(
                windowClass = windowClass,
                density = PosDensity.Operational,
                darkTheme = darkTheme,
            ) {
                ComponentGallery(glassTreatment = glass)
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}
