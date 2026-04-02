package com.example.aiadventchallenge.data.repository

import com.example.aiadventchallenge.data.invariant.FitnessInvariants
import com.example.aiadventchallenge.domain.model.InvariantConfig
import com.example.aiadventchallenge.domain.repository.InvariantRepository

class InvariantRepositoryImpl : InvariantRepository {

    override fun getInvariantConfig(): InvariantConfig {
        return FitnessInvariants.createDefaultConfig()
    }
}
