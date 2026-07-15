package com.alexroux.ntsalarmclock.ui.screens.home

import android.content.res.Resources
import com.alexroux.ntsalarmclock.R
import com.alexroux.ntsalarmclock.alarm.NextAlarmCalculator
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime

/** Converts the next alarm occurrence into the status text shown on the home screen. */
interface AlarmScheduleTextFormatter {
    /**
     * Returns a disabled, unscheduled, or countdown message for the supplied alarm state.
     *
     * [now] is injectable so callers and tests can calculate the message from a stable instant.
     */
    fun format(
        enabled: Boolean,
        hour: Int,
        minute: Int,
        enabledDays: Set<DayOfWeek>,
        now: LocalDateTime = LocalDateTime.now()
    ): String
}

/** Android-resource-backed formatter that keeps quantities and separators localizable. */
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

        // Status text is intentionally minute-granular; any remaining seconds are truncated.
        // Guarding against negative values also protects the UI if a calculator implementation
        // ever returns a timestamp earlier than the supplied `now` value.
        val minutesUntilAlarm = Duration.between(now, nextTrigger).toMinutes().coerceAtLeast(0)
        val days = minutesUntilAlarm / MINUTES_PER_DAY
        val hours = (minutesUntilAlarm % MINUTES_PER_DAY) / MINUTES_PER_HOUR
        val minutes = minutesUntilAlarm % MINUTES_PER_HOUR
        // Omit zero-valued units so the result stays compact (for example, "1 day and 2 minutes").
        val durationParts = buildList {
            if (days > 0) add(resources.formatQuantity(R.plurals.duration_days, days))
            if (hours > 0) add(resources.formatQuantity(R.plurals.duration_hours, hours))
            if (minutes > 0) add(resources.formatQuantity(R.plurals.duration_minutes, minutes))
        }

        // A zero-minute duration still represents a future alarm less than sixty seconds away.
        if (durationParts.isEmpty()) {
            return resources.getString(R.string.alarm_scheduled_in_less_than_minute)
        }

        return resources.getString(
            R.string.alarm_scheduled_in,
            resources.joinDurationParts(durationParts)
        )
    }

    private fun Resources.formatQuantity(id: Int, value: Long): String {
        // The first value selects the plural rule; the second fills the quantity placeholder.
        return getQuantityString(id, value.toInt(), value)
    }

    private fun Resources.joinDurationParts(parts: List<String>): String {
        // Separators are resources because punctuation and conjunction placement vary by locale.
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
