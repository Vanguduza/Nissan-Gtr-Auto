package co.zw.nissangtr.pos.ui.harness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Verifies CERT-01 and CERT-04: Roborazzi screenshot test harness
 * executing on the JVM across the 5 reference sizes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BaselineScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @Config(qualifiers = "+w1280dp-h800dp")
    fun `baseline expanded canonical 1280x800`() {
        captureBaseline(ReferenceSize.ExpandedCanonical)
    }

    @Test
    @Config(qualifiers = "+w1024dp-h768dp")
    fun `baseline expanded legacy 1024x768`() {
        captureBaseline(ReferenceSize.ExpandedLegacy)
    }

    @Test
    @Config(qualifiers = "+w800dp-h1280dp")
    fun `baseline tablet portrait 800x1280`() {
        captureBaseline(ReferenceSize.TabletPortrait)
    }

    @Test
    @Config(qualifiers = "+w412dp-h915dp")
    fun `baseline compact standard 412x915`() {
        captureBaseline(ReferenceSize.CompactStandard)
    }

    @Test
    @Config(qualifiers = "+w360dp-h800dp")
    fun `baseline compact minimum 360x800`() {
        captureBaseline(ReferenceSize.CompactMinimum)
    }

    private fun captureBaseline(size: ReferenceSize) {
        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF4F5F7)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Nissan GTR Auto POS — ${size.label}",
                    color = Color(0xFF12151C)
                )
            }
        }
        composeTestRule.onRoot().captureRoboImage("build/outputs/roborazzi/${size.label}.png")
    }
}
