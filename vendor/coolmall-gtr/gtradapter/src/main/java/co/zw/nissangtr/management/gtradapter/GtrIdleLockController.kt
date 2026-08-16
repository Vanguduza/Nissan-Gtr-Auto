package co.zw.nissangtr.management.gtradapter

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-app idle lock parity with web `STAFF_IDLE_LOCK_MS` (3 minutes).
 * UI layers call [onUserInteraction] and observe [locked]; unlock via password reauth.
 */
class GtrIdleLockController(
    private val idleMs: Long = STAFF_IDLE_LOCK_MS,
) {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    @Volatile
    private var lastInteractionMs: Long = System.currentTimeMillis()

    @Volatile
    private var enabled: Boolean = false

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) {
            _locked.value = false
            lastInteractionMs = System.currentTimeMillis()
        }
    }

    fun onUserInteraction() {
        if (!enabled) return
        if (_locked.value) return
        lastInteractionMs = System.currentTimeMillis()
    }

    /** Call periodically (e.g. on resume / composition) to flip lock when idle. */
    fun tick(nowMs: Long = System.currentTimeMillis()) {
        if (!enabled || _locked.value) return
        if (nowMs - lastInteractionMs >= idleMs) {
            _locked.value = true
        }
    }

    fun unlock() {
        _locked.value = false
        lastInteractionMs = System.currentTimeMillis()
    }

    companion object {
        const val STAFF_IDLE_LOCK_MINUTES = 3L
        const val STAFF_IDLE_LOCK_MS = STAFF_IDLE_LOCK_MINUTES * 60_000L
    }
}
