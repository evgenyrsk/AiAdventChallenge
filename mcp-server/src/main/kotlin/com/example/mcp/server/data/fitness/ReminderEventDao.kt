package com.example.mcp.server.data.fitness

import com.example.mcp.server.model.reminder.ReminderEventEntity
import java.sql.SQLException

class ReminderEventDao(private val database: ReminderDatabase) {

    fun insert(event: ReminderEventEntity): Boolean {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                INSERT OR REPLACE INTO reminder_events (
                    id, reminder_id, type, scheduled_time, triggered_at, 
                    status, context, response
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)

            statement.setString(1, event.id)
            statement.setString(2, event.reminderId)
            statement.setString(3, event.type)
            statement.setLong(4, event.scheduledTime)
            statement.setLong(5, event.triggeredAt ?: 0)
            statement.setString(6, event.status)
            statement.setString(7, event.context)
            statement.setString(8, event.response)

            val result = statement.executeUpdate() > 0
            statement.close()
            result
        } catch (e: SQLException) {
            println("Error inserting reminder event: ${e.message}")
            false
        }
    }

    fun getById(id: String): ReminderEventEntity? {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("SELECT * FROM reminder_events WHERE id = ?")
            statement.setString(1, id)
            val resultSet = statement.executeQuery()

            val event = if (resultSet.next()) {
                mapToEntity(resultSet)
            } else {
                null
            }

            resultSet.close()
            statement.close()
            event
        } catch (e: SQLException) {
            println("Error fetching reminder event by id: ${e.message}")
            null
        }
    }

    fun getByReminderId(reminderId: String, limit: Int = 10): List<ReminderEventEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                SELECT * FROM reminder_events 
                WHERE reminder_id = ? 
                ORDER BY scheduled_time DESC 
                LIMIT ?
            """)
            statement.setString(1, reminderId)
            statement.setInt(2, limit)
            val resultSet = statement.executeQuery()

            val events = mutableListOf<ReminderEventEntity>()
            while (resultSet.next()) {
                events.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            events
        } catch (e: SQLException) {
            println("Error fetching reminder events by reminder id: ${e.message}")
            emptyList()
        }
    }

    fun getPendingEvents(): List<ReminderEventEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("""
                SELECT * FROM reminder_events 
                WHERE status = 'PENDING'
                ORDER BY scheduled_time ASC
            """)

            val events = mutableListOf<ReminderEventEntity>()
            while (resultSet.next()) {
                events.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            events
        } catch (e: SQLException) {
            println("Error fetching pending reminder events: ${e.message}")
            emptyList()
        }
    }

    fun getEventsInDateRange(startTime: Long, endTime: Long): List<ReminderEventEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                SELECT * FROM reminder_events 
                WHERE scheduled_time >= ? AND scheduled_time <= ?
                ORDER BY scheduled_time DESC
            """)
            statement.setLong(1, startTime)
            statement.setLong(2, endTime)
            val resultSet = statement.executeQuery()

            val events = mutableListOf<ReminderEventEntity>()
            while (resultSet.next()) {
                events.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            events
        } catch (e: SQLException) {
            println("Error fetching reminder events in date range: ${e.message}")
            emptyList()
        }
    }

    fun updateStatus(id: String, status: String, triggeredAt: Long?): Boolean {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                UPDATE reminder_events 
                SET status = ?, triggered_at = ? 
                WHERE id = ?
            """)
            statement.setString(1, status)
            statement.setLong(2, triggeredAt ?: 0)
            statement.setString(3, id)
            val result = statement.executeUpdate() > 0
            statement.close()
            result
        } catch (e: SQLException) {
            println("Error updating reminder event status: ${e.message}")
            false
        }
    }

    fun updateResponse(id: String, response: String?): Boolean {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                UPDATE reminder_events 
                SET response = ? 
                WHERE id = ?
            """)
            statement.setString(1, response)
            statement.setString(2, id)
            val result = statement.executeUpdate() > 0
            statement.close()
            result
        } catch (e: SQLException) {
            println("Error updating reminder event response: ${e.message}")
            false
        }
    }

    fun delete(id: String): Boolean {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("DELETE FROM reminder_events WHERE id = ?")
            statement.setString(1, id)
            val result = statement.executeUpdate() > 0
            statement.close()
            result
        } catch (e: SQLException) {
            println("Error deleting reminder event: ${e.message}")
            false
        }
    }

    fun count(): Int {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("SELECT COUNT(*) FROM reminder_events")

            val count = if (resultSet.next()) resultSet.getInt(1) else 0

            resultSet.close()
            statement.close()
            count
        } catch (e: SQLException) {
            println("Error counting reminder events: ${e.message}")
            0
        }
    }

    private fun mapToEntity(resultSet: java.sql.ResultSet): ReminderEventEntity {
        val triggeredAt = resultSet.getLong("triggered_at").takeIf { !resultSet.wasNull() }
        return ReminderEventEntity(
            id = resultSet.getString("id"),
            reminderId = resultSet.getString("reminder_id"),
            type = resultSet.getString("type"),
            scheduledTime = resultSet.getLong("scheduled_time"),
            triggeredAt = if (triggeredAt != null && triggeredAt > 0) triggeredAt else null,
            status = resultSet.getString("status"),
            context = resultSet.getString("context").takeIf { !resultSet.wasNull() },
            response = resultSet.getString("response").takeIf { !resultSet.wasNull() }
        )
    }
}