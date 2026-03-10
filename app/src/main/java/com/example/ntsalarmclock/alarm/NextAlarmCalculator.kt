package com.example.ntsalarmclock.alarm

import com.example.ntsalarmclock.ui.components.DayOfWeekUi
import java.time.DayOfWeek
import java.time.Duration
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
        enabledDays: Set<DayOfWeekUi>
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

        val selectedDays = enabledDays.map { it.toJavaDayOfWeek() }.toSet()

        for (offset in 0..7) {
            val candidateDate = now.toLocalDate().plusDays(offset.toLong())
            val candidateDay = candidateDate.dayOfWeek

            if (candidateDay !in selectedDays) {
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
        enabledDays: Set<DayOfWeekUi>
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

    /**
     * Build the helper text displayed in the UI to indicate when the
     * next alarm is scheduled.
     */
    fun buildScheduledInText(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeekUi>,
        now: LocalDateTime = LocalDateTime.now()
    ): String {
        if (!enabled) {
            return "Alarm is disabled"
        }

        val nextTrigger = computeNextTriggerDateTime(
            now = now,
            hour = hour,
            minute = minute,
            enabledDays = enabledDays
        ) ?: return "No alarm scheduled"

        val duration = Duration.between(now, nextTrigger)
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours > 0 && minutes > 0 ->
                "This alarm is scheduled in $hours hours and $minutes minutes"

            hours > 0 ->
                "This alarm is scheduled in $hours hours"

            minutes > 0 ->
                "This alarm is scheduled in $minutes minutes"

            else ->
                "This alarm is scheduled in less than a minute"
        }
    }

    /**
     * Convert the UI enum into java.time.DayOfWeek for date calculations.
     */
    private fun DayOfWeekUi.toJavaDayOfWeek(): DayOfWeek {
        return when (this) {
            DayOfWeekUi.MO -> DayOfWeek.MONDAY
            DayOfWeekUi.TU -> DayOfWeek.TUESDAY
            DayOfWeekUi.WE -> DayOfWeek.WEDNESDAY
            DayOfWeekUi.TH -> DayOfWeek.THURSDAY
            DayOfWeekUi.FR -> DayOfWeek.FRIDAY
            DayOfWeekUi.SA -> DayOfWeek.SATURDAY
            DayOfWeekUi.SU -> DayOfWeek.SUNDAY
        }
    }
}