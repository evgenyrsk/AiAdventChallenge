package com.example.aiadventchallenge.data.mapper

import com.example.aiadventchallenge.data.local.entity.TaskEntity
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.TaskContext
import com.example.aiadventchallenge.domain.model.TaskPhase
import com.example.aiadventchallenge.domain.utils.JsonUtils

object TaskMapper {
    
    fun toDomain(entity: TaskEntity): TaskContext {
        return TaskContext(
            taskId = entity.taskId,
            query = entity.query,
            phase = TaskPhase.valueOf(entity.phase),
            currentStep = entity.currentStep,
            totalSteps = entity.totalSteps,
            currentAction = entity.currentAction,
            plan = JsonUtils.parseStringList(entity.plan),
            done = JsonUtils.parseStringList(entity.done),
            profile = FitnessProfileType.valueOf(entity.profile),
            isActive = entity.isActive,
            awaitingUserConfirmation = entity.awaitingConfirmation,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }
    
    fun toEntity(domain: TaskContext): TaskEntity {
        return TaskEntity(
            taskId = domain.taskId,
            query = domain.query,
            phase = domain.phase.name,
            currentStep = domain.currentStep,
            totalSteps = domain.totalSteps,
            currentAction = domain.currentAction,
            plan = JsonUtils.serializeStringList(domain.plan),
            done = JsonUtils.serializeStringList(domain.done),
            profile = domain.profile.name,
            isActive = domain.isActive,
            awaitingConfirmation = domain.awaitingUserConfirmation,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt
        )
    }
    
    fun toDomainList(entities: List<TaskEntity>): List<TaskContext> {
        return entities.map { toDomain(it) }
    }
}
