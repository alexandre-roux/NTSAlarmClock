package com.alexroux.ntsalarmclock.ui.screens.home

import android.content.res.Resources
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.alarm.NextAlarmCalculator
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime

interface AlarmScheduleTextFormatter {
    fun format(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeek>,
        now: LocalDateTime = LocalDateTime.now()
    ): String
}

class ResourceAlarmScheduleTextFormatter(
    private val resources: Resources
) : AlarmScheduleTextFormatter {
    override fun format(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeek>,
        now: LocalDateTime
    ): String {
        if (!enabled) return resources.getString(R.string.alarm_disabled)

        val nextTrigger = NextAlarmCalculator.computeNextTriggerDateTime(
            now = now,
            hour = hour,
            minute = minute,
            enabledDays = enabledDays
        ) ?: return resources.getString(R.string.no_alarm_scheduled)

        val minutesUntilAlarm = Duration.between(now, nextTrigger).toMinutes().coerceAtLeast(0)
        val days = minutesUntilAlarm / MINUTES_PER_DAY
        val hours = (minutesUntilAlarm % MINUTES_PER_DAY) / MINUTES_PER_HOUR
        val minutes = minutesUntilAlarm % MINUTES_PER_HOUR
        val durationParts = buildList {
            if (days > 0) add(resources.formatQuantity(R.plurals.duration_days, days))
            if (hours > 0) add(resources.formatQuantity(R.plurals.duration_hours, hours))
            if (minutes > 0) add(resources.formatQuantity(R.plurals.duration_minutes, minutes))
        }

        if (durationParts.isEmpty()) {
            return resources.getString(R.string.alarm_scheduled_in_less_than_minute)
        }

        return resources.getString(
            R.string.alarm_scheduled_in,
            resources.joinDurationParts(durationParts)
        )
    }

    private fun Resources.formatQuantity(id: Int, value: Long): String {
        return getQuantityString(id, value.toInt(), value)
    }

    private fun Resources.joinDurationParts(parts: List<String>): String {
        val separator = getString(R.string.duration_separator)
        val finalSeparator = getString(R.string.duration_final_separator)
        return when (parts.size) {
            1 -> parts.first()
            2 -> parts.joinToString(" $finalSeparator ")
            else -> {
                val initialParts = parts.dropLast(1).joinToString("$separator ")
                "$initialParts $finalSeparator ${parts.last()}"
            }
        }
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    }
}
