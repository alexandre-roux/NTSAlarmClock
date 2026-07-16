package com.alexroux.ntsalarmclock.data.nts

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NtsRepositoryTest {
    private val api = mockk<NtsApi>()
    private val repository = NtsRepository(api)

    /** A populated live schedule produces a successful result containing the current show title. */
    @Test
    fun `getCurrentShow returns the current broadcast title`() = runTest {
        coEvery { api.getLiveSchedule() } returns NtsLiveResponse(
            channels = listOf(
                NtsChannel(
                    name = "channel-1",
                    currentShow = NtsCurrentShow(title = "Breakfast Show")
                )
            )
        )

        val result = repository.getCurrentShow()

        assertTrue(result.isSuccess)
        assertEquals("Breakfast Show", result.getOrNull())
    }

    /** An empty schedule is a successful response with no current show, rather than an error. */
    @Test
    fun `getCurrentShow returns null when the schedule has no channels`() = runTest {
        coEvery { api.getLiveSchedule() } returns NtsLiveResponse(channels = emptyList())

        val result = repository.getCurrentShow()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    /** API exceptions are captured as failures while preserving the original exception instance. */
    @Test
    fun `getCurrentShow preserves a network failure`() = runTest {
        val failure = IllegalStateException("network unavailable")
        coEvery { api.getLiveSchedule() } throws failure

        val result = repository.getCurrentShow()

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }
}
