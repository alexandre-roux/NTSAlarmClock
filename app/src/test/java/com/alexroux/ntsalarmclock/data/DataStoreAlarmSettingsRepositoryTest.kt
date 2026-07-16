package com.alexroux.ntsalarmclock.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.time.DayOfWeek

/** Tests the repository with an isolated, real Preferences DataStore. */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreAlarmSettingsRepositoryTest {

    private val enabledKey = booleanPreferencesKey("alarm_enabled")
    private val hourKey = intPreferencesKey("alarm_hour")
    private val minuteKey = intPreferencesKey("alarm_minute")
    private val volumeKey = intPreferencesKey("alarm_volume")
    private val enabledDaysKey = stringSetPreferencesKey("alarm_enabled_days")
    private val progressiveVolumeKey = booleanPreferencesKey("alarm_progressive_volume")

    @Before
    fun setUpAndroidLog() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    private fun TestScope.createDataStore(): DataStore<Preferences> {
        val file = Files
            .createTempFile("alarm-settings-test", ".preferences_pb")
            .toFile()

        return PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
    }

    /** With no stored preferences, the repository emits the complete application defaults. */
    @Test
    fun `empty DataStore returns default settings`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        assertEquals(
            AlarmSettings(
                enabled = false,
                hour = 7,
                minute = 0,
                volume = 70,
                enabledDays = emptySet(),
                progressiveVolume = false
            ),
            repository.settings.first()
        )
    }

    /** Enabling the alarm is persisted and reflected by the repository's settings flow. */
    @Test
    fun `setEnabled updates the enabled setting`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setEnabled(true)

        val settings = repository.settings.first()

        assertTrue(settings.enabled)
    }

    /** Updating the alarm time persists both hour and minute as one logical setting change. */
    @Test
    fun `setTime updates the hour and minute`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setTime(9, 30)

        val settings = repository.settings.first()

        assertEquals(9, settings.hour)
        assertEquals(30, settings.minute)
    }

    /** Enabled days are stored as enum names and reconstructed as the same enum set. */
    @Test
    fun `setEnabledDays stores and reads enum names`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)

        repository.setEnabledDays(days)

        val storedDays = dataStore.data.first()[enabledDaysKey]
        val settings = repository.settings.first()

        assertEquals(setOf("MONDAY", "FRIDAY"), storedDays)
        assertEquals(days, settings.enabledDays)
    }

    /** Changing volume is persisted and exposed through the mapped settings flow. */
    @Test
    fun `setVolume updates the volume setting`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setVolume(42)

        val settings = repository.settings.first()

        assertEquals(42, settings.volume)
    }

    /** Enabling progressive volume is persisted and returned in the repository model. */
    @Test
    fun `setProgressiveVolume updates the progressive volume setting`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setProgressiveVolume(true)

        val settings = repository.settings.first()

        assertTrue(settings.progressiveVolume)
    }

    /** Unknown stored day names are skipped while valid names still survive deserialization. */
    @Test
    fun `invalid stored days are ignored`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        dataStore.edit { preferences ->
            preferences[enabledDaysKey] =
                setOf("MONDAY", "MO", "INVALID_DAY", "FRIDAY")
        }

        val settings = repository.settings.first()

        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), settings.enabledDays)
    }

    /** Each targeted update leaves values written by earlier repository operations intact. */
    @Test
    fun `updating one setting preserves the other settings`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setTime(6, 45)
        repository.setVolume(25)
        repository.setEnabled(true)

        val settings = repository.settings.first()

        assertTrue(settings.enabled)
        assertEquals(6, settings.hour)
        assertEquals(45, settings.minute)
        assertEquals(25, settings.volume)
    }

    /** A fully populated preference record maps every raw key to its domain-model field. */
    @Test
    fun `raw preferences are mapped to alarm settings`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        dataStore.edit { preferences ->
            preferences[enabledKey] = true
            preferences[hourKey] = 5
            preferences[minuteKey] = 20
            preferences[volumeKey] = 15
            preferences[enabledDaysKey] = setOf("TUESDAY", "THURSDAY")
            preferences[progressiveVolumeKey] = true
        }

        assertEquals(
            AlarmSettings(
                enabled = true,
                hour = 5,
                minute = 20,
                volume = 15,
                enabledDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
                progressiveVolume = true
            ),
            repository.settings.first()
        )
    }
}
