package co.zw.nissangtr.management.pos.offline

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LegacyPassphraseEncodingTest {
    @Test fun preservesHighBytesFromExistingKeystoreWrappedPasswords() {
        val original = byteArrayOf(0x41, 0x80.toByte(), 0xff.toByte(), 0xc3.toByte(), 0xa9.toByte())
        val encoded = legacySqlCipherPassword(original)
        assertArrayEquals(byteArrayOf(0x41, 0xc2.toByte(), 0x80.toByte(), 0xc3.toByte(), 0xbf.toByte(), 0xc3.toByte(), 0x83.toByte(), 0xc2.toByte(), 0xa9.toByte()), encoded)
        assertFalse(original.contentEquals(encoded))
    }

    @Test fun preservesEmbeddedZeroBytesAndAscii() {
        assertArrayEquals(byteArrayOf(65, 0, 66), legacySqlCipherPassword(byteArrayOf(65, 0, 66)))
    }
}
