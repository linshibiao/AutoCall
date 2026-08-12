package com.autocall.app.util

import com.autocall.app.data.ScheduledCall
import com.autocall.app.scheduler.AlarmScheduler

fun ScheduledCall.nextTriggerMillis(): Long? {
    if (!isEnabled) return null
    return AlarmScheduler.nextTriggerMillis(days(), hour, minute)
}

fun List<ScheduledCall>.sortedByNextTrigger(): List<ScheduledCall> =
    sortedWith(
        compareBy<ScheduledCall> { if (it.isEnabled) 0 else 1 }
            .thenBy { it.nextTriggerMillis() ?: Long.MAX_VALUE }
            .thenBy { it.id },
    )
