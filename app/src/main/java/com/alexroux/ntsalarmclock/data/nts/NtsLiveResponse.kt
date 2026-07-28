package com.alexroux.ntsalarmclock.data.nts

import com.google.gson.annotations.SerializedName

data class NtsLiveResponse(
    @SerializedName("results")
    val channels: List<NtsChannel>
)

data class NtsChannel(
    @SerializedName("channel_name")
    val name: String,
    @SerializedName("now")
    val currentShow: NtsCurrentShow
)

data class NtsCurrentShow(
    @SerializedName("broadcast_title")
    val title: String
)
