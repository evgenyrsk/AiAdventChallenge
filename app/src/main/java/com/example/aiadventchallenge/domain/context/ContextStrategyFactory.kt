package com.example.aiadventchallenge.domain.context

import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.FactRepository
import com.example.aiadventchallenge.domain.repository.MemoryRepository
import com.example.aiadventchallenge.domain.repository.AiRepository
import com.example.aiadventchallenge.domain.repository.MemoryClassificationRepository
import com.example.aiadventchallenge.domain.memory.AiMemoryClassifier
import com.example.aiadventchallenge.data.repository.ChatRepository

class ContextStrategyFactory(
    private val factRepository: FactRepository,
    private val branchRepository: BranchRepository,
    private val factExtractor: FactExtractor,
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val aiRepository: AiRepository,
    private val classificationRepository: MemoryClassificationRepository
) {

    private val aiMemoryClassifier by lazy {
        AiMemoryClassifier(aiRepository)
    }

    fun create(config: ContextStrategyConfig): ContextStrategy {
        return when (config.type) {
            ContextStrategyType.SLIDING_WINDOW -> SlidingWindowStrategy(config)
            ContextStrategyType.STICKY_FACTS -> StickyFactsStrategy(config, factRepository, factExtractor)
            ContextStrategyType.BRANCHING -> BranchingStrategy(config, branchRepository, chatRepository)
            ContextStrategyType.MEMORY_BASED -> MemoryBasedStrategy(
                config,
                memoryRepository,
                chatRepository,
                aiMemoryClassifier,
                classificationRepository
            )
        }
    }
}