package com.example.supportassistant.service

sealed class SupportException(
    override val message: String,
    val statusCode: Int
) : RuntimeException(message)

class InvalidSupportRequestException(message: String) : SupportException(message, 400)
class SupportDataUnavailableException(message: String) : SupportException(message, 503)
class SupportLlmUnavailableException(message: String) : RuntimeException(message)
