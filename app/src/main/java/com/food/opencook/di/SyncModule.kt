package com.food.opencook.di

import com.food.opencook.sync.Stamper
import com.food.opencook.sync.SyncClock
import com.food.opencook.sync.SyncManager
import com.food.opencook.sync.SyncTrigger
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides
    fun provideStamper(syncClock: SyncClock): Stamper = syncClock

    @Provides
    fun provideSyncTrigger(syncManager: SyncManager): SyncTrigger = syncManager
}
