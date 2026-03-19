package com.alexroux.ntsalarmclock.data.nts

import com.google.gson.annotations.SerializedName

data class NtsLiveResponse(
    val results: List<Channel>
)

data class Channel(
    @SerializedName("channel_name")
    val channelName: String,
    val now: Now
)

data class Now(
    @SerializedName("broadcast_title")
    val broadcastTitle: String
)