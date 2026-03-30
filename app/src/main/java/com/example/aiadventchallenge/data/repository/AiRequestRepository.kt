package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.local.dao.AiRequestDao
import com.example.aiadventchallenge.domain.model.DialogTokenStats
import com.example.aiadventchallenge.domain.model.RequestType

class AiRequestRepository(
    private val aiRequestDao: AiRequestDao
) {
    suspend fun recordRequest(
        type: RequestType,
        model: String?,
        prompt: String?,
        response: String?,
        promptTokens: Int?,
        completionTokens: Int?,
        totalTokens: Int?
    ) {
        val entity = com.example.aiadventchallenge.data.local.entity.AiRequestEntity(
            id = "${type.name}_${System.currentTimeMillis()}",
            timestamp = System.currentTimeMillis(),
            requestType = type.name,
            model = model,
            prompt = prompt,
            response = response,
            promptTokens = promptTokens,
            completionTokens = completionTokens,
            totalTokens = totalTokens
        )
        aiRequestDao.insertRequest(entity)
    }

    suspend fun getAllTimeStats(): DialogTokenStats =
        aiRequestDao.getAllTimeStats()

    suspend fun clearAllRequests() =
        aiRequestDao.deleteAllRequests()

    suspend fun getRequestCount(): Int =
        aiRequestDao.getRequestCount()
}
