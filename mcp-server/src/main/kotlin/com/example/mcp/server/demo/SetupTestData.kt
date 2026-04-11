package com.example.mcp.server.demo

import com.example.mcp.server.data.fitness.FitnessReminderRepository
import com.example.mcp.server.data.fitness.ReminderDatabase
import com.example.mcp.server.data.fitness.FitnessLogDao
import com.example.mcp.server.data.fitness.ScheduledSummaryDao
import com.example.mcp.server.data.fitness.ReminderDao
import com.example.mcp.server.data.fitness.ReminderEventDao
import com.example.mcp.server.model.fitness.FitnessLog
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.system.exitProcess

class SetupTestData(
    private val periodDays: Int = 7
) {
    private val database = ReminderDatabase()
    private val fitnessLogDao = FitnessLogDao(database)
    private val scheduledSummaryDao = ScheduledSummaryDao(database)
    private val reminderDao = ReminderDao(database)
    private val reminderEventDao = ReminderEventDao(database)
    
    private val repository = FitnessReminderRepository(
        database,
        fitnessLogDao,
        scheduledSummaryDao,
        reminderDao,
        reminderEventDao
    )

    fun setup() {
        runBlocking {
            println("\n" + "=".repeat(60))
            println("SETUP TEST DATA")
            println("=".repeat(60))
            println("📅 Period: $periodDays days")
            
            clearDatabase()
            
            when (periodDays) {
                7 -> add7DaysData()
                30 -> add30DaysData()
                else -> {
                    println("⚠️  Unsupported period: $periodDays days")
                    println("Supported periods: 7, 30")
                    exitProcess(1)
                }
            }
            
            verifyData()
            
            println("\n" + "=".repeat(60))
            println("✅ Test data setup completed!")
            println("=".repeat(60) + "\n")
        }
    }

    private suspend fun clearDatabase() {
        println("\n🧹 Step 1: Clearing database...")
        repository.clearAllData()
    }

    private suspend fun add7DaysData() {
        println("\n📊 Step 2: Adding test data for 7 days...")
        
        val today = LocalDate.now()
        val testData = generate7DaysData(today)
        
        var successCount = 0
        testData.forEach { log ->
            val success = repository.addFitnessLog(log)
            if (success) {
                successCount++
                println("   ✅ Day ${testData.size - testData.indexOf(log)}: ${log.date}, ${log.weight}kg, ${if (log.workoutCompleted) "workout" else "rest"}, ${log.steps} steps")
            } else {
                println("   ❌ Failed: ${log.date}")
            }
        }
        
        println("   📊 Added: $successCount/${testData.size} entries")
    }

    private suspend fun add30DaysData() {
        println("\n📊 Step 2: Adding test data for 30 days...")
        
        val today = LocalDate.now()
        val testData = generate30DaysData(today)
        
        var successCount = 0
        testData.chunked(5).forEachIndexed { chunkIndex, chunk ->
            println("   📊 Adding days ${(chunkIndex * 5 + 1)}-${(chunkIndex + 1) * 5}...")
            chunk.forEach { log ->
                val success = repository.addFitnessLog(log)
                if (success) {
                    successCount++
                }
            }
            println("      ${chunk.count { repository.addFitnessLog(it) }}/${chunk.size} added")
        }
        
        println("   📊 Total added: $successCount/${testData.size} entries")
    }

    private suspend fun verifyData() {
        println("\n🔍 Step 3: Verifying data...")
        
        val allLogs = repository.getAllFitnessLogs()
        val logsCount = allLogs.size
        
        println("   📊 Total logs in database: $logsCount")
        
        val workoutsCount = allLogs.count { it.workoutCompleted }
        println("   🏋️  Workouts completed: $workoutsCount")
        
        val avgWeight = allLogs.mapNotNull { it.weight }.average()
        if (!avgWeight.isNaN()) {
            println("   ⚖️  Average weight: ${String.format("%.2f", avgWeight)}kg")
        }
        
        if (logsCount < periodDays) {
            println("   ⚠️  Expected $periodDays entries, found $logsCount")
        } else {
            println("   ✅ All entries added successfully")
        }
    }

    public fun generate7DaysData(today: LocalDate): List<FitnessLog> {
        return listOf(
            FitnessLog(
                date = today.minusDays(6).format(DateTimeFormatter.ISO_DATE),
                weight = 82.5,
                calories = 2450,
                protein = 160,
                workoutCompleted = true,
                steps = 8200,
                sleepHours = 7.5,
                notes = "Тренировка ног"
            ),
            FitnessLog(
                date = today.minusDays(5).format(DateTimeFormatter.ISO_DATE),
                weight = 82.3,
                calories = 2600,
                protein = 175,
                workoutCompleted = false,
                steps = 6500,
                sleepHours = 6.8,
                notes = "День отдыха"
            ),
            FitnessLog(
                date = today.minusDays(4).format(DateTimeFormatter.ISO_DATE),
                weight = 82.1,
                calories = 2500,
                protein = 165,
                workoutCompleted = true,
                steps = 8800,
                sleepHours = 7.2,
                notes = "Тренировка спины"
            ),
            FitnessLog(
                date = today.minusDays(3).format(DateTimeFormatter.ISO_DATE),
                weight = 82.0,
                calories = 2550,
                protein = 170,
                workoutCompleted = true,
                steps = 9100,
                sleepHours = 7.4,
                notes = "Тренировка плеч"
            ),
            FitnessLog(
                date = today.minusDays(2).format(DateTimeFormatter.ISO_DATE),
                weight = 81.8,
                calories = 2400,
                protein = 155,
                workoutCompleted = false,
                steps = 7200,
                sleepHours = 6.5,
                notes = "День отдыха"
            ),
            FitnessLog(
                date = today.minusDays(1).format(DateTimeFormatter.ISO_DATE),
                weight = 81.9,
                calories = 2580,
                protein = 168,
                workoutCompleted = true,
                steps = 8500,
                sleepHours = 7.1,
                notes = "Тренировка груди"
            ),
            FitnessLog(
                date = today.format(DateTimeFormatter.ISO_DATE),
                weight = 81.7,
                calories = 2420,
                protein = 158,
                workoutCompleted = true,
                steps = 8900,
                sleepHours = 7.3,
                notes = "Тренировка рук"
            )
        )
    }

    private fun generate30DaysData(today: LocalDate): List<FitnessLog> {
        val data = mutableListOf<FitnessLog>()
        var currentWeight = 85.0
        
        for (week in 0 until 4) {
            for (day in 0 until 7) {
                val date = today.minusDays((3 - week) * 7L + (6 - day).toLong())
                val isWorkoutDay = day < 5 
                
                data.add(FitnessLog(
                    date = date.format(DateTimeFormatter.ISO_DATE),
                    weight = currentWeight,
                    calories = if (isWorkoutDay) 2500 else 2300,
                    protein = if (isWorkoutDay) 165 else 150,
                    workoutCompleted = isWorkoutDay,
                    steps = if (isWorkoutDay) 8500 else 7000,
                    sleepHours = if (isWorkoutDay) 7.5 else 8.0,
                    notes = when {
                        isWorkoutDay -> "Тренировка"
                        day == 5 -> "День отдыха"
                        else -> "Выходной"
                    }
                ))
                
                currentWeight -= (0.1 + Math.random() * 0.2)
            }
        }
        
        for (day in 0 until 2) {
            val date = today.minusDays(day.toLong())
            data.add(FitnessLog(
                date = date.format(DateTimeFormatter.ISO_DATE),
                weight = currentWeight,
                calories = 2450,
                protein = 158,
                workoutCompleted = day == 0,
                steps = if (day == 0) 8900 else 7200,
                sleepHours = 7.3,
                notes = if (day == 0) "Тренировка" else "День отдыха"
            ))
            currentWeight -= 0.1
        }
        
        return data
    }
}

suspend fun main(args: Array<String>) {
    val period = args.getOrElse(0) { "7" }.toIntOrNull() ?: 7
    
    if (period != 7 && period != 30) {
        println("❌ Invalid period: $period")
        println("Supported periods: 7, 30")
        println("Usage: ./gradlew :mcp-server:setupTestData -Pperiod=7")
        println("                    ./gradlew :mcp-server:setupTestData -Pperiod=30")
        exitProcess(1)
    }
    
    val setup = SetupTestData(period)
    setup.setup()
}
