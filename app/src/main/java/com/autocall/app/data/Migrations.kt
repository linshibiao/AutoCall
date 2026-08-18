package com.autocall.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS scheduled_calls_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                contactName TEXT,
                phoneNumber TEXT NOT NULL,
                daysOfWeek TEXT NOT NULL,
                hour INTEGER NOT NULL,
                minute INTEGER NOT NULL,
                isEnabled INTEGER NOT NULL,
                useSpeakerphone INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO scheduled_calls_new (
                id, contactName, phoneNumber, daysOfWeek, hour, minute, isEnabled, useSpeakerphone
            )
            SELECT
                id, contactName, phoneNumber, CAST(dayOfWeek AS TEXT), hour, minute, isEnabled, useSpeakerphone
            FROM scheduled_calls
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE scheduled_calls")
        db.execSQL("ALTER TABLE scheduled_calls_new RENAME TO scheduled_calls")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE scheduled_calls ADD COLUMN expectedDurationSeconds INTEGER DEFAULT NULL",
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE scheduled_calls ADD COLUMN successWindowSeconds INTEGER NOT NULL DEFAULT 10",
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS call_duration_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                scheduledCallId INTEGER NOT NULL,
                durationSeconds INTEGER NOT NULL,
                recordedAtMillis INTEGER NOT NULL,
                FOREIGN KEY(scheduledCallId) REFERENCES scheduled_calls(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_call_duration_logs_scheduledCallId ON call_duration_logs (scheduledCallId)",
        )
    }
}
