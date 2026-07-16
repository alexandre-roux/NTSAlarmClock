package com.alexroux.ntsalarmclock.data.nts

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests the small boundary between the NTS network API and the rest of the app.
 *
 * [NtsApi] is mocked so no real HTTP request is made. `coEvery` defines what its suspend function
 * returns or throws, and `runTest` provides a deterministic coroutine environment. The repository
 * exposes Kotlin [Result] values so callers can handle a show title, an empty schedule, and network
 * failure without catching exceptions themselves.
 */
class NtsRepositoryTest {
    private val api = mockk<NtsApi>()
    private val repository = NtsRepository(api)

    /**
     * Given an API response whose first channel has "Breakfast Show" as its current broadcast, the
     * repository should return a successful [Result] containing that title. The two assertions prove
     * both the success/failure wrapper and the extracted string are correct.
     */
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

    /**
     * An API response with no channels is valid data, not a network failure. The repository should
     * therefore return `Result.success(null)`. Checking success before checking the null payload makes
     * that distinction explicit for callers of `getCurrentShow()`.
     */
    @Test
    fun `getCurrentShow returns null when the schedule has no channels`() = runTest {
        coEvery { api.getLiveSchedule() } returns NtsLiveResponse(channels = emptyList())

        val result = repository.getCurrentShow()

        assertTrue(result.isSuccess)
        assertNull(result.getOrNull())
    }

    /**
     * Given the API throws a simulated network exception, the repository should catch it and return a
     * failed [Result]. [assertSame] checks identity rather than only message equality, proving diagnostic
     * details from the original exception are preserved exactly.
     */
    @Test
    fun `getCurrentShow preserves a network failure`() = runTest {
        val failure = IllegalStateException("network unavailable")
        coEvery { api.getLiveSchedule() } throws failure

        val result = repository.getCurrentShow()

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }
}
