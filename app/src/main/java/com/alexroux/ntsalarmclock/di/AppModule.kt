package com.alexroux.ntsalarmclock.di

import android.content.Context
import com.alexroux.ntsalarmclock.alarm.AlarmScheduler
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.DataStoreAlarmSettingsRepository
import com.alexroux.ntsalarmclock.data.alarmSettingsDataStore
import com.alexroux.ntsalarmclock.data.nts.NtsNetwork
import com.alexroux.ntsalarmclock.data.nts.NtsRepository
import com.alexroux.ntsalarmclock.ui.screens.home.AlarmScheduleTextFormatter
import com.alexroux.ntsalarmclock.ui.screens.home.ResourceAlarmScheduleTextFormatter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Defines how Hilt creates dependencies that are shared across the entire application.
 *
 * Installing this module in [SingletonComponent] makes its providers available to Android classes
 * managed by Hilt, such as Activities, Services, and ViewModels. Each [Singleton] provider creates one
 * application-wide instance, while [ApplicationContext] supplies a long-lived Context that is safe for
 * repositories, resource access, and alarm scheduling.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAlarmSettingsRepository(
        @ApplicationContext context: Context
    ): AlarmSettingsRepository =
        DataStoreAlarmSettingsRepository(context.alarmSettingsDataStore)

    @Provides
    @Singleton
    fun provideAlarmScheduler(
        @ApplicationContext context: Context
    ): AlarmScheduler = AlarmScheduler(context)

    @Provides
    @Singleton
    fun provideAlarmScheduleTextFormatter(
        @ApplicationContext context: Context
    ): AlarmScheduleTextFormatter = ResourceAlarmScheduleTextFormatter(context.resources)

    @Provides
    @Singleton
    fun provideNtsRepository(): NtsRepository = NtsRepository(NtsNetwork.api)
}
