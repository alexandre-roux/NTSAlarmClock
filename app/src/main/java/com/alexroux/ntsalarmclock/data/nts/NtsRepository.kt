package com.alexroux.ntsalarmclock.data.nts

class NtsRepository(private val api: NtsApi) {

    suspend fun getCurrentShow(): Result<String?> {
        return runCatching {
            api.getLive()
                .results
                .firstOrNull()
                ?.now
                ?.broadcastTitle
        }
    }
}
