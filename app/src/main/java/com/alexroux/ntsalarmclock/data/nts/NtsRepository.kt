package com.alexroux.ntsalarmclock.data.nts

class NtsRepository(private val api: NtsApi) {

    suspend fun getCurrentShow(): String? {
        return runCatching {
            api.getLive()
                .results
                .firstOrNull()
                ?.now
                ?.broadcastTitle
        }.getOrNull()
    }
}