package co.zw.nissangtr.pos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PosShellViewModelHelpersTest {
    @Test
    fun displayNameFromEmailFormatsLocalPart() {
        assertEquals(
            "Admin",
            PosShellViewModel.displayNameFromEmail("admin@gtr.local"),
        )
        assertEquals(
            "T Moyo",
            PosShellViewModel.displayNameFromEmail("t.moyo@nissangtr.co.zw"),
        )
        assertNull(PosShellViewModel.displayNameFromEmail(null))
        assertNull(PosShellViewModel.displayNameFromEmail("  "))
    }
}
