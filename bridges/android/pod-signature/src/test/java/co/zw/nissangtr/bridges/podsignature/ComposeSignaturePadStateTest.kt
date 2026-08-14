package co.zw.nissangtr.bridges.podsignature

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the inline pad is not a stub: strokes are recorded and clearable.
 * Rasterization ([ComposeSignaturePadState.toPngFile]) needs Android Bitmap (device/emulator).
 */
class ComposeSignaturePadStateTest {

    @Test
    fun recordsStrokesAndReportsInk() {
        val state = ComposeSignaturePadState()
        assertFalse(state.hasInk)
        state.onSize(IntSize(200, 100))
        state.start(Offset(10f, 10f))
        state.drag(Offset(20f, 15f))
        state.drag(Offset(30f, 20f))
        state.end()
        assertTrue(state.hasInk)
        assertEquals(1, state.committedStrokes.size)
        assertEquals(3, state.committedStrokes[0].size)
    }

    @Test
    fun clearRemovesInk() {
        val state = ComposeSignaturePadState()
        state.start(Offset(1f, 1f))
        state.end()
        assertTrue(state.hasInk)
        state.clear()
        assertFalse(state.hasInk)
        assertTrue(state.committedStrokes.isEmpty())
    }
}
