package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.error.PosError
import co.zw.nissangtr.pos.domain.model.BusinessProfile
import co.zw.nissangtr.pos.domain.model.LetterSource
import co.zw.nissangtr.pos.domain.model.MySignature
import co.zw.nissangtr.pos.domain.model.PaymentLetter
import co.zw.nissangtr.pos.domain.model.PaymentLetterSummary

/*
 * Payment letters (phase 8). Recovery shows the letters issued for each payment and lets a signed-in
 * manager, finance or admin issue one; it opens at once for printing. Settings holds the signer's own
 * signature and (admin) the business details printed at the top.
 */

sealed interface LetterIntent : PosSaleIntent {
    data class Load(val source: LetterSource) : LetterIntent
    data class Issue(val source: LetterSource, val notes: String?) : LetterIntent
    data class Open(val letterId: String) : LetterIntent
    data object Close : LetterIntent
    data object LoadSettings : LetterIntent
    data class SaveSignature(val png: ByteArray) : LetterIntent {
        override fun equals(other: Any?) = other is SaveSignature && other.png.contentEquals(png)
        override fun hashCode() = png.contentHashCode()
    }
    data class SaveProfile(val profile: BusinessProfile) : LetterIntent
}

sealed interface LetterEvent : PosSaleEvent {
    data class Loaded(val source: LetterSource, val letters: List<PaymentLetterSummary>) : LetterEvent
    data class Issued(val source: LetterSource, val letterId: String) : LetterEvent
    data class Opened(val letter: PaymentLetter) : LetterEvent
    data class SignatureLoaded(val signature: MySignature?, val saved: Boolean) : LetterEvent
    data class ProfileLoaded(val profile: BusinessProfile?, val saved: Boolean) : LetterEvent
    data class Failed(val error: PosError) : LetterEvent
}

sealed interface LetterEffect : PosSaleEffect {
    data class Load(val source: LetterSource) : LetterEffect
    data class Issue(val source: LetterSource, val notes: String?) : LetterEffect
    data class Open(val letterId: String) : LetterEffect
    data object LoadSettings : LetterEffect
    data class SaveSignature(val png: ByteArray) : LetterEffect {
        override fun equals(other: Any?) = other is SaveSignature && other.png.contentEquals(png)
        override fun hashCode() = png.contentHashCode()
    }
    data class SaveProfile(val profile: BusinessProfile) : LetterEffect
}

internal fun reduceLetterIntent(state: PosState, intent: LetterIntent): Reduction = when (intent) {
    is LetterIntent.Load -> Reduction(state, listOf(LetterEffect.Load(intent.source)))
    is LetterIntent.Issue -> if (state.lettersBusy) Reduction(state)
    else Reduction(state.copy(lettersBusy = true, feedback = null), listOf(LetterEffect.Issue(intent.source, intent.notes?.trim()?.ifEmpty { null })))
    is LetterIntent.Open -> Reduction(state.copy(lettersBusy = true), listOf(LetterEffect.Open(intent.letterId)))
    LetterIntent.Close -> Reduction(state.copy(openLetter = null))
    LetterIntent.LoadSettings -> Reduction(state, listOf(LetterEffect.LoadSettings))
    is LetterIntent.SaveSignature -> when {
        state.lettersBusy -> Reduction(state)
        intent.png.isEmpty() -> Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("signature", "required"))))
        else -> Reduction(state.copy(lettersBusy = true, feedback = null), listOf(LetterEffect.SaveSignature(intent.png)))
    }
    is LetterIntent.SaveProfile -> {
        val p = intent.profile
        if (p.legalName.isBlank() || p.tradingName.isBlank() || p.domain.isBlank()) Reduction(state.copy(feedback = PosFeedback.Failure(PosError.Input("business", "required"))))
        else Reduction(state.copy(lettersBusy = true, feedback = null), listOf(LetterEffect.SaveProfile(p)))
    }
}

internal fun reduceLetterEvent(state: PosState, event: LetterEvent): Reduction = when (event) {
    is LetterEvent.Loaded -> Reduction(state.copy(letters = state.letters + (event.source.key to event.letters)))
    // Issued: show it at once for printing, and refresh that payment's list.
    is LetterEvent.Issued -> Reduction(
        state.copy(lettersBusy = false, feedback = PosFeedback.Notice(PosNotice.LetterIssued)),
        listOf(LetterEffect.Open(event.letterId), LetterEffect.Load(event.source)),
    )
    is LetterEvent.Opened -> Reduction(state.copy(lettersBusy = false, openLetter = event.letter))
    is LetterEvent.SignatureLoaded -> Reduction(
        state.copy(lettersBusy = false, mySignature = event.signature, feedback = if (event.saved) PosFeedback.Notice(PosNotice.SignatureSaved) else state.feedback),
    )
    is LetterEvent.ProfileLoaded -> Reduction(
        state.copy(lettersBusy = false, businessProfile = event.profile, feedback = if (event.saved) PosFeedback.Notice(PosNotice.ProfileSaved) else state.feedback),
    )
    is LetterEvent.Failed -> Reduction(state.copy(lettersBusy = false, feedback = PosFeedback.Failure(event.error)))
}
