package co.zw.nissangtr.pos.till

/**
 * Idle lock — returns to login chrome without process death.
 * [IDLE_MS] of no interaction → locked; unlock via staff reauth callback.
 */
object IdleLockController {
    const val IDLE_MS: Long = 5 * 60 * 1000L

    fun shouldLock(lastInteractionEpochMs: Long, nowEpochMs: Long, idleMs: Long = IDLE_MS): Boolean =
        nowEpochMs - lastInteractionEpochMs >= idleMs
}
