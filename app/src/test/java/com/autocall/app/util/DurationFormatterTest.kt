package com.autocall.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatterTest {

    @Test
    fun formatEntry_appendsHungUp_whenAutoHangup() {
        assertEquals(
            "1m 05s (hung up)",
            DurationFormatter.formatEntry(
                RecentCallDuration(durationSeconds = 65, endedByAutoHangup = true),
            ),
        )
    }

    @Test
    fun formatEntry_plainDuration_whenNotHungUp() {
        assertEquals(
            "58s",
            DurationFormatter.formatEntry(
                RecentCallDuration(durationSeconds = 58, endedByAutoHangup = false),
            ),
        )
    }

    @Test
    fun formatRecent_marksHungUpEntries() {
        assertEquals(
            "Recent calls: 1m 10s (hung up), 58s (avg 1m 04s)",
            DurationFormatter.formatRecent(
                listOf(
                    RecentCallDuration(70, endedByAutoHangup = true),
                    RecentCallDuration(58, endedByAutoHangup = false),
                ),
            ),
        )
    }

    @Test
    fun formatRecent_singleHungUpEntry_omitsAverage() {
        assertEquals(
            "Recent calls: 1m 11s (hung up)",
            DurationFormatter.formatRecent(
                listOf(RecentCallDuration(71, endedByAutoHangup = true)),
            ),
        )
    }

    @Test
    fun formatRecent_none_whenEmpty() {
        assertEquals("Recent calls: none", DurationFormatter.formatRecent(emptyList()))
    }
}
