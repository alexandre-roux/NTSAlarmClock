package com.alexroux.ntsalarmclock.data.nts

import retrofit2.http.GET

interface NtsApi {

    @GET("api/v2/live")
    suspend fun getLive(): NtsLiveResponse
}