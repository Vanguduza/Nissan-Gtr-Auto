package co.zw.nissangtr.management.gtradapter.di

import co.zw.nissangtr.management.gtradapter.FakeGtrPasswordAdapter
import co.zw.nissangtr.management.gtradapter.FakeGtrStaffAuthAdapter
import co.zw.nissangtr.management.gtradapter.FakeGtrStaffHubAdapter
import co.zw.nissangtr.management.gtradapter.FakeGtrWarehouseAdapter
import co.zw.nissangtr.management.gtradapter.GtrPasswordAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffAuthAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffHubAdapter
import co.zw.nissangtr.management.gtradapter.GtrStaffSession
import co.zw.nissangtr.management.gtradapter.GtrWarehouseAdapter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Default Fake injectors for CoolMall smoke. Swap to Live when supabase-kt lands (Phase B).
 */
@Module
@InstallIn(SingletonComponent::class)
object GtrAdapterModule {
    @Provides
    @Singleton
    fun provideGtrStaffSession(): GtrStaffSession = GtrStaffSession()

    @Provides
    @Singleton
    fun provideGtrStaffAuthAdapter(session: GtrStaffSession): GtrStaffAuthAdapter =
        FakeGtrStaffAuthAdapter(session)

    @Provides
    @Singleton
    fun provideGtrStaffHubAdapter(): GtrStaffHubAdapter = FakeGtrStaffHubAdapter()

    @Provides
    @Singleton
    fun provideGtrPasswordAdapter(session: GtrStaffSession): GtrPasswordAdapter =
        FakeGtrPasswordAdapter(session)

    @Provides
    @Singleton
    fun provideGtrWarehouseAdapter(): GtrWarehouseAdapter = FakeGtrWarehouseAdapter()
}
