package co.zw.nissangtr.management.pos.offline

import android.database.sqlite.SQLiteException
import net.zetetic.database.DatabaseErrorHandler
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** Financial outboxes must survive corruption/key errors for explicit recovery, never auto-delete. */
internal object PreserveOfflineDatabaseErrorHandler : DatabaseErrorHandler {
    override fun onCorruption(dbObj: SQLiteDatabase, exception: SQLiteException) {
        throw exception
    }
}
