package co.zw.nissangtr.catalogapk.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import co.zw.nissangtr.catalogapk.data.model.JobEntity
import co.zw.nissangtr.catalogapk.data.model.SiteProfileEntity
import co.zw.nissangtr.catalogapk.data.model.SupabaseProjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SiteProfileDao {
    @Query("SELECT * FROM site_profiles ORDER BY displayName ASC")
    fun observeAll(): Flow<List<SiteProfileEntity>>

    @Query("SELECT * FROM site_profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): SiteProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: SiteProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<SiteProfileEntity>)

    @Query("DELETE FROM site_profiles WHERE id = :id AND isPreset = 0")
    suspend fun deleteCustom(id: String)
}

@Dao
interface JobDao {
    @Query("SELECT * FROM jobs ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<JobEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(job: JobEntity)

    @Update
    suspend fun update(job: JobEntity)

    @Query("UPDATE jobs SET desiredState = :desiredState, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateDesiredState(id: String, desiredState: String, updatedAt: Long)

    @Query(
        "SELECT * FROM jobs WHERE desiredState = :desiredState AND status IN (:statuses)",
    )
    suspend fun findByDesiredStateAndStatuses(
        desiredState: String,
        statuses: List<String>,
    ): List<JobEntity>
}

@Dao
interface SupabaseProjectDao {
    @Query("SELECT * FROM supabase_projects ORDER BY name ASC")
    fun observeAll(): Flow<List<SupabaseProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(project: SupabaseProjectEntity)

    @Query("DELETE FROM supabase_projects WHERE id = :id")
    suspend fun delete(id: String)
}
