package com.alexroux.ntsalarmclock.alarm

import android.content.res.Resources
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.ui.components.DayOfWeekUi
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
        resources: Resources,
        enabled: Boolean,
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeekUi>,
        now: LocalDateTime = LocalDateTime.now()
    ): String {
        if (!enabled) {
            return resources.getString(R.string.alarm_disabled)
        }

        val nextTrigger = computeNextTriggerDateTime(
            now = now,
            hour = hour,
            minute = minute,
            enabledDays = enabledDays
        ) ?: return resources.getString(R.string.no_alarm_scheduled)

        val duration = Duration.between(now, nextTrigger)
        val totalMinutes = duration.toMinutes().coerceAtLeast(0)
        val days = totalMinutes / (24 * 60)
        val hours = (totalMinutes % (24 * 60)) / 60
        val minutes = totalMinutes % 60
        val durationParts = buildList {
            if (days > 0) {
                add(resources.formatQuantity(R.plurals.duration_days, days))
            }
            if (hours > 0) {
                add(resources.formatQuantity(R.plurals.duration_hours, hours))
            }
            if (minutes > 0) {
                add(resources.formatQuantity(R.plurals.duration_minutes, minutes))
            }
        }

        return if (durationParts.isEmpty()) {
            resources.getString(R.string.alarm_scheduled_in_less_than_minute)
        } else {
            resources.formatString(
                R.string.alarm_scheduled_in,
                resources.joinDurationParts(durationParts)
            )
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

    private fun Resources.formatString(
        id: Int,
        vararg args: Any
    ): String {
        return String.format(getString(id), *args)
    }

    private fun Resources.formatQuantity(
        id: Int,
        value: Long
    ): String {
        return String.format(getQuantityText(id, value.toInt()).toString(), value)
    }

    private fun Resources.joinDurationParts(parts: List<String>): String {
        val separator = getString(R.string.duration_separator)
        val finalSeparator = getString(R.string.duration_final_separator)

        return when (parts.size) {
            0 -> ""
            1 -> parts.first()
            2 -> parts.joinToString(" $finalSeparator ")
            else -> {
                val allButLast = parts.dropLast(1).joinToString("$separator ")
                "$allButLast $finalSeparator ${parts.last()}"
            }
        }
    }
}
