package com.autocall.app.data

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MigrationsSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun migration_5_6_addsEndCallAfterSeconds() {
        val db = openAtVersion(
            version = 5,
            onCreate = { database ->
                database.execSQL(
                    """
                    CREATE TABLE scheduled_calls (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        contactName TEXT,
                        phoneNumber TEXT NOT NULL,
                        daysOfWeek TEXT NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        useSpeakerphone INTEGER NOT NULL,
                        expectedDurationSeconds INTEGER DEFAULT NULL,
                        successWindowSeconds INTEGER NOT NULL DEFAULT 10
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE call_duration_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        scheduledCallId INTEGER NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        recordedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            },
        )

        assertFalse(hasColumn(db, "scheduled_calls", "endCallAfterSeconds"))
        MIGRATION_5_6.migrate(db)
        assertTrue(hasColumn(db, "scheduled_calls", "endCallAfterSeconds"))
        db.close()
    }

    @Test
    fun migration_6_7_addsEndedByAutoHangupOnLogs() {
        val db = openAtVersion(
            version = 6,
            onCreate = { database ->
                database.execSQL(
                    """
                    CREATE TABLE scheduled_calls (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        contactName TEXT,
                        phoneNumber TEXT NOT NULL,
                        daysOfWeek TEXT NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        useSpeakerphone INTEGER NOT NULL,
                        expectedDurationSeconds INTEGER DEFAULT NULL,
                        successWindowSeconds INTEGER NOT NULL DEFAULT 10,
                        endCallAfterSeconds INTEGER DEFAULT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    CREATE TABLE call_duration_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        scheduledCallId INTEGER NOT NULL,
                        durationSeconds INTEGER NOT NULL,
                        recordedAtMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                database.execSQL(
                    """
                    INSERT INTO call_duration_logs (
                        scheduledCallId, durationSeconds, recordedAtMillis
                    ) VALUES (1, 65, 123456)
                    """.trimIndent(),
                )
            },
        )

        assertFalse(hasColumn(db, "call_duration_logs", "endedByAutoHangup"))
        MIGRATION_6_7.migrate(db)
        assertTrue(hasColumn(db, "call_duration_logs", "endedByAutoHangup"))

        val cursor = db.query("SELECT endedByAutoHangup FROM call_duration_logs")
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.getInt(0) == 0)
        cursor.close()
        db.close()
    }

    private fun openAtVersion(
        version: Int,
        onCreate: (SupportSQLiteDatabase) -> Unit,
    ): SupportSQLiteDatabase {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(null)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) = onCreate(db)
                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                )
                .build(),
        )
        return helper.writableDatabase
    }

    private fun hasColumn(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
    ): Boolean {
        val cursor = db.query("PRAGMA table_info($table)")
        cursor.use {
            val nameIndex = it.getColumnIndex("name")
            while (it.moveToNext()) {
                if (it.getString(nameIndex) == column) return true
            }
        }
        return false
    }
}
