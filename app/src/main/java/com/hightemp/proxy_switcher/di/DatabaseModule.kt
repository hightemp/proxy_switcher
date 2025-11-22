package com.hightemp.proxy_switcher.di

import android.content.Context
import androidx.room.Room
import com.hightemp.proxy_switcher.data.local.AppDatabase
import com.hightemp.proxy_switcher.data.local.ProxyDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "proxy_switcher_db"
        ).build()
    }

    @Provides
    fun provideProxyDao(database: AppDatabase): ProxyDao {
        return database.proxyDao()
    }
}