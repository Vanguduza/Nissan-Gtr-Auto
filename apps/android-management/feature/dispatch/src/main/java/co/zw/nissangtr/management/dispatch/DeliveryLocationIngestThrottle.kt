package co.zw.nissangtr.management.dispatch

import java.util.concurrent.atomic.AtomicLong

/**
 * Client-side gate before [co.zw.nissangtr.management.rpc.RpcNames.INGEST_DELIVERY_LOCATION].
 * Server also enforces ~5s; this avoids noisy RPC failures from FusedLocation bursts.
 */
class DeliveryLocationIngestThrottle(
    private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    private val lastAcceptedAtMs = AtomicLong(0L)

    /** Returns true when this fix may be ingested (and records the accept time). */
    fun tryAccept(): Boolean {
        val now = clockMs()
        while (true) {
            val last = lastAcceptedAtMs.get()
            if (last != 0L && now - last < minIntervalMs) return false
            if (lastAcceptedAtMs.compareAndSet(last, now)) return true
        }
    }

    fun reset() {
        lastAcceptedAtMs.set(0L)
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS: Long = 5_000L
    }
}
