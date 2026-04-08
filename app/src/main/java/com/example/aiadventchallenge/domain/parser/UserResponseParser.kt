package com.example.aiadventchallenge.domain.parser

import com.example.aiadventchallenge.data.config.EnhancedTaskAiResponse

interface UserResponseParser {
    /**
     * Определяет, является ли ответ пользователя утвердительным.
     * 
     * @param input Текст ответа пользователя
     * @return true если ответ содержит утвердительное подтверждение
     */
    fun isAffirmative(input: String): Boolean
    
    /**
     * Определяет, содержит ли AI ответ признаки несогласия пользователя.
     * 
     * @param aiResponse Ответ от LLM
     * @return true если в ответе есть признаки несогласия
     */
    fun isDisagreement(aiResponse: EnhancedTaskAiResponse): Boolean
}
