package co.zw.nissangtr.pos.ui.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.zw.nissangtr.pos.design.theme.PosTheme
import co.zw.nissangtr.pos.design.theme.PosWindowClass
import co.zw.nissangtr.pos.domain.model.CurrencyCode
import co.zw.nissangtr.pos.domain.model.Money
import co.zw.nissangtr.pos.domain.model.WaitingApproval
import co.zw.nissangtr.pos.domain.state.ApprovalsIntent
import co.zw.nissangtr.pos.domain.state.PosDestination
import co.zw.nissangtr.pos.domain.state.PosIntent
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.ui.fakes.PosFixtures
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDateTime

/** Approvals on the tablet: the header pill while something waits, and the list it opens. */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class PosApprovalsScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val now = LocalDateTime.of(2026, 10, 1, 7, 42)
    private val waiting = listOf(
        WaitingApproval("card_unresolved", "a1", "Card payment to reconcile", "Machine gave no answer at Counter 1", true, "2026-10-01T05:30:00Z", Money.ofMajor(42.0, CurrencyCode.USD)),
        WaitingApproval("till_variance", "t1", "Till difference: Rudo", "Counted USD 5.00 short", false, "2026-10-01T04:10:00Z", Money.ofMajor(-5.0, CurrencyCode.USD)),
        WaitingApproval("requisition", "r1", "Requisition: shop supplies", "Waiting for your approval", false, "2026-09-30T13:00:00Z", Money.ofMajor(120.0, CurrencyCode.USD)),
    )

    private fun show(state: PosState, width: Dp, height: Dp, dispatched: MutableList<PosIntent> = mutableListOf()) {
        compose.setContent {
            PosTheme(windowClass = PosWindowClass.derive(width, height)) {
                PosHomeScreen(state = state, now = now, dispatch = { dispatched += it },
                    host = PosHostActions(onScan = {}, onPrint = { _, _ -> }, onStaffPortal = {}, onKioskSettings = {}))
            }
        }
        compose.waitForIdle()
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun headerPill() {
        val dispatched = mutableListOf<PosIntent>()
        show(PosFixtures.homeWithSale.copy(approvals = waiting), 1280.dp, 800.dp, dispatched)
        captureScreenRoboImage("build/outputs/roborazzi/screen_approvals_pill.png")
        compose.onNodeWithText("3 to approve").performClick()
        assertTrue(dispatched.contains(ApprovalsIntent.Open))
    }

    @Test @Config(qualifiers = "w1280dp-h800dp-land-mdpi")
    fun inboxOpen() {
        val dispatched = mutableListOf<PosIntent>()
        show(PosFixtures.homeWithSale.copy(approvals = waiting, approvalsOpen = true), 1280.dp, 800.dp, dispatched)
        captureScreenRoboImage("build/outputs/roborazzi/screen_approvals_open.png")
        // A till difference is decided on the tablet: tapping it closes the list and opens the Till.
        compose.onNodeWithText("Till difference: Rudo").performClick()
        assertTrue(dispatched.contains(ApprovalsIntent.Close))
        assertTrue(dispatched.contains(PosIntent.Navigate(PosDestination.Till)))
        // A requisition is decided on the web: the row says so.
        compose.onNodeWithText("decide on the web", substring = true).assertExists()
    }

    @Test @Config(qualifiers = "w412dp-h915dp-port-xxhdpi")
    fun inboxCompact() {
        show(PosFixtures.homeWithSale.copy(approvals = waiting, approvalsOpen = true), 412.dp, 915.dp)
        captureScreenRoboImage("build/outputs/roborazzi/screen_approvals_compact.png")
    }
}
