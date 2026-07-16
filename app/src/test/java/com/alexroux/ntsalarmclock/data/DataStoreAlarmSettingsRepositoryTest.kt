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

/**
 * Tests the repository with an isolated, real Preferences DataStore.
 *
 * Unlike a mock, the temporary DataStore performs the same serialization and asynchronous edits as
 * production. Each test creates its own temporary file, so no preferences leak between tests. Calling
 * `repository.settings.first()` waits for the first mapped [AlarmSettings] value and then stops
 * collecting, which is sufficient for verifying one update.
 *
 * The preference keys are repeated here only when a test needs to inspect or seed the raw storage
 * format. Most tests deliberately use the repository's public methods, matching normal app usage.
 */
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
        // DataStore needs a physical file and a CoroutineScope. A unique temporary file gives each
        // test production-like persistence, while backgroundScope lets runTest clean up its jobs.
        val file = Files
            .createTempFile("alarm-settings-test", ".preferences_pb")
            .toFile()

        return PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = { file }
        )
    }

    /**
     * Given a brand-new DataStore containing no keys, the repository should supply defaults for every
     * [AlarmSettings] field. Comparing the complete object catches both wrong default values and a
     * mapping that accidentally omits a field.
     */
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

    /**
     * Given a repository backed by empty storage, calling `setEnabled(true)` should write the enabled
     * preference. Reading the settings flow afterward and observing `true` proves the public setter and
     * the preference-to-domain mapping agree.
     */
    @Test
    fun `setEnabled updates the enabled setting`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setEnabled(true)

        val settings = repository.settings.first()

        assertTrue(settings.enabled)
    }

    /**
     * Alarm time is represented by two preference keys but exposed as one repository operation. After
     * setting 09:30, separate assertions for hour and minute prove both parts were written and neither
     * argument was lost or swapped.
     */
    @Test
    fun `setTime updates the hour and minute`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setTime(9, 30)

        val settings = repository.settings.first()

        assertEquals(9, settings.hour)
        assertEquals(30, settings.minute)
    }

    /**
     * DataStore cannot store [DayOfWeek] objects directly, so the repository serializes them as names.
     * The raw assertion proves `MONDAY` and `FRIDAY` are stored in the portable string format; the
     * domain assertion proves reading converts those strings back into the original enum set.
     */
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

    /**
     * Calling `setVolume(42)` should update the integer preference used by playback settings. Reading
     * 42 from the mapped model proves the setter writes the correct key and the settings flow reads it.
     * Range validation is tested elsewhere; this test is specifically about persistence.
     */
    @Test
    fun `setVolume updates the volume setting`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setVolume(42)

        val settings = repository.settings.first()

        assertEquals(42, settings.volume)
    }

    /**
     * Calling `setProgressiveVolume(true)` should store the playback-ramp preference. Observing `true`
     * in [AlarmSettings] proves this newer boolean field participates in both write and read mapping.
     * Starting from empty storage also shows the update changes the default value of false.
     */
    @Test
    fun `setProgressiveVolume updates the progressive volume setting`() = runTest {
        val dataStore = createDataStore()
        val repository = DataStoreAlarmSettingsRepository(dataStore)

        repository.setProgressiveVolume(true)

        val settings = repository.settings.first()

        assertTrue(settings.progressiveVolume)
    }

    /**
     * Storage may contain obsolete or corrupted weekday strings after an app update or manual damage.
     * The test writes two valid names and two invalid names directly. Reading only Monday and Friday
     * proves malformed entries are ignored individually instead of crashing or discarding the entire set.
     */
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

    /**
     * Repository setters should edit only their own preference keys. The test writes time, then volume,
     * then enabled state and finally reads all four values. If a later edit replaced the whole preference
     * record, one of the earlier assertions would fail.
     */
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

    /**
     * This is the reverse-direction integration test: raw preferences are inserted without using any
     * repository setter, then the settings flow must construct the exact domain object. It covers every
     * key together, including weekday deserialization and progressive volume, to document the complete
     * on-disk-to-model contract.
     */
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
