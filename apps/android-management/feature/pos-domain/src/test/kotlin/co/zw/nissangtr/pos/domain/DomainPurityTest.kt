package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.result.PosResult
import co.zw.nissangtr.pos.domain.result.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies ARCH-03: pos-domain has pure Kotlin logic and zero Android dependencies.
 */
class DomainPurityTest {

    @Test
    fun `pos-domain classpath contains no android dependencies`() {
        val androidClasses = listOf(
            "android.content.Context",
            "android.os.Bundle",
            "androidx.compose.ui.Modifier",
            "androidx.lifecycle.ViewModel"
        )
        for (className in androidClasses) {
            val loaded = try {
                Class.forName(className)
                true
            } catch (e: ClassNotFoundException) {
                false
            }
            assertFalse("ARCH-03 violation: $className should NOT be on pos-domain classpath", loaded)
        }
    }

    @Test
    fun `pos result ok and err work as pure value types`() {
        val okResult: PosResult<String> = PosResult.Ok("success")
        assertTrue(okResult.isOk)
        assertFalse(okResult.isErr)
        assertEquals("success", okResult.getOrNull())

        val mapped = okResult.map { it.uppercase() }
        assertEquals("SUCCESS", mapped.getOrNull())

        val errResult: PosResult<String> = PosResult.Err(PosError.Transient(retryable = true))
        assertTrue(errResult.isErr)
        assertEquals(null, errResult.getOrNull())
        assertTrue(errResult.errorOrNull() is PosError.Transient)
    }
}
