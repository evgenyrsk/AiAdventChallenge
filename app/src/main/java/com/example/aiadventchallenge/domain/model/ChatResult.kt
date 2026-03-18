package com.example.aiadventchallenge.domain.model

sealed interface ChatResult<out T> {
    data class Success<T>(val data: T) : ChatResult<T>
    data class Error(val message: String, val code: Int? = null) : ChatResult<Nothing>
}
