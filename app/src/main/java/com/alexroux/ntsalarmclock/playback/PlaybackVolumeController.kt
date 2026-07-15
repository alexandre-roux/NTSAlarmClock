package com.alexroux.ntsalarmclock.playback

import androidx.media3.common.Player
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private const val PROGRESSIVE_VOLUME_DURATION_MS = 60_000L
private const val PROGRESSIVE_VOLUME_STEP_DELAY_MS = 1_000L

class PlaybackVolumeController(
    private val scope: CoroutineScope,
    private val repository: AlarmSettingsRepository
) {
    private var progressiveVolumeJob: Job? = null

    fun startProgressiveVolume(player: Player, targetVolume: Float) {
        cancelProgressiveVolume()
        progressiveVolumeJob = scope.launch {
            val stepCount =
                (PROGRESSIVE_VOLUME_DURATION_MS / PROGRESSIVE_VOLUME_STEP_DELAY_MS).toInt()

            repeat(stepCount) {
                delay(PROGRESSIVE_VOLUME_STEP_DELAY_MS.milliseconds)
                player.volume = PlaybackServiceLogic.nextProgressiveVolumeStep(
                    currentVolume = player.volume,
                    targetVolume = targetVolume,
                    stepCount = stepCount
                )
                if (player.volume >= targetVolume) return@launch
            }

            player.volume = targetVolume
        }
    }

    fun setAbsoluteVolume(player: Player, volumePercent: Int): Float {
        cancelProgressiveVolume()
        val sanitizedVolume = PlaybackServiceLogic.coerceVolumePercent(volumePercent)
        val playerVolume = PlaybackServiceLogic.toPlayerVolume(sanitizedVolume)
        player.volume = playerVolume
        persistVolume(sanitizedVolume)
        return playerVolume
    }

    fun adjustVolume(player: Player, delta: Float): Float {
        cancelProgressiveVolume()
        val playerVolume = PlaybackServiceLogic.applyManualVolumeDelta(player.volume, delta)
        player.volume = playerVolume
        persistVolume((playerVolume * 100).toInt())
        return playerVolume
    }

    fun cancelProgressiveVolume() {
        progressiveVolumeJob?.cancel()
        progressiveVolumeJob = null
    }

    private fun persistVolume(volumePercent: Int) {
        scope.launch { repository.setVolume(volumePercent) }
    }
}
