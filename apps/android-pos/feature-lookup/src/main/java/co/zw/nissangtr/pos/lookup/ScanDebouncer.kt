package co.zw.nissangtr.pos.lookup

import co.zw.nissangtr.pos.api.OemNormalize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ~400ms scan/search debounce + OEM normalize before dispatch.
 */
class ScanDebouncer(
    private val scope: CoroutineScope,
    private val delayMs: Long = 400L,
    private val onReady: (normalized: String) -> Unit,
) {
    private var job: Job? = null

    fun onInput(raw: String) {
        job?.cancel()
        val normalized = OemNormalize.normalize(raw)
        if (normalized.isEmpty()) {
            onReady("")
            return
        }
        job = scope.launch {
            delay(delayMs)
            onReady(normalized)
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
    }
}

/** Bridge scan stub — hardware agent wires later; emits normalized OEM. */
object BridgeScanStub {
    fun onScanPayload(raw: String): String = OemNormalize.normalize(raw)
}
