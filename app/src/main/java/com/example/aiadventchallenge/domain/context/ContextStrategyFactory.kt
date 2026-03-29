package com.example.aiadventchallenge.domain.context

import com.example.aiadventchallenge.domain.model.ContextStrategyConfig
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.repository.BranchRepository
import com.example.aiadventchallenge.domain.repository.FactRepository

class ContextStrategyFactory(
    private val factRepository: FactRepository,
    private val branchRepository: BranchRepository
) {
    fun create(config: ContextStrategyConfig): ContextStrategy {
        return when (config.type) {
            ContextStrategyType.SLIDING_WINDOW -> SlidingWindowStrategy(config)
            ContextStrategyType.STICKY_FACTS -> StickyFactsStrategy(config, factRepository)
            ContextStrategyType.BRANCHING -> BranchingStrategy(config, branchRepository)
        }
    }
}
