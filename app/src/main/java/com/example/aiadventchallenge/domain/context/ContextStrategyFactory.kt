package com.example.aiadventchallenge.domain.context

import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.FactRepository
import com.example.aiadventchallenge.data.repository.ChatRepository

class ContextStrategyFactory(
    private val factRepository: FactRepository,
    private val branchRepository: BranchRepository,
    private val factExtractor: FactExtractor,
    private val chatRepository: ChatRepository
) {

    fun create(config: ContextStrategyConfig): ContextStrategy {
        return when (config.type) {
            ContextStrategyType.SLIDING_WINDOW -> SlidingWindowStrategy(config)
            ContextStrategyType.STICKY_FACTS -> StickyFactsStrategy(config, factRepository, factExtractor)
            ContextStrategyType.BRANCHING -> BranchingStrategy(config, branchRepository, chatRepository)
            ContextStrategyType.MEMORY_BASED -> throw UnsupportedOperationException(
                "MemoryBasedStrategy was removed. Use SLIDING_WINDOW, STICKY_FACTS or BRANCHING instead."
            )
        }
    }
}
