package com.alexroux.ntsalarmclock.data.nts

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Singleton responsible for providing Retrofit + API instance.
 * Ensures that Retrofit is created only once in the app lifecycle.
 */
object NtsNetwork {

    // Lazy initialization ensures thread-safe singleton
    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.nts.live/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // Expose API as singleton
    val api: NtsApi by lazy {
        retrofit.create(NtsApi::class.java)
    }
}