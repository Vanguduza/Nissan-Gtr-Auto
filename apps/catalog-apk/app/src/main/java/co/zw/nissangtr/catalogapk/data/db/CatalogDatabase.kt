package co.zw.nissangtr.catalogapk.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import co.zw.nissangtr.catalogapk.data.model.JobEntity
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity
import co.zw.nissangtr.catalogapk.data.model.SupabaseProjectEntity

@Database(
    entities = [
        SiteProfileEntity::class,
        JobEntity::class,
        SupabaseProjectEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class CatalogDatabase : RoomDatabase() {
    abstract fun siteProfileDao(): SiteProfileDao
    abstract fun jobDao(): JobDao
    abstract fun supabaseProjectDao(): SupabaseProjectDao

    companion object {
        @Volatile
        private var instance: CatalogDatabase? = null

        fun get(context: Context): CatalogDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    CatalogDatabase::class.java,
                    "catalog_apk.db",
                )
                    .fallbackToDestructiveMigration()
                    .enableMultiInstanceInvalidation()
                    .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                    .build()
                    .also { instance = it }
            }
    }
}
