package com.example.aiadventchallenge.domain.repository

import com.example.aiadventchallenge.domain.model.InvariantConfig

interface InvariantRepository {
    fun getInvariantConfig(): InvariantConfig
}
