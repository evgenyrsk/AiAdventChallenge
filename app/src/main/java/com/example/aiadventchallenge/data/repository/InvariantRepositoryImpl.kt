package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.domain.repository.InvariantRepository
import com.example.aiadventchallenge.domain.model.InvariantConfig
import com.example.aiadventchallenge.domain.model.Invariant

class InvariantRepositoryImpl() : InvariantRepository {
    override fun getInvariantConfig(): InvariantConfig {
        return InvariantConfig(invariants = emptyList())
    }
}
