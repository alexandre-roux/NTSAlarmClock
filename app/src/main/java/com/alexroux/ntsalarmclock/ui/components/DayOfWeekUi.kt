package com.alexroux.ntsalarmclock.ui.components

/**
 * Enum representing the days of the week used in the UI.
 *
 * Each value contains a short label that is displayed
 * in the day selection buttons on the home screen.
 */
enum class DayOfWeekUi(val shortLabel: String) {
    MO("Mo"),
    TU("Tu"),
    WE("We"),
    TH("Th"),
    FR("Fr"),
    SA("Sa"),
    SU("So"),
}