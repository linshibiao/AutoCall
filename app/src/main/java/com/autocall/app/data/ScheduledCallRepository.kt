package com.autocall.app.data

import com.autocall.app.scheduler.AlarmScheduler
import kotlinx.coroutines.flow.Flow

class ScheduledCallRepository(
    private val dao: ScheduledCallDao,
    private val alarmScheduler: AlarmScheduler,
) {

    val scheduledCalls: Flow<List<ScheduledCall>> = dao.getAll()

    suspend fun getById(id: Long): ScheduledCall? = dao.getById(id)

    suspend fun insert(scheduledCall: ScheduledCall): Long {
        val id = dao.insert(scheduledCall)
        val saved = scheduledCall.copy(id = id)
        if (saved.isEnabled) {
            alarmScheduler.scheduleCall(saved)
        }
        return id
    }

    suspend fun update(scheduledCall: ScheduledCall) {
        dao.update(scheduledCall)
        alarmScheduler.cancelCall(scheduledCall.id)
        if (scheduledCall.isEnabled) {
            alarmScheduler.scheduleCall(scheduledCall)
        }
    }

    suspend fun delete(scheduledCall: ScheduledCall) {
        alarmScheduler.cancelCall(scheduledCall.id)
        dao.delete(scheduledCall)
    }

    suspend fun deleteById(id: Long) {
        alarmScheduler.cancelCall(id)
        dao.deleteById(id)
    }

    suspend fun setEnabled(scheduledCall: ScheduledCall, enabled: Boolean) {
        update(scheduledCall.copy(isEnabled = enabled))
    }

    suspend fun rescheduleAllEnabled() {
        alarmScheduler.rescheduleAll(dao)
    }
}
