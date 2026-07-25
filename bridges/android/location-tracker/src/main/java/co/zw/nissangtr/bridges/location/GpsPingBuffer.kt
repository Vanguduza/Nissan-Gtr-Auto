package co.zw.nissangtr.bridges.location

import java.util.ArrayDeque

/**
 * Ephemeral in-memory ring buffer for location pings while the network is down.
 *
 * **Not durable** — process death / force-stop clears this. The delivery app must
 * persist to SQLite / Room / WorkManager for a true offline queue, then flush via
 * `ingest_delivery_location` on reconnect (respect ~5s rate limit).
 *
 * Thread-safe for concurrent offer/drain from the Fused callback + app coroutines.
 */
class GpsPingBuffer(
    capacity: Int = DEFAULT_CAPACITY,
) {
    private val max = capacity.coerceAtLeast(1)
    private val deque = ArrayDeque<GpsCoordinate>(max)

    @Synchronized
    fun offer(coord: GpsCoordinate) {
        if (deque.size >= max) {
            deque.removeFirst()
        }
        deque.addLast(coord)
    }

    /** Snapshot without clearing. */
    @Synchronized
    fun peek(): List<GpsCoordinate> = deque.toList()

    /** Drain all buffered fixes (FIFO). */
    @Synchronized
    fun drain(): List<GpsCoordinate> {
        val out = deque.toList()
        deque.clear()
        return out
    }

    @Synchronized
    fun size(): Int = deque.size

    @Synchronized
    fun clear() {
        deque.clear()
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 64
    }
}
