package com.example.aiadventchallenge.domain.parser

import com.example.aiadventchallenge.data.config.EnhancedTaskAiResponse

class UserResponseParserImpl : UserResponseParser {
    
    private val affirmativeKeywords = listOf(
        "да", "утверждаю", "хорошо", "согласен", "yes", "ок", "окей",
        "давай", "отлично", "супер", "конечно", "разумеется", "несомненно",
        "ладно", "норм", "нормально", "го", "погнали", "поехали",
        "ясно", "принято", "записано", "принимаю", "выглядит неплохо",
        "выглядит хорошо", "вроде бы", "думаю да", "наверное да", "пожалуй",
        "ну ладно", "ну ок", "ну давай", "окей давай", "ого круто", "класс"
    )
    
    private val disagreementKeywords = listOf(
        "нет", "не нравится", "не устраивает", "хочу изменить",
        "переделай", "исправь", "это неправильно", "плохо",
        "неудачно", "не то", "другой вариант", "изменить", "измени",
        "переделать", "переделай", "не работает", "сложно", "слишком сложно"
    )
    
    override fun isAffirmative(input: String): Boolean {
        val responseText = input.lowercase()
        return affirmativeKeywords.any { it in responseText }
    }
    
    override fun isDisagreement(aiResponse: EnhancedTaskAiResponse): Boolean {
        val responseText = aiResponse.result.lowercase()
        return disagreementKeywords.any { it in responseText }
    }
}
