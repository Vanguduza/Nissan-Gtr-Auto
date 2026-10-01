package co.zw.nissangtr.pos.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.design.theme.PosWindowClass
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.ui.fakes.PosFixtures
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/** Phase 4 gate: Expanded home shell at the reference sizes (Blueprint §11.2, SHELL-14). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PosHomeScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = LocalDateTime.of(2026, 10, 1, 7, 42)

    private fun capture(name: String, width: Dp, height: Dp, state: PosState, dark: Boolean = false) {
        compose.setContent {
            PosTheme(windowClass = PosWindowClass.derive(width, height), darkTheme = dark) {
                PosHomeScreen(
                    state = state,
                    now = now,
                    dispatch = {},
                    host = PosHostActions(onPay = {}, onAddCustomer = {}, onPark = {}, onScan = {}),
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/home_$name.png")
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `home with sale 1280x800 expanded canonical`() = capture("1280x800_sale", 1280.dp, 800.dp, PosFixtures.homeWithSale)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `home empty 1280x800 expanded canonical`() = capture("1280x800_empty", 1280.dp, 800.dp, PosFixtures.homeEmpty)

    @Test
    @Config(qualifiers = "w1536dp-h1024dp-land-mdpi")
    fun `home with sale 1536x1024 benchmark frame`() = capture("1536x1024_sale", 1536.dp, 1024.dp, PosFixtures.homeWithSale)

    @Test
    @Config(qualifiers = "w1024dp-h768dp-land-mdpi")
    fun `home with sale 1024x768 expanded legacy`() = capture("1024x768_sale", 1024.dp, 768.dp, PosFixtures.homeWithSale)

    @Test
    @Config(qualifiers = "w800dp-h1280dp-port-mdpi")
    fun `home with sale 800x1280 tablet portrait`() = capture("800x1280_sale", 800.dp, 1280.dp, PosFixtures.homeWithSale)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun `home with sale 1280x800 dark scheme`() = capture("1280x800_sale_dark", 1280.dp, 800.dp, PosFixtures.homeWithSale, dark = true)
}
