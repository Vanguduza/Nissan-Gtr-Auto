package com.joker.coolmall.di

import co.zw.nissangtr.management.gtradapter.GtrSupabaseConfig
import com.joker.coolmall.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Bridges app BuildConfig (from local.properties) into gtradapter Live/Fake switch.
 */
@Module
@InstallIn(SingletonComponent::class)
object GtrSupabaseConfigModule {
    @Provides
    @Singleton
    fun provideGtrSupabaseConfig(): GtrSupabaseConfig = object : GtrSupabaseConfig {
        override val supabaseUrl: String = BuildConfig.SUPABASE_URL
        override val supabaseAnonKey: String = BuildConfig.SUPABASE_ANON_KEY
        override val forceFake: Boolean = BuildConfig.RPC_FORCE_FAKE
    }
}
