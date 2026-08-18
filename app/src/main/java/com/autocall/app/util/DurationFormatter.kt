package com.autocall.app.util

object DurationFormatter {

    fun format(totalSeconds: Int): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            "${minutes}m ${seconds.toString().padStart(2, '0')}s"
        } else {
            "${seconds}s"
        }
    }

    fun parseOptionalDuration(minutesText: String, secondsText: String): Int? {
        val minutes = minutesText.trim().toIntOrNull() ?: 0
        val seconds = secondsText.trim().toIntOrNull() ?: 0
        if (minutesText.isBlank() && secondsText.isBlank()) return null
        val total = minutes * 60 + seconds
        return total.takeIf { it > 0 }
    }

    fun split(totalSeconds: Int?): Pair<String, String> {
        if (totalSeconds == null || totalSeconds <= 0) return "" to ""
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val minutesText = if (minutes > 0) minutes.toString() else ""
        val secondsText = when {
            seconds > 0 -> seconds.toString()
            minutes == 0 -> seconds.toString()
            else -> ""
        }
        return minutesText to secondsText
    }
}
