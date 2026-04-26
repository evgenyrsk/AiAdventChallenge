package com.example.privateaiservice.service

sealed class ServiceException(
    override val message: String,
    val statusCode: Int
) : RuntimeException(message)

class UnauthorizedException(message: String = "Invalid API key") : ServiceException(message, 401)
class ValidationException(message: String) : ServiceException(message, 400)
class RateLimitException(message: String = "Rate limit exceeded") : ServiceException(message, 429)
class UpstreamUnavailableException(message: String) : ServiceException(message, 502)
class UpstreamTimeoutException(message: String) : ServiceException(message, 504)
