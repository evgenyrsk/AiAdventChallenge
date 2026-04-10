package com.example.mcp.server.data.fitness

import java.sql.Connection
import java.sql.DriverManager

class ReminderDatabase(private val dbPath: String = "./fitness_data.db") {

    private var connection: Connection? = null

    fun getConnection(): Connection {
        if (connection == null || connection?.isClosed == true) {
            connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            createTables()
        }
        return connection!!
    }

    private fun createTables() {
        val conn = getConnection()
        val statement = conn.createStatement()

        statement.execute("""
            CREATE TABLE IF NOT EXISTS fitness_logs (
                id TEXT PRIMARY KEY NOT NULL,
                date TEXT NOT NULL,
                weight REAL,
                calories INTEGER,
                protein INTEGER,
                workout_completed INTEGER NOT NULL DEFAULT 0,
                steps INTEGER,
                sleep_hours REAL,
                notes TEXT,
                created_at INTEGER NOT NULL
            )
        """)

        statement.execute("""
            CREATE INDEX IF NOT EXISTS index_fitness_logs_date ON fitness_logs (date)
        """)

        statement.execute("""
            CREATE INDEX IF NOT EXISTS index_fitness_logs_created_at ON fitness_logs (created_at)
        """)

        statement.execute("""
            CREATE TABLE IF NOT EXISTS scheduled_summaries (
                id TEXT PRIMARY KEY NOT NULL,
                period TEXT NOT NULL,
                entries_count INTEGER NOT NULL,
                avg_weight REAL,
                workouts_completed INTEGER NOT NULL DEFAULT 0,
                avg_steps INTEGER,
                avg_sleep_hours REAL,
                avg_protein INTEGER,
                adherence_score REAL NOT NULL DEFAULT 0.0,
                summary_text TEXT NOT NULL,
                created_at INTEGER NOT NULL
            )
        """)

        statement.execute("""
            CREATE INDEX IF NOT EXISTS index_scheduled_summaries_created_at ON scheduled_summaries (created_at)
        """)

        statement.execute("""
            CREATE TABLE IF NOT EXISTS reminders (
                id TEXT PRIMARY KEY NOT NULL,
                type TEXT NOT NULL,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                time TEXT NOT NULL,
                days_of_week TEXT NOT NULL,
                is_active INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL
            )
        """)

        statement.execute("""
            CREATE INDEX IF NOT EXISTS index_reminders_active ON reminders (is_active)
        """)

        statement.execute("""
            CREATE TABLE IF NOT EXISTS reminder_events (
                id TEXT PRIMARY KEY NOT NULL,
                reminder_id TEXT NOT NULL,
                type TEXT NOT NULL,
                scheduled_time INTEGER NOT NULL,
                triggered_at INTEGER,
                status TEXT NOT NULL,
                context TEXT,
                response TEXT,
                FOREIGN KEY (reminder_id) REFERENCES reminders (id) ON DELETE CASCADE
            )
        """)

        statement.execute("""
            CREATE INDEX IF NOT EXISTS index_reminder_events_scheduled_time ON reminder_events (scheduled_time)
        """)

        statement.execute("""
            CREATE INDEX IF NOT EXISTS index_reminder_events_status ON reminder_events (status)
        """)

        statement.close()
    }

    fun close() {
        connection?.close()
        connection = null
    }
}