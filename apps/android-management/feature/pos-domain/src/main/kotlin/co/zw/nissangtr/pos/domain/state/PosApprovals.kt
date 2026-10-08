package co.zw.nissangtr.pos.domain.state

import co.zw.nissangtr.pos.domain.model.WaitingApproval

/*
 * Approvals on the tablet: the same list as the web Approvals page, behind a header button with a
 * count. Items the tablet decides open their screen (Till, Returns, Recovery, Orders); the rest say
 * where on the web they are decided.
 */

sealed interface ApprovalsIntent : PosSaleIntent {
    data object Load : ApprovalsIntent
    data object Open : ApprovalsIntent
    data object Close : ApprovalsIntent
}

sealed interface ApprovalsEvent : PosSaleEvent {
    data class Loaded(val items: List<WaitingApproval>) : ApprovalsEvent
}

sealed interface ApprovalsEffect : PosSaleEffect {
    data object Load : ApprovalsEffect
}

internal fun reduceApprovalsIntent(state: PosState, intent: ApprovalsIntent): Reduction = when (intent) {
    ApprovalsIntent.Load -> Reduction(state, listOf(ApprovalsEffect.Load))
    ApprovalsIntent.Open -> Reduction(state.copy(approvalsOpen = true), listOf(ApprovalsEffect.Load))
    ApprovalsIntent.Close -> Reduction(state.copy(approvalsOpen = false))
}

internal fun reduceApprovalsEvent(state: PosState, event: ApprovalsEvent): Reduction = when (event) {
    is ApprovalsEvent.Loaded -> Reduction(state.copy(approvals = event.items.sortedWith(compareByDescending<WaitingApproval> { it.urgent }.thenBy { it.waitingSince })))
}
