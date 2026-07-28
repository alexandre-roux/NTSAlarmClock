package com.alexroux.ntsalarmclock.data.nts

class NtsRepository(private val api: NtsApi) {

    suspend fun getCurrentShow(): Result<String?> =
        runCatching {
            api.getLiveSchedule()
                .channels
                .firstOrNull()
                ?.currentShow
                ?.title
        }
}
