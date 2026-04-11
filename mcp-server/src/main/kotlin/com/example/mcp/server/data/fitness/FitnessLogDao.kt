package com.example.mcp.server.data.fitness

import com.example.mcp.server.model.fitness.FitnessLogEntity
import java.sql.SQLException
import java.sql.Types

class FitnessLogDao(private val database: ReminderDatabase) {

    fun insert(log: FitnessLogEntity): Boolean {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                INSERT OR REPLACE INTO fitness_logs (
                    id, date, weight, calories, protein, workout_completed,
                    steps, sleep_hours, notes, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)

            val cleanDate = log.date.trim().removeSurrounding("\"")
            statement.setString(1, log.id)
            statement.setString(2, cleanDate)
            
            if (log.weight != null) {
                statement.setDouble(3, log.weight)
            } else {
                statement.setNull(3, Types.REAL)
            }
            
            if (log.calories != null) {
                statement.setInt(4, log.calories)
            } else {
                statement.setNull(4, Types.INTEGER)
            }
            
            if (log.protein != null) {
                statement.setInt(5, log.protein)
            } else {
                statement.setNull(5, Types.INTEGER)
            }
            
            statement.setInt(6, if (log.workoutCompleted) 1 else 0)
            
            if (log.steps != null) {
                statement.setInt(7, log.steps)
            } else {
                statement.setNull(7, Types.INTEGER)
            }
            
            if (log.sleepHours != null) {
                statement.setDouble(8, log.sleepHours)
            } else {
                statement.setNull(8, Types.REAL)
            }
            
            statement.setString(9, log.notes)
            statement.setLong(10, log.createdAt)

            val result = statement.executeUpdate() > 0
            statement.close()
            result
        } catch (e: SQLException) {
            println("Error inserting fitness log: ${e.message}")
            false
        }
    }

    fun getAll(): List<FitnessLogEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("SELECT * FROM fitness_logs ORDER BY date ASC")

            val logs = mutableListOf<FitnessLogEntity>()
            while (resultSet.next()) {
                logs.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            logs
        } catch (e: SQLException) {
            println("Error fetching fitness logs: ${e.message}")
            emptyList()
        }
    }

    fun getByDateRange(startDate: String, endDate: String): List<FitnessLogEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                SELECT * FROM fitness_logs 
                WHERE date >= ? AND date <= ? 
                ORDER BY date ASC
            """)

            statement.setString(1, startDate)
            statement.setString(2, endDate)
            val resultSet = statement.executeQuery()

            val logs = mutableListOf<FitnessLogEntity>()
            while (resultSet.next()) {
                logs.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            logs
        } catch (e: SQLException) {
            println("Error fetching fitness logs by date range: ${e.message}")
            emptyList()
        }
    }

    fun getLastNDays(days: Int): List<FitnessLogEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.prepareStatement("""
                SELECT * FROM fitness_logs 
                WHERE date >= date('now', '-$days days')
                ORDER BY date ASC
            """)

            val resultSet = statement.executeQuery()

            val logs = mutableListOf<FitnessLogEntity>()
            while (resultSet.next()) {
                logs.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            logs
        } catch (e: SQLException) {
            println("Error fetching last N days fitness logs: ${e.message}")
            emptyList()
        }
    }

    fun count(): Int {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("SELECT COUNT(*) FROM fitness_logs")
            
            val count = if (resultSet.next()) resultSet.getInt(1) else 0
            
            resultSet.close()
            statement.close()
            count
        } catch (e: SQLException) {
            println("Error counting fitness logs: ${e.message}")
            0
        }
    }
    
    fun clear(): Boolean {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val result = statement.executeUpdate("DELETE FROM fitness_logs")
            
            statement.close()
            println("✅ Cleared $result fitness logs")
            true
        } catch (e: SQLException) {
            println("❌ Error clearing fitness logs: ${e.message}")
            false
        }
    }
    
    fun getAllSortedByDateDesc(): List<FitnessLogEntity> {
        return try {
            val conn = database.getConnection()
            val statement = conn.createStatement()
            val resultSet = statement.executeQuery("SELECT * FROM fitness_logs ORDER BY date DESC")

            val logs = mutableListOf<FitnessLogEntity>()
            while (resultSet.next()) {
                logs.add(mapToEntity(resultSet))
            }

            resultSet.close()
            statement.close()
            logs
        } catch (e: SQLException) {
            println("Error fetching fitness logs sorted by date: ${e.message}")
            emptyList()
        }
    }

    private fun mapToEntity(resultSet: java.sql.ResultSet): FitnessLogEntity {
        return FitnessLogEntity(
            id = resultSet.getString("id"),
            date = resultSet.getString("date"),
            weight = resultSet.getDouble("weight").takeIf { !resultSet.wasNull() },
            calories = resultSet.getInt("calories").takeIf { !resultSet.wasNull() },
            protein = resultSet.getInt("protein").takeIf { !resultSet.wasNull() },
            workoutCompleted = resultSet.getInt("workout_completed") == 1,
            steps = resultSet.getInt("steps").takeIf { !resultSet.wasNull() },
            sleepHours = resultSet.getDouble("sleep_hours").takeIf { !resultSet.wasNull() },
            notes = resultSet.getString("notes").takeIf { !resultSet.wasNull() },
            createdAt = resultSet.getLong("created_at")
        )
    }
}
