package com.autocall.app.util

import java.util.Calendar

object DaysOfWeek {

    fun parse(value: String): Set<Int> =
        value.split(",")
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
            .toSet()

    fun encode(days: Set<Int>): String =
        days.filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
            .sorted()
            .joinToString(",")

    fun toggle(days: Set<Int>, day: Int): Set<Int> {
        val updated = days.toMutableSet()
        if (updated.contains(day)) {
            updated.remove(day)
        } else {
            updated.add(day)
        }
        return updated
    }

    fun formatShort(days: Set<Int>): String {
        if (days.isEmpty()) return "No days selected"
        return days.sorted()
            .joinToString(", ") { day ->
                DayOfWeekFormatter.label(day).take(3)
            }
    }

    fun formatLong(days: Set<Int>): String {
        if (days.isEmpty()) return "No days selected"
        return days.sorted()
            .joinToString(", ") { day -> DayOfWeekFormatter.label(day) }
    }
}
