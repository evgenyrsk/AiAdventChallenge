package com.example.aiadventchallenge.rag.rewrite

enum class RewriteIntent {
    UNKNOWN,
    FAT_LOSS_PRIORITY,
    PROTEIN_CUTTING,
    SLEEP_RECOVERY_APPETITE,
    CARDIO_VS_FAT_LOSS,
    LIQUID_CALORIES,
    TRAINING_TO_FAILURE,
    BEGINNER_FREQUENCY,
    STEPS_ENERGY_EXPENDITURE
}

enum class RewriteStrategy {
    IDENTITY,
    FILLER_CLEANUP,
    INTENT_EXPANSION,
    SEARCH_COMPACTION
}

data class RewriteResult(
    val originalQuery: String,
    val rewrittenQuery: String? = null,
    val applied: Boolean,
    val detectedIntent: RewriteIntent = RewriteIntent.UNKNOWN,
    val strategy: RewriteStrategy = RewriteStrategy.IDENTITY,
    val addedTerms: List<String> = emptyList(),
    val removedPhrases: List<String> = emptyList()
) {
    val effectiveQuery: String
        get() = rewrittenQuery ?: originalQuery
}

object FitnessQueryRewriteEngine {

    fun analyze(query: String): RewriteResult {
        val normalizedOriginal = normalize(query)
        if (normalizedOriginal.isBlank()) {
            return RewriteResult(
                originalQuery = query,
                rewrittenQuery = null,
                applied = false
            )
        }

        val fillerCleanup = removeConversationalFiller(normalizedOriginal)
        val cleaned = fillerCleanup.cleaned
        val detectedIntent = detectIntent(cleaned)
        val candidateTerms = when (detectedIntent) {
            RewriteIntent.UNKNOWN -> emptyList()
            else -> composeTerms(detectedIntent, cleaned)
        }

        val addedTerms = candidateTerms.filterNot { normalizedOriginal.contains(it, ignoreCase = true) }
        val cleanedDiffers = !cleaned.equals(normalizedOriginal, ignoreCase = true)
        val alreadyConcrete = isAlreadyConcrete(cleaned, detectedIntent, addedTerms)

        val rewriteCandidate = when {
            detectedIntent != RewriteIntent.UNKNOWN && !alreadyConcrete -> {
                candidateTerms.joinToString(" ").trim()
            }
            cleanedDiffers -> cleaned
            else -> null
        }

        val helpful = rewriteCandidate != null &&
            rewriteCandidate.isNotBlank() &&
            !rewriteCandidate.equals(normalizedOriginal, ignoreCase = true) &&
            isHelpfulRewrite(
                originalQuery = normalizedOriginal,
                rewriteCandidate = rewriteCandidate,
                addedTerms = addedTerms,
                removedPhrases = fillerCleanup.removedPhrases
            )

        val finalRewrite = rewriteCandidate
            ?.takeIf { helpful }
            ?.replaceFirstChar { char -> char.uppercase() }

        val strategy = when {
            finalRewrite == null -> RewriteStrategy.IDENTITY
            detectedIntent != RewriteIntent.UNKNOWN && addedTerms.isNotEmpty() -> RewriteStrategy.INTENT_EXPANSION
            cleanedDiffers -> RewriteStrategy.FILLER_CLEANUP
            else -> RewriteStrategy.SEARCH_COMPACTION
        }

        return RewriteResult(
            originalQuery = normalizedOriginal,
            rewrittenQuery = finalRewrite,
            applied = finalRewrite != null,
            detectedIntent = detectedIntent,
            strategy = strategy,
            addedTerms = addedTerms,
            removedPhrases = fillerCleanup.removedPhrases
        )
    }

