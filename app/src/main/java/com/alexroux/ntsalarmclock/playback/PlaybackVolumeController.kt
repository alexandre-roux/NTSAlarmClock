package com.alexroux.ntsalarmclock.playback

import androidx.media3.common.Player
import com.alexroux.ntsalarmclock.data.AlarmSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

private const val PROGRESSIVE_VOLUME_STEP_COUNT = 60
private val PROGRESSIVE_VOLUME_STEP_DELAY = 1.seconds

/** Controls player volume and persists changes made while the alarm is ringing. */
class PlaybackVolumeController(
    private val serviceScope: CoroutineScope,
    private val settingsRepository: AlarmSettingsRepository
) {
    private var progressiveVolumeJob: Job? = null

    fun startProgressiveVolume(player: Player, targetVolume: Float) {
        cancelProgressiveVolume()
        progressiveVolumeJob = serviceScope.launch {
            repeat(PROGRESSIVE_VOLUME_STEP_COUNT) {
                delay(PROGRESSIVE_VOLUME_STEP_DELAY)
                val nextVolume = PlaybackServiceLogic.nextProgressiveVolumeStep(
                    currentVolume = player.volume,
                    targetVolume = targetVolume,
                    stepCount = PROGRESSIVE_VOLUME_STEP_COUNT
                )
                player.volume = nextVolume

                if (nextVolume >= targetVolume) return@launch
            }

            player.volume = targetVolume
        }
    }

    fun setAbsoluteVolume(player: Player, volumePercent: Int): Float {
        cancelProgressiveVolume()
        val clampedVolumePercent = PlaybackServiceLogic.coerceVolumePercent(volumePercent)
        val playerVolume = PlaybackServiceLogic.toPlayerVolume(clampedVolumePercent)
        player.volume = playerVolume
        persistVolume(clampedVolumePercent)
        return playerVolume
    }

    fun adjustVolumeBy(player: Player, delta: Float): Float {
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
        serviceScope.launch { settingsRepository.setVolume(volumePercent) }
    }
}
