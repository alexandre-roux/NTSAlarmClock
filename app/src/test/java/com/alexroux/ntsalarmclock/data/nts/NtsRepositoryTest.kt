package com.alexroux.ntsalarmclock.data.nts

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NtsRepositoryTest {
    private val api = mockk<NtsApi>()
    private val repository = NtsRepository(api)

    @Test
    fun getCurrentShow_returnsCurrentBroadcastTitle() = runTest {
        coEvery { api.getLive() } returns NtsLiveResponse(
            results = listOf(
                Channel(
                    channelName = "channel-1",
                    now = Now(broadcastTitle = "Breakfast Show")
                )
            )
        )

        val result = repository.getCurrentShow()

        assertTrue(result.isSuccess)
        assertEquals("Breakfast Show", result.getOrNull())
    }

    @Test
    fun getCurrentShow_preservesNetworkFailure() = runTest {
        val failure = IllegalStateException("network unavailable")
        coEvery { api.getLive() } throws failure

        val result = repository.getCurrentShow()

        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }
}
