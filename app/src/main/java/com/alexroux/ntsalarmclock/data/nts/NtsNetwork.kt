package com.alexroux.ntsalarmclock.data.nts

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

private const val NTS_BASE_URL = "https://www.nts.live/"

/** Creates the shared NTS API client when it is first requested. */
object NtsNetwork {

    val api: NtsApi by lazy {
        Retrofit.Builder()
            .baseUrl(NTS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NtsApi::class.java)
    }
}