    private fun normalize(query: String): String {
        return query
            .trim()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[!?.,:;]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun removeConversationalFiller(query: String): FillerCleanupResult {
        var cleaned = query.lowercase()
        val removed = mutableListOf<String>()

        fillerPatterns.forEach { phrase ->
            if (cleaned.contains(phrase)) {
                removed += phrase
                cleaned = cleaned.replace(phrase, " ")
            }
        }

        cleaned = cleaned
            .replace(Regex("\\b(мне|вообще|просто|пожалуйста|как бы|ли)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return FillerCleanupResult(
            cleaned = cleaned,
            removedPhrases = removed.distinct()
        )
    }

    private fun detectIntent(cleaned: String): RewriteIntent {
        val scores = intentRules.mapValues { (_, rule) ->
            scoreIntent(cleaned, rule)
        }

        val best = scores.maxByOrNull { it.value } ?: return RewriteIntent.UNKNOWN
        return if (best.value < 2) RewriteIntent.UNKNOWN else best.key
    }

    private fun scoreIntent(cleaned: String, rule: IntentRule): Int {
        val tokens = tokenize(cleaned)
        var score = 0
        rule.signalPhrases.forEach { phrase ->
            if (cleaned.contains(phrase)) {
                score += 3
            }
        }
        rule.signalTokens.forEach { token ->
            if (tokens.contains(token)) {
                score += 1
            }
        }
        return score
    }

    private fun composeTerms(intent: RewriteIntent, cleaned: String): List<String> {
        val rule = intentRules.getValue(intent)
        val baseTerms = rule.templateTerms.toMutableList()

        if (intent == RewriteIntent.FAT_LOSS_PRIORITY && cleaned.contains("время")) {
            baseTerms += "время приема пищи"
        }
        if (intent == RewriteIntent.CARDIO_VS_FAT_LOSS && cleaned.contains("шаг")) {
            baseTerms += "шаги"
        }
        if (intent == RewriteIntent.BEGINNER_FREQUENCY && cleaned.contains("нович")) {
            baseTerms += "новичок"
        }

        return baseTerms.distinct()
    }

    private fun isAlreadyConcrete(
        cleaned: String,
        intent: RewriteIntent,
        addedTerms: List<String>
    ): Boolean {
        if (intent == RewriteIntent.UNKNOWN) return false
        val tokenCount = tokenize(cleaned).size
        return tokenCount <= 8 && addedTerms.isEmpty()
    }

    private fun isHelpfulRewrite(
        originalQuery: String,
        rewriteCandidate: String,
        addedTerms: List<String>,
        removedPhrases: List<String>
    ): Boolean {
        if (rewriteCandidate.equals(originalQuery, ignoreCase = true)) return false
        if (rewriteCandidate.equals("$originalQuery fitness knowledge", ignoreCase = true)) return false
        if (addedTerms.isEmpty() && removedPhrases.isEmpty()) return false
        return true
    }

    private fun tokenize(text: String): Set<String> = text
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}_]+"))
        .filter { it.length >= 3 }
        .toSet()

    private data class FillerCleanupResult(
        val cleaned: String,
        val removedPhrases: List<String>
    )

    private data class IntentRule(
        val signalPhrases: List<String>,
        val signalTokens: List<String>,
        val templateTerms: List<String>
    )

    private val fillerPatterns = listOf(
        "подскажи пожалуйста",
        "подскажи",
        "скажи пожалуйста",
        "скажи",
        "можешь объяснить",
        "можешь подсказать",
        "объясни пожалуйста",
        "объясни",
        "расскажи",
        "мне интересно",
        "хочу понять",
        "не очень понимаю",
        "как вообще",
        "можно ли"
    )

    private val intentRules = mapOf(
        RewriteIntent.FAT_LOSS_PRIORITY to IntentRule(
            signalPhrases = listOf("что важнее для похудения", "время приёма пищи", "время приема пищи", "тайминг питания"),
            signalTokens = listOf("похудения", "похудеть", "дефицит", "калорий", "тайминг"),
            templateTerms = listOf("дефицит калорий", "energy balance", "meal timing", "время приема пищи")
        ),
        RewriteIntent.PROTEIN_CUTTING to IntentRule(
            signalPhrases = listOf("сколько белка", "сохранить мышцы"),
            signalTokens = listOf("белка", "белок", "мышцы", "похудении", "дефиците"),
            templateTerms = listOf("protein intake", "белок", "1.6-2.2 г/кг", "сохранение мышц", "дефицит калорий")
        ),
        RewriteIntent.SLEEP_RECOVERY_APPETITE to IntentRule(
            signalPhrases = listOf("контроль аппетита", "почему сон влияет", "влияет на восстановление"),
            signalTokens = listOf("сон", "восстановление", "аппетита", "аппетит", "недосып"),
            templateTerms = listOf("сон", "восстановление", "аппетит", "качество тренировки", "недосып")
        ),
        RewriteIntent.CARDIO_VS_FAT_LOSS to IntentRule(
            signalPhrases = listOf("без кардио", "можно ли худеть без кардио"),
            signalTokens = listOf("кардио", "похудеть", "похудения", "дефицит"),
            templateTerms = listOf("кардио", "дефицит калорий", "расход энергии", "снижение веса")
        ),
        RewriteIntent.LIQUID_CALORIES to IntentRule(
            signalPhrases = listOf("жидкие калории", "мешать снижению веса"),
            signalTokens = listOf("жидкие", "калории", "насыщают", "снижения"),
            templateTerms = listOf("жидкие калории", "насыщение", "калораж", "снижение веса")
        ),
        RewriteIntent.TRAINING_TO_FAILURE to IntentRule(
            signalPhrases = listOf("до отказа", "роста мышц"),
            signalTokens = listOf("отказа", "отказ", "мышц", "гипертрофия"),
            templateTerms = listOf("тренировки до отказа", "гипертрофия", "объём", "прогрессия")
        ),
        RewriteIntent.BEGINNER_FREQUENCY to IntentRule(
            signalPhrases = listOf("сколько тренировок в неделю", "достаточно новичку"),
            signalTokens = listOf("новичку", "новичок", "тренировок", "неделю"),
            templateTerms = listOf("новичок", "2-4 тренировки", "регулярность", "силовые тренировки")
        ),
        RewriteIntent.STEPS_ENERGY_EXPENDITURE to IntentRule(
            signalPhrases = listOf("увеличение шагов", "общий расход энергии"),
            signalTokens = listOf("шагов", "шаги", "энергии", "расход"),
            templateTerms = listOf("шаги", "neat", "ежедневная активность", "расход энергии")
        )
    )
}
