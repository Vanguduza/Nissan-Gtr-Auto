package co.zw.nissangtr.pos.pay

import co.zw.nissangtr.pos.api.MoneyCents
import co.zw.nissangtr.pos.api.TenderMode
import co.zw.nissangtr.pos.api.TenderRpcLine

/**
 * Pure Kotlin split-tender math in **integer minor units** (cents). Plan §6.2 / §16.5.
 *
 * Equal-split policy: floor(due/n) on each line; **remainder cents on the last line**
 * so Σ applied == due exactly (e.g. 1000¢ / 3 → 333 + 333 + 334).
 */
object TenderAllocator {

    fun splitEqually(dueCents: Long, modes: List<TenderMode>): List<TenderDraft> {
        require(dueCents >= 0L) { "dueCents must be >= 0" }
        require(modes.isNotEmpty()) { "modes required" }
        val n = modes.size
        val base = dueCents / n
        val rem = dueCents % n
        return modes.mapIndexed { index, mode ->
            val slice = if (index == n - 1) base + rem else base
            TenderDraft(
                mode = mode,
                tenderedCents = slice,
                railSettled = !mode.isLiveRail,
            )
        }
    }

    /** Next draft = current remaining after [existing] allocations. */
    fun fillRest(
        dueCents: Long,
        existing: List<TenderDraft>,
        mode: TenderMode,
        railSettled: Boolean = !mode.isLiveRail,
    ): TenderDraft {
        val snap = allocate(dueCents, existing)
        return TenderDraft(
            mode = mode,
            tenderedCents = snap.remainingCents.coerceAtLeast(0L),
            railSettled = railSettled,
        )
    }

    fun allocate(dueCents: Long, drafts: List<TenderDraft>): AllocationSnapshot {
        require(dueCents >= 0L) { "dueCents must be >= 0" }
        var remaining = dueCents
        val out = ArrayList<AllocatedLine>(drafts.size)
        var cashTendered = 0L
        var cashApplied = 0L

        for (draft in drafts) {
            require(draft.tenderedCents >= 0L) { "tenderedCents must be >= 0" }
            val applied = when (draft.mode) {
                TenderMode.CASH -> minOf(draft.tenderedCents, remaining)
                else -> {
                    // Exact slice — clamp so over-allocation does not go negative remaining.
                    minOf(draft.tenderedCents, remaining)
                }
            }
            if (draft.mode == TenderMode.CASH) {
                cashTendered += draft.tenderedCents
                cashApplied += applied
            }
            remaining -= applied
            out += AllocatedLine(
                mode = draft.mode,
                tenderedCents = draft.tenderedCents,
                appliedCents = applied,
                railSettled = draft.railSettled,
            )
        }

        val change = (cashTendered - cashApplied).coerceAtLeast(0L)
        val railsSettled = out.all { !it.mode.isLiveRail || it.appliedCents == 0L || it.railSettled }
        val positiveOk = out.isNotEmpty() && out.all { it.appliedCents > 0L }
        // Confirm math: remaining must be 0. Live rails settle on confirm path
        // ([LiveRailSettler]); unsettled rails do not block the button here.
        val confirmEnabled = remaining == 0L && (dueCents == 0L || positiveOk)

        return AllocationSnapshot(
            dueCents = dueCents,
            lines = out,
            remainingCents = remaining,
            changeCents = change,
            confirmEnabled = confirmEnabled,
            railsSettled = railsSettled,
        )
    }

    /** RPC tenders: applied majors only (change never posted). */
    fun toRpcLines(snapshot: AllocationSnapshot, currency: String): List<TenderRpcLine> {
        require(currency == "USD" || currency == "ZIG") { "currency must be USD|ZIG" }
        return snapshot.lines
            .filter { it.appliedCents > 0L }
            .map {
                TenderRpcLine(
                    tender = it.mode.rpcValue,
                    amount = MoneyCents.centsToMajorString(it.appliedCents),
                    currency = currency,
                )
            }
    }
}

data class TenderDraft(
    val mode: TenderMode,
    /** Cash: handed over (may exceed applied). Non-cash: exact slice. */
    val tenderedCents: Long,
    /** Live rails (EcoCash/Paynow/ContiPay) must settle before confirm. */
    val railSettled: Boolean = true,
)

data class AllocatedLine(
    val mode: TenderMode,
    val tenderedCents: Long,
    val appliedCents: Long,
    val railSettled: Boolean,
)

data class AllocationSnapshot(
    val dueCents: Long,
    val lines: List<AllocatedLine>,
    val remainingCents: Long,
    val changeCents: Long,
    val confirmEnabled: Boolean,
    val railsSettled: Boolean = true,
) {
    val appliedSumCents: Long get() = lines.sumOf { it.appliedCents }
}
