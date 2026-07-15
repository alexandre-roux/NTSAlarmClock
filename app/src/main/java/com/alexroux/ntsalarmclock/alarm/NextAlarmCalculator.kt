package com.alexroux.ntsalarmclock.alarm

import java.time.DayOfWeek
import java.time.LocalDateTime

/**
 * Shared utility used to compute the next alarm trigger time.
 *
 * This logic is intentionally extracted so both the UI layer and the
 * scheduling layer can rely on the exact same calculation.
 */
object NextAlarmCalculator {

    /**
     * Compute the next date-time when the alarm should ring.
     *
     * When no day is selected, the alarm behaves as a one-shot alarm
     * scheduled for today or tomorrow depending on the current time.
     */
    fun computeNextTriggerDateTime(
        now: LocalDateTime = LocalDateTime.now(),
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeek>
    ): LocalDateTime? {
        if (enabledDays.isEmpty()) {
            var candidate = now
                .withHour(hour)
                .withMinute(minute)
                .withSecond(0)
                .withNano(0)

            if (!candidate.isAfter(now)) {
                candidate = candidate.plusDays(1)
            }

            return candidate
        }

        for (offset in 0..7) {
            val candidateDate = now.toLocalDate().plusDays(offset.toLong())
            val candidateDay = candidateDate.dayOfWeek

            if (candidateDay !in enabledDays) {
                continue
            }

            val candidate = candidateDate.atTime(hour, minute, 0, 0)

            if (candidate.isAfter(now)) {
                return candidate
            }
        }

        return null
    }

    /**
     * Compute the next alarm trigger time in epoch milliseconds.
     */
    fun computeNextTriggerMillis(
        now: LocalDateTime = LocalDateTime.now(),
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeek>
    ): Long? {
        return computeNextTriggerDateTime(
            now = now,
            hour = hour,
            minute = minute,
            enabledDays = enabledDays
        )?.atZone(java.time.ZoneId.systemDefault())
            ?.toInstant()
            ?.toEpochMilli()
    }

}
