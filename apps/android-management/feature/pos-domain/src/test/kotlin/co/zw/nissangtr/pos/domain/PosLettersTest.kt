package co.zw.nissangtr.pos.domain

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.BusinessProfile
import co.zw.nissangtr.pos.domain.model.LetterSource
import co.zw.nissangtr.pos.domain.state.LetterEffect
import co.zw.nissangtr.pos.domain.state.LetterEvent
import co.zw.nissangtr.pos.domain.state.LetterIntent
import co.zw.nissangtr.pos.domain.state.PosFeedback
import co.zw.nissangtr.pos.domain.state.PosNotice
import co.zw.nissangtr.pos.domain.state.PosState
import co.zw.nissangtr.pos.domain.state.reduce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PosLettersTest {
    private val src = LetterSource.cardTerminal("att-1")

    @Test fun onlyEcoCashPaynowContiPayIntentsTakeALetter() {
        assertEquals("ecocash", LetterSource.provider("ecocash", "i1")?.kind)
        assertNull(LetterSource.provider("cash", "i1"))
        assertNull(LetterSource.provider("paynow", null))
    }

    @Test fun issuedOpensTheLetterAndRefreshesTheList() {
        val r = reduce(PosState(lettersBusy = true), LetterEvent.Issued(src, "L1"))
        assertEquals(PosFeedback.Notice(PosNotice.LetterIssued), r.state.feedback)
        assertEquals(listOf(LetterEffect.Open("L1"), LetterEffect.Load(src)), r.effects)
    }

    @Test fun issueIsOnlineOnlyAndNotDoubled() {
        assertTrue(reduce(PosState(online = false), LetterIntent.Issue(src, null)).effects.isEmpty())
        assertTrue(reduce(PosState(lettersBusy = true), LetterIntent.Issue(src, null)).effects.isEmpty())
        assertEquals(LetterEffect.Issue(src, "x"), reduce(PosState(), LetterIntent.Issue(src, " x ")).effects.single())
    }

    @Test fun profileNeedsNamesAndDomain() {
        val bad = BusinessProfile("", "GTR", "d", null, null, null, null, null, null, null)
        val r = reduce(PosState(), LetterIntent.SaveProfile(bad))
        assertTrue((r.state.feedback as PosFeedback.Failure).error is PosError.Input)
        assertTrue(reduce(PosState(), LetterIntent.SaveProfile(bad.copy(legalName = "GTR Ltd"))).effects.single() is LetterEffect.SaveProfile)
    }
}
