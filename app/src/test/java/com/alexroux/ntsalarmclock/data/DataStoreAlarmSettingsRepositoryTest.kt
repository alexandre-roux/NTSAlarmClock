package com.alexroux.ntsalarmclock.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * Repository tests backed by a real temporary Preferences DataStore.
 *
 * Using the actual DataStore implementation verifies key names, defaults, and
 * serialization without writing to the app's production preferences file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreAlarmSettingsRepositoryTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
    }

    private fun createDataStore(scope: TestScope): DataStore<Preferences> {
        // Each test gets an isolated file so persisted values cannot leak
        // between repository instances.
        val file = Files.createTempFile("alarm-settings-test", ".preferences_pb").toFile()

        return PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { file }
        )
    }

    @Test
    fun defaultValuesAreReturnedWhenDataStoreIsEmpty() = runTest {
        val dataStore = createDataStore(this)
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        val settings = repository.settings.first()

        assertFalse(settings.enabled)
        assertEquals(7, settings.hour)
        assertEquals(0, settings.minute)
        assertEquals(70, settings.volume)
        assertTrue(settings.enabledDays.isEmpty())
        assertFalse(settings.progressiveVolume)
    }

    @Test
    fun setEnabledUpdatesDataStore() = runTest {
        val dataStore = createDataStore(this)
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setEnabled(true)

        val settings = repository.settings.first()

        assertTrue(settings.enabled)
    }

    @Test
    fun setTimeUpdatesDataStore() = runTest {
        val dataStore = createDataStore(this)
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setTime(9, 30)

        val settings = repository.settings.first()

        assertEquals(9, settings.hour)
        assertEquals(30, settings.minute)
    }

    @Test
    fun setEnabledDaysUpdatesDataStore() = runTest {
        val dataStore = createDataStore(this)
        val repository = DataStoreAlarmSettingsRepository(dataStore)
        val days = setOf(DayOfWeekUi.MO, DayOfWeekUi.FR)

        repository.setEnabledDays(days)

        val settings = repository.settings.first()

        assertEquals(days, settings.enabledDays)
    }

    @Test
    fun setVolumeUpdatesDataStore() = runTest {
        val dataStore = createDataStore(this)
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setVolume(42)

        val settings = repository.settings.first()

        assertEquals(42, settings.volume)
    }

    @Test
    fun setProgressiveVolumeUpdatesDataStore() = runTest {
        val dataStore = createDataStore(this)
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setProgressiveVolume(true)

        val settings = repository.settings.first()

        assertTrue(settings.progressiveVolume)
    }

    @Test
    fun invalidStoredDaysAreIgnored() = runTest {
        val dataStore = createDataStore(this)
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        // Write raw preferences directly to simulate corrupted or obsolete
        // persisted data that bypassed the repository API.
        dataStore.edit { prefs ->
            prefs[stringSetPreferencesKey("alarm_enabled_days")] =
                setOf("MO", "INVALID_DAY", "FR")
        }

        val settings = repository.settings.first()

        assertEquals(setOf(DayOfWeekUi.MO, DayOfWeekUi.FR), settings.enabledDays)
    }

    @Test
    fun updatingOneFieldKeepsPreviouslyStoredValues() = runTest {
        val dataStore = createDataStore(this)
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

    @Test
    fun repositoryMapsRawStoredValuesCorrectly() = runTest {
        val dataStore = createDataStore(this)
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        // This verifies the repository mapping layer, not the setter methods.
        dataStore.edit { prefs ->
            prefs[booleanPreferencesKey("alarm_enabled")] = true
            prefs[intPreferencesKey("alarm_hour")] = 5
            prefs[intPreferencesKey("alarm_minute")] = 20
            prefs[intPreferencesKey("alarm_volume")] = 15
            prefs[stringSetPreferencesKey("alarm_enabled_days")] = setOf("TU", "TH")
            prefs[booleanPreferencesKey("alarm_progressive_volume")] = true
        }

        val settings = repository.settings.first()

        assertTrue(settings.enabled)
        assertEquals(5, settings.hour)
        assertEquals(20, settings.minute)
        assertEquals(15, settings.volume)
        assertEquals(setOf(DayOfWeekUi.TU, DayOfWeekUi.TH), settings.enabledDays)
        assertTrue(settings.progressiveVolume)
    }
}
