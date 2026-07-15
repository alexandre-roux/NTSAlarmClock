package com.alexroux.ntsalarmclock.alarm

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Shared utility used to compute the next alarm trigger time.
 *
 * The UI and scheduling layers use this object so they always agree about the
 * next alarm occurrence.
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
        val alarmToday = now.toLocalDate().atTime(hour, minute)

        if (enabledDays.isEmpty()) {
            return if (alarmToday.isAfter(now)) {
                alarmToday
            } else {
                alarmToday.plusDays(1)
            }
        }

        // Include the same weekday next week when today's alarm time has passed.
        for (daysFromToday in 0..DAYS_IN_WEEK) {
            val candidate = now.toLocalDate()
                .plusDays(daysFromToday.toLong())
                .atTime(hour, minute)

            if (candidate.dayOfWeek in enabledDays && candidate.isAfter(now)) {
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
        val triggerDateTime = computeNextTriggerDateTime(
            now = now,
            hour = hour,
            minute = minute,
            enabledDays = enabledDays
        ) ?: return null

        return triggerDateTime
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private const val DAYS_IN_WEEK = 7
}
