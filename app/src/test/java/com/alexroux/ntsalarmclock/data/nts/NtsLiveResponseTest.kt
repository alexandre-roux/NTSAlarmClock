package com.alexroux.ntsalarmclock.data.nts

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class NtsLiveResponseTest {

    /** Gson maps the API's snake_case keys to the readable channel and current-show properties. */
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
