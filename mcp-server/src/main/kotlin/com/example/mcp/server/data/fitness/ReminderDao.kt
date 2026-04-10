package com.example.mcp.server.data.fitness

import com.example.mcp.server.model.reminder.ReminderEntity
import java.sql.SQLException

class ReminderDao(private val database: ReminderDatabase) {

    fun insert(reminder: ReminderEntity): Boolean {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                INSERT OR REPLACE INTO reminders (
                    id, type, title, message, time, days_of_week, is_active, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)

            statement.setString(1, reminder.id)
            statement.setString(2, reminder.type)
            statement.setString(3, reminder.title)
            statement.setString(4, reminder.message)
            statement.setString(5, reminder.time)
            statement.setString(6, reminder.daysOfWeek)
            statement.setInt(7, if (reminder.isActive) 1 else 0)
            statement.setLong(8, reminder.createdAt)

            val result = statement.executeUpdate() > 0
            statement.close()
            result
        } catch (e: SQLException) {
            println("Error inserting reminder: ${e.message}")
            false
        }
    }

    fun getById(id: String): ReminderEntity? {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("SELECT * FROM reminders WHERE id = ?")
            statement.setString(1, id)
            val resultSet = statement.executeQuery()

            val reminder = if (resultSet.next()) {
                mapToEntity(resultSet)
            } else {
                null
            }

            resultSet.close()
            statement.close()
            reminder
        } catch (e: SQLException) {
            println("Error fetching reminder by id: ${e.message}")
            null
        }
    }

    fun getAllActive(): List<ReminderEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("""
                SELECT * FROM reminders 
                WHERE is_active = 1 
                ORDER BY time ASC
            """)

            val reminders = mutableListOf<ReminderEntity>()
            while (resultSet.next()) {
                reminders.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            reminders
        } catch (e: SQLException) {
            println("Error fetching active reminders: ${e.message}")
            emptyList()
        }
    }

    fun getAll(): List<ReminderEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("SELECT * FROM reminders ORDER BY created_at DESC")

            val reminders = mutableListOf<ReminderEntity>()
            while (resultSet.next()) {
                reminders.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            reminders
        } catch (e: SQLException) {
            println("Error fetching all reminders: ${e.message}")
            emptyList()
        }
    }

    fun delete(id: String): Boolean {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("DELETE FROM reminders WHERE id = ?")
            statement.setString(1, id)
            val result = statement.executeUpdate() > 0
            statement.close()
            result
        } catch (e: SQLException) {
            println("Error deleting reminder: ${e.message}")
            false
        }
    }

    fun count(): Int {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("SELECT COUNT(*) FROM reminders")

            val count = if (resultSet.next()) resultSet.getInt(1) else 0

            resultSet.close()
            statement.close()
            count
        } catch (e: SQLException) {
            println("Error counting reminders: ${e.message}")
            0
        }
    }

    private fun mapToEntity(resultSet: java.sql.ResultSet): ReminderEntity {
        return ReminderEntity(
            id = resultSet.getString("id"),
            type = resultSet.getString("type"),
            title = resultSet.getString("title"),
            message = resultSet.getString("message"),
            time = resultSet.getString("time"),
            daysOfWeek = resultSet.getString("days_of_week"),
            isActive = resultSet.getInt("is_active") == 1,
            createdAt = resultSet.getLong("created_at")
        )
    }
}