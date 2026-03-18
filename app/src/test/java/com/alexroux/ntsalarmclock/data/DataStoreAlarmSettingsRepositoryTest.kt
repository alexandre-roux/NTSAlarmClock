package com.alexroux.ntsalarmclock.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
import io.mockk.every
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreAlarmSettingsRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreAlarmSettingsRepository
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0

        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { tmpFolder.newFile("test.preferences_pb") }
        )
        repository = DataStoreAlarmSettingsRepository(dataStore)
    }

    @Test
    fun `default values are returned when DataStore is empty`() = runTest(testDispatcher) {
        val settings = repository.settings.first()

        assertFalse(settings.enabled)
        assertEquals(7, settings.hour)
        assertEquals(0, settings.minute)
        assertEquals(70, settings.volume)
        assertTrue(settings.enabledDays.isEmpty())
        assertFalse(settings.progressiveVolume)
    }

    @Test
    fun `setEnabled updates DataStore`() = runTest(testDispatcher) {
        repository.setEnabled(true)
        val settings = repository.settings.first()
        assertTrue(settings.enabled)
    }

    @Test
    fun `setTime updates DataStore`() = runTest(testDispatcher) {
        repository.setTime(9, 30)
        val settings = repository.settings.first()
        assertEquals(9, settings.hour)
        assertEquals(30, settings.minute)
    }

    @Test
    fun `setEnabledDays updates DataStore`() = runTest(testDispatcher) {
        val days = setOf(DayOfWeekUi.MO, DayOfWeekUi.FR)
        repository.setEnabledDays(days)
        val settings = repository.settings.first()
        assertEquals(days, settings.enabledDays)
    }

    @Test
    fun `setVolume updates DataStore`() = runTest(testDispatcher) {
        repository.setVolume(42)
        val settings = repository.settings.first()
        assertEquals(42, settings.volume)
    }

    @Test
    fun `setProgressiveVolume updates DataStore`() = runTest(testDispatcher) {
        repository.setProgressiveVolume(true)
        val settings = repository.settings.first()
        assertTrue(settings.progressiveVolume)
    }
}