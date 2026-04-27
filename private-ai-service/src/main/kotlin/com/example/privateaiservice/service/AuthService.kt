package com.example.privateaiservice.service

class AuthService(
    private val expectedApiKey: String
) {
    fun authenticate(authorizationHeader: String?) {
        if (expectedApiKey.isBlank()) {
            throw UnauthorizedException("Private AI service API key is not configured on the server.")
        }

        val token = authorizationHeader
            ?.removePrefix("Bearer")
            ?.trim()
            .orEmpty()

        if (token != expectedApiKey) {
            throw UnauthorizedException()
        }
    }
}
