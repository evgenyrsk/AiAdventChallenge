package com.example.mcp.server.data.task

import com.example.mcp.server.data.fitness.FitnessDatabase
import com.example.mcp.server.model.task.ScheduleType
import com.example.mcp.server.model.task.ScheduledTaskEntity
import com.example.mcp.server.model.task.TaskStatus
import com.example.mcp.server.model.task.TaskType
import java.sql.SQLException

class ScheduledTaskDao(private val database: FitnessDatabase) {

    fun insert(task: ScheduledTaskEntity): Boolean {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                INSERT OR REPLACE INTO scheduled_tasks (
                    id, type, schedule_type, message, delay_minutes,
                    scheduled_time, period_minutes, created_at, status,
                    executed_at, error_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)

            statement.setString(1, task.id)
            statement.setString(2, task.type.name)
            statement.setString(3, task.scheduleType.name)
            statement.setString(4, task.message)
            task.delayMinutes?.let { statement.setInt(5, it) } ?: statement.setNull(5, java.sql.Types.INTEGER)
            task.scheduledTime?.let { statement.setLong(6, it) } ?: statement.setNull(6, java.sql.Types.INTEGER)
            task.periodMinutes?.let { statement.setInt(7, it) } ?: statement.setNull(7, java.sql.Types.INTEGER)
            statement.setLong(8, task.createdAt)
            statement.setString(9, task.status.name)
            task.executedAt?.let { statement.setLong(10, it) } ?: statement.setNull(10, java.sql.Types.INTEGER)
            task.errorMessage?.let { statement.setString(11, it) } ?: statement.setNull(11, java.sql.Types.VARCHAR)

            val result = statement.executeUpdate() > 0
            statement.close()
            result
        } catch (e: SQLException) {
            println("Error inserting scheduled task: ${e.message}")
            false
        }
    }

    fun getById(id: String): ScheduledTaskEntity? {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("""
                SELECT * FROM scheduled_tasks WHERE id = '$id'
            """)

            val task = if (resultSet.next()) {
                mapToEntity(resultSet)
            } else {
                null
            }

            resultSet.close()
            statement.close()
            task
        } catch (e: SQLException) {
            println("Error fetching scheduled task: ${e.message}")
            null
        }
    }

    fun getPendingTasks(): List<ScheduledTaskEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("""
                SELECT * FROM scheduled_tasks 
                WHERE status = 'PENDING'
                ORDER BY created_at ASC
            """)

            val tasks = mutableListOf<ScheduledTaskEntity>()
            while (resultSet.next()) {
                tasks.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            tasks
        } catch (e: SQLException) {
            println("Error fetching pending tasks: ${e.message}")
            emptyList()
        }
    }

    fun getPendingTasksDueNow(): List<ScheduledTaskEntity> {
        return try {
            val conn = database.getConnection()
            val currentTime = System.currentTimeMillis()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("""
                SELECT * FROM scheduled_tasks 
                WHERE status = 'PENDING'
                AND (
                    (scheduled_time IS NOT NULL AND scheduled_time <= $currentTime)
                    OR (delay_minutes IS NOT NULL AND created_at + (delay_minutes * 60000) <= $currentTime)
                )
                ORDER BY created_at ASC
            """)

            val tasks = mutableListOf<ScheduledTaskEntity>()
            while (resultSet.next()) {
                tasks.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            tasks
        } catch (e: SQLException) {
            println("Error fetching pending tasks due now: ${e.message}")
            emptyList()
        }
    }

    fun updateStatus(id: String, status: TaskStatus, executedAt: Long? = null, errorMessage: String? = null): Boolean {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                UPDATE scheduled_tasks 
                SET status = ?, executed_at = ?, error_message = ?
                WHERE id = ?
            """)

            statement.setString(1, status.name)
            executedAt?.let { statement.setLong(2, it) } ?: statement.setNull(2, java.sql.Types.INTEGER)
            errorMessage?.let { statement.setString(3, it) } ?: statement.setNull(3, java.sql.Types.VARCHAR)
            statement.setString(4, id)

            val result = statement.executeUpdate() > 0
            statement.close()
            result
        } catch (e: SQLException) {
            println("Error updating task status: ${e.message}")
            false
        }
    }

    fun getAll(): List<ScheduledTaskEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("SELECT * FROM scheduled_tasks ORDER BY created_at DESC")

            val tasks = mutableListOf<ScheduledTaskEntity>()
            while (resultSet.next()) {
                tasks.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            tasks
        } catch (e: SQLException) {
            println("Error fetching all tasks: ${e.message}")
            emptyList()
        }
    }

    fun count(): Int {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("SELECT COUNT(*) FROM scheduled_tasks")

            val count = if (resultSet.next()) resultSet.getInt(1) else 0

            resultSet.close()
            statement.close()
            count
        } catch (e: SQLException) {
            println("Error counting tasks: ${e.message}")
            0
        }
    }

    private fun mapToEntity(resultSet: java.sql.ResultSet): ScheduledTaskEntity {
        val typeStr = resultSet.getString("type")
        val scheduleTypeStr = resultSet.getString("schedule_type")
        val statusStr = resultSet.getString("status")

        return ScheduledTaskEntity(
            id = resultSet.getString("id"),
            type = TaskType.valueOf(typeStr),
            scheduleType = ScheduleType.valueOf(scheduleTypeStr),
            message = resultSet.getString("message"),
            delayMinutes = resultSet.getInt("delay_minutes").takeIf { !resultSet.wasNull() },
            scheduledTime = resultSet.getLong("scheduled_time").takeIf { !resultSet.wasNull() },
            periodMinutes = resultSet.getInt("period_minutes").takeIf { !resultSet.wasNull() },
            createdAt = resultSet.getLong("created_at"),
            status = TaskStatus.valueOf(statusStr),
            executedAt = resultSet.getLong("executed_at").takeIf { !resultSet.wasNull() },
            errorMessage = resultSet.getString("error_message")
        )
    }
}
