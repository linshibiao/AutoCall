package com.autocall.app.util

import java.util.Calendar

object DayOfWeekFormatter {

    private val labels = listOf(
        Calendar.SUNDAY to "Sunday",
        Calendar.MONDAY to "Monday",
        Calendar.TUESDAY to "Tuesday",
        Calendar.WEDNESDAY to "Wednesday",
        Calendar.THURSDAY to "Thursday",
        Calendar.FRIDAY to "Friday",
        Calendar.SATURDAY to "Saturday",
    )

    val allDays: List<Pair<Int, String>> = labels

    fun label(dayOfWeek: Int): String =
        labels.firstOrNull { it.first == dayOfWeek }?.second ?: "Unknown"

    fun formatTime(hour: Int, minute: Int): String {
        val amPm = if (hour < 12) "AM" else "PM"
        val displayHour = when (hour % 12) {
            0 -> 12
            else -> hour % 12
        }
        return String.format("%d:%02d %s", displayHour, minute, amPm)
    }
}
