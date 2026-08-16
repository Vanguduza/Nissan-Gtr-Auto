package co.zw.nissangtr.management.gtradapter.di

import co.zw.nissangtr.management.gtradapter.FakeGtrMyAccountAdapter
import co.zw.nissangtr.management.gtradapter.FakeGtrPasswordAdapter
import co.zw.nissangtr.management.gtradapter.FakeGtrStaffAuthAdapter
import co.zw.nissangtr.management.gtradapter.FakeGtrStaffHubAdapter
import co.zw.nissangtr.management.gtradapter.FakeGtrWarehouseAdapter
import co.zw.nissangtr.management.gtradapter.GtrIdleLockController
import co.zw.nissangtr.management.gtradapter.GtrMyAccountAdapter
import co.zw.nissangtr.management.gtradapter.GtrPasswordAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffAuthAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffHubAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffSession
import co.zw.nissangtr.management.gtradapter.GtrSupabaseConfig
import co.zw.nissangtr.management.gtradapter.GtrSupabaseRuntime
import co.zw.nissangtr.management.gtradapter.GtrWarehouseAdapter
import co.zw.nissangtr.management.gtradapter.FakeGtrStaffOpsAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffOpsAdapter
import co.zw.nissangtr.management.gtradapter.live.LiveGtrStaffOpsAdapter
import co.zw.nissangtr.management.gtradapter.live.LiveGtrMyAccountAdapter
import co.zw.nissangtr.management.gtradapter.live.LiveGtrPasswordAdapter
import co.zw.nissangtr.management.gtradapter.live.LiveGtrStaffAuthAdapter
import co.zw.nissangtr.management.gtradapter.live.LiveGtrWarehouseAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Fake when URL/anon missing or `rpc.forceFake=true`; Live supabase-kt otherwise.
 * [GtrSupabaseConfig] is provided by the app module from BuildConfig.
 */
@Module
@InstallIn(SingletonComponent::class)
object GtrAdapterModule {
    @Provides
    @Singleton
    fun provideGtrStaffSession(): GtrStaffSession = GtrStaffSession()

    @Provides
    @Singleton
    fun provideGtrSupabaseRuntime(config: GtrSupabaseConfig): GtrSupabaseRuntime =
        GtrSupabaseRuntime.from(config)

    @Provides
    @Singleton
    fun provideGtrIdleLockController(): GtrIdleLockController = GtrIdleLockController()

    @Provides
    @Singleton
    fun provideGtrStaffAuthAdapter(
        session: GtrStaffSession,
        runtime: GtrSupabaseRuntime,
    ): GtrStaffAuthAdapter = when (runtime) {
        is GtrSupabaseRuntime.Live -> LiveGtrStaffAuthAdapter(runtime.client, session)
        GtrSupabaseRuntime.Fake -> FakeGtrStaffAuthAdapter(session)
    }

    @Provides
    @Singleton
    fun provideGtrStaffHubAdapter(): GtrStaffHubAdapter = FakeGtrStaffHubAdapter()

    @Provides
    @Singleton
    fun provideGtrPasswordAdapter(
        session: GtrStaffSession,
        runtime: GtrSupabaseRuntime,
    ): GtrPasswordAdapter = when (runtime) {
        is GtrSupabaseRuntime.Live -> LiveGtrPasswordAdapter(runtime.client, session)
        GtrSupabaseRuntime.Fake -> FakeGtrPasswordAdapter(session)
    }

    @Provides
    @Singleton
    fun provideGtrWarehouseAdapter(runtime: GtrSupabaseRuntime): GtrWarehouseAdapter =
        when (runtime) {
            is GtrSupabaseRuntime.Live -> LiveGtrWarehouseAdapter(runtime.client)
            GtrSupabaseRuntime.Fake -> FakeGtrWarehouseAdapter()
        }

    @Provides
    @Singleton
    fun provideGtrMyAccountAdapter(
        session: GtrStaffSession,
        runtime: GtrSupabaseRuntime,
    ): GtrMyAccountAdapter = when (runtime) {
        is GtrSupabaseRuntime.Live -> LiveGtrMyAccountAdapter(runtime.client)
        GtrSupabaseRuntime.Fake -> FakeGtrMyAccountAdapter(session)
    }

    @Provides
    @Singleton
    fun provideGtrStaffOpsAdapter(runtime: GtrSupabaseRuntime): GtrStaffOpsAdapter =
        when (runtime) {
            is GtrSupabaseRuntime.Live -> LiveGtrStaffOpsAdapter(runtime.client)
            GtrSupabaseRuntime.Fake -> FakeGtrStaffOpsAdapter()
        }
}
