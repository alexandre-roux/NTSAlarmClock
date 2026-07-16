package com.alexroux.ntsalarmclock.data.nts

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Verifies the JSON contract at the NTS API boundary.
 *
 * Production uses Gson to deserialize fields whose API names do not match idiomatic Kotlin property
 * names. A literal JSON sample makes this test independent of the network while still exercising the
 * real Gson annotations and nested model structure.
 */
class NtsLiveResponseTest {

    /**
     * The sample uses API keys `results`, `channel_name`, `now`, and `broadcast_title`. After real Gson
     * deserialization, the test selects the only channel and checks its readable `name` and nested show
     * `title`. A failure identifies a renamed field or missing serialization annotation before it can
     * silently produce empty data in the ringing UI.
     */
    @Test
    fun `API fields map to readable model properties`() {
        val json = """
            {
              "results": [
                {
                  "channel_name": "channel-1",
                  "now": { "broadcast_title": "Breakfast Show" }
                }
              ]
            }
        """.trimIndent()

        val response = Gson().fromJson(json, NtsLiveResponse::class.java)
        val channel = response.channels.single()

        assertEquals("channel-1", channel.name)
        assertEquals("Breakfast Show", channel.currentShow.title)
    }
}
