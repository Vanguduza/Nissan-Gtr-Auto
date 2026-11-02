package co.zw.nissangtr.management.pos.offline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/** Native proof against an encrypted fixture created by the independent legacy 4.5.4 CLI. */
@RunWith(AndroidJUnit4::class)
class SqlCipherUpgradeCompatibilityTest {
    @Test fun wrongKeyPreservesTheEncryptedOutboxForRecovery() {
        System.loadLibrary("sqlcipher")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "wrong-key-preservation.db")
        val original = instrumentation.context.assets.open("sqlcipher-4.5.4-cache.db").use { it.readBytes() }
        file.writeBytes(original)
        try {
            assertThrows(android.database.sqlite.SQLiteException::class.java) {
                SQLiteDatabase.openDatabase(file.path, byteArrayOf(1, 2, 3), null,
                    SQLiteDatabase.OPEN_READWRITE, PreserveOfflineDatabaseErrorHandler, null).use { db ->
                    db.rawQuery("SELECT * FROM pending_sales", emptyArray<String>()).use { it.moveToFirst() }
                }
            }
            assertArrayEquals(original, file.readBytes())
        } finally {
            file.delete()
        }
    }

    @Test fun legacyCacheAndPendingSaleSurviveModernDriverReadWriteReopen() {
        System.loadLibrary("sqlcipher")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "legacy-sqlcipher-compatibility.db")
        instrumentation.context.assets.open("sqlcipher-4.5.4-cache.db").use { source ->
            file.outputStream().use { source.copyTo(it) }
        }
        val password = legacySqlCipherPassword(byteArrayOf(0x41, 0x80.toByte(), 0xff.toByte(), 0xc3.toByte(), 0xa9.toByte()))
        try {
            SQLiteDatabase.openDatabase(file.path, password, null, SQLiteDatabase.OPEN_READWRITE, null).use { db ->
                assertEquals(7, db.version)
                db.rawQuery("SELECT payload_json FROM pending_sales WHERE local_id='sale-before-upgrade'", emptyArray<String>()).use { row ->
                    row.moveToFirst()
                    assertEquals("{\"cart\":\"retained\",\"amount_minor\":1250}", row.getString(0))
                }
                db.rawQuery("SELECT description FROM catalog_items WHERE stock_item_id='part-before-upgrade'", emptyArray<String>()).use { row ->
                    row.moveToFirst()
                    assertEquals("Retained fixture part", row.getString(0))
                }
                db.execSQL("INSERT INTO pending_sales(local_id,payload_json) VALUES('sale-after-upgrade','{}')")
            }
            SQLiteDatabase.openDatabase(file.path, password, null, SQLiteDatabase.OPEN_READWRITE, null).use { db ->
                db.rawQuery("SELECT count(*) FROM pending_sales", emptyArray<String>()).use { rows ->
                    rows.moveToFirst()
                    assertEquals(2, rows.getInt(0))
                }
                db.rawQuery("PRAGMA integrity_check", emptyArray<String>()).use { rows ->
                    rows.moveToFirst()
                    assertEquals("ok", rows.getString(0))
                }
            }
        } finally {
            password.fill(0)
            file.delete()
        }
    }
}
