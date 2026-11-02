package co.zw.nissangtr.management.pos.offline

import java.nio.CharBuffer

/**
 * Preserve the legacy 4.5.4 driver's CharArray -> UTF-8 passphrase conversion.
 * Using the original random bytes directly would lock out existing encrypted caches/outboxes.
 */
internal fun legacySqlCipherPassword(wrappedBytes: ByteArray): ByteArray {
    val chars = CharArray(wrappedBytes.size) { (wrappedBytes[it].toInt() and 0xff).toChar() }
    val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
    return ByteArray(encoded.remaining()).also { result ->
        encoded.get(result)
        chars.fill('\u0000')
        if (encoded.hasArray()) encoded.array().fill(0)
    }
}
