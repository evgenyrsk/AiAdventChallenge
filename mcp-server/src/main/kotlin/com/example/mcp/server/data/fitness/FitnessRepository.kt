package com.example.mcp.server.data.fitness

import com.example.mcp.server.model.fitness.FitnessLog
import com.example.mcp.server.model.fitness.FitnessLogEntity
import com.example.mcp.server.model.fitness.ScheduledSummary
import com.example.mcp.server.model.fitness.ScheduledSummaryEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class FitnessRepository(
    private val fitnessLogDao: FitnessLogDao,
    private val scheduledSummaryDao: ScheduledSummaryDao
) {

    fun addFitnessLog(log: FitnessLog): Boolean {
        val entity = log.toEntity()
        return fitnessLogDao.insert(entity)
    }

    fun getAllFitnessLogs(): List<FitnessLogEntity> {
        return fitnessLogDao.getAll()
    }

    fun getFitnessLogsByDateRange(startDate: String, endDate: String): List<FitnessLogEntity> {
        return fitnessLogDao.getByDateRange(startDate, endDate)
    }

    fun getLastNDaysFitnessLogs(days: Int): List<FitnessLogEntity> {
        val allLogs = fitnessLogDao.getAllSortedByDateDesc()

        if (allLogs.isEmpty()) {
            return emptyList()
        }

        val formatter = DateTimeFormatter.ISO_DATE
        val latestDate = LocalDate.parse(allLogs.first().date, formatter)
        val startDate = latestDate.minusDays(days.toLong() - 1)

        return allLogs.filter { log ->
            val logDate = LocalDate.parse(log.date, formatter)
            logDate >= startDate && logDate <= latestDate
        }.sortedBy { it.date }
    }

    fun addScheduledSummary(summary: ScheduledSummary): Boolean {
        val entity = summary.toEntity()
        return scheduledSummaryDao.insert(entity)
    }

    fun getLatestScheduledSummary(): ScheduledSummary? {
        val entity = scheduledSummaryDao.getLatest()
        return entity?.toDomain()
    }

    fun getAllScheduledSummaries(): List<ScheduledSummary> {
        return scheduledSummaryDao.getAll().map { it.toDomain() }
    }

    fun getFitnessLogsCount(): Int {
        return fitnessLogDao.count()
    }

    fun getScheduledSummariesCount(): Int {
        return scheduledSummaryDao.count()
    }
    
    fun clearLogs(): Boolean {
        return fitnessLogDao.clear()
    }
    
    fun clearScheduledSummaries(): Boolean {
        return scheduledSummaryDao.clear()
    }
}
