package com.alexroux.ntsalarmclock.di

import android.content.Context
import com.alexroux.ntsalarmclock.alarm.AlarmScheduler
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.alarmSettingsDataStore
import com.alexroux.ntsalarmclock.data.nts.NtsNetwork
import com.alexroux.ntsalarmclock.data.nts.NtsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAlarmSettingsRepository(
        @ApplicationContext context: Context
    ): AlarmSettingsRepository {
        return DataStoreAlarmSettingsRepository(context.alarmSettingsDataStore)
    }

    @Provides
    @Singleton
    fun provideAlarmScheduler(
        @ApplicationContext context: Context
    ): AlarmScheduler {
        return AlarmScheduler(context)
    }

    @Provides
    @Singleton
    fun provideNtsRepository(): NtsRepository {
        return NtsRepository(NtsNetwork.api)
    }
}