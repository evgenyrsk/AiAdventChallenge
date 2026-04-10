package com.example.mcp.server.data.fitness

import com.example.mcp.server.model.fitness.ScheduledSummaryEntity
import java.sql.SQLException

class ScheduledSummaryDao(private val database: ReminderDatabase) {

    fun insert(summary: ScheduledSummaryEntity): Boolean {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                INSERT OR REPLACE INTO scheduled_summaries (
                    id, period, entries_count, avg_weight, workouts_completed,
                    avg_steps, avg_sleep_hours, avg_protein, adherence_score,
                    summary_text, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)

            statement.setString(1, summary.id)
            statement.setString(2, summary.period)
            statement.setInt(3, summary.entriesCount)
            statement.setDouble(4, summary.avgWeight ?: 0.0)
            statement.setInt(5, summary.workoutsCompleted)
            statement.setInt(6, summary.avgSteps ?: 0)
            statement.setDouble(7, summary.avgSleepHours ?: 0.0)
            statement.setInt(8, summary.avgProtein ?: 0)
            statement.setDouble(9, summary.adherenceScore)
            statement.setString(10, summary.summaryText)
            statement.setLong(11, summary.createdAt)

            val result = statement.executeUpdate() > 0
            statement.close()
            result
        } catch (e: SQLException) {
            println("Error inserting scheduled summary: ${e.message}")
            false
        }
    }

    fun getLatest(): ScheduledSummaryEntity? {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("""
                SELECT * FROM scheduled_summaries 
                ORDER BY created_at DESC 
                LIMIT 1
            """)

            val summary = if (resultSet.next()) {
                mapToEntity(resultSet)
            } else {
                null
            }

            resultSet.close()
            statement.close()
            summary
        } catch (e: SQLException) {
            println("Error fetching latest scheduled summary: ${e.message}")
            null
        }
    }

    fun getAll(): List<ScheduledSummaryEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("SELECT * FROM scheduled_summaries ORDER BY created_at DESC")

            val summaries = mutableListOf<ScheduledSummaryEntity>()
            while (resultSet.next()) {
                summaries.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            summaries
        } catch (e: SQLException) {
            println("Error fetching all scheduled summaries: ${e.message}")
            emptyList()
        }
    }

    fun count(): Int {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("SELECT COUNT(*) FROM scheduled_summaries")

            val count = if (resultSet.next()) resultSet.getInt(1) else 0

            resultSet.close()
            statement.close()
            count
        } catch (e: SQLException) {
            println("Error counting scheduled summaries: ${e.message}")
            0
        }
    }

    private fun mapToEntity(resultSet: java.sql.ResultSet): ScheduledSummaryEntity {
        return ScheduledSummaryEntity(
            id = resultSet.getString("id"),
            period = resultSet.getString("period"),
            entriesCount = resultSet.getInt("entries_count"),
            avgWeight = resultSet.getDouble("avg_weight").takeIf { !resultSet.wasNull() },
            workoutsCompleted = resultSet.getInt("workouts_completed"),
            avgSteps = resultSet.getInt("avg_steps").takeIf { !resultSet.wasNull() },
            avgSleepHours = resultSet.getDouble("avg_sleep_hours").takeIf { !resultSet.wasNull() },
            avgProtein = resultSet.getInt("avg_protein").takeIf { !resultSet.wasNull() },
            adherenceScore = resultSet.getDouble("adherence_score"),
            summaryText = resultSet.getString("summary_text"),
            createdAt = resultSet.getLong("created_at")
        )
    }
}