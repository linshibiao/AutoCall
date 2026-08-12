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
