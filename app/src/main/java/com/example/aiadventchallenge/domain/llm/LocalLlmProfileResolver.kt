package com.example.aiadventchallenge.domain.llm

import com.example.aiadventchallenge.data.config.Prompts
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.LocalLlmConfig
import com.example.aiadventchallenge.domain.model.LocalLlmExecutionSettings
import com.example.aiadventchallenge.domain.model.LocalLlmProfile
import com.example.aiadventchallenge.domain.model.LocalLlmRuntimeOptions
import com.example.aiadventchallenge.domain.model.PromptProfile
import com.example.aiadventchallenge.domain.model.RequestConfig

class LocalLlmProfileResolver {

    fun resolveExecutionSettings(
        localConfig: LocalLlmConfig,
        answerMode: AnswerMode
    ): LocalLlmExecutionSettings {
        val requestedProfile = localConfig.profile
        val effectiveProfile = when {
            answerMode != AnswerMode.PLAIN_LLM && requestedProfile == LocalLlmProfile.OPTIMIZED_CHAT ->
                LocalLlmProfile.OPTIMIZED_RAG
            answerMode == AnswerMode.PLAIN_LLM && requestedProfile == LocalLlmProfile.OPTIMIZED_RAG ->
                LocalLlmProfile.OPTIMIZED_CHAT
            else -> requestedProfile
        }
        val promptProfile = when (effectiveProfile) {
            LocalLlmProfile.BASELINE -> PromptProfile.BASELINE
            LocalLlmProfile.OPTIMIZED_CHAT -> PromptProfile.OPTIMIZED_CHAT
            LocalLlmProfile.OPTIMIZED_RAG -> PromptProfile.OPTIMIZED_RAG
        }

        val profileDefaults = defaultsFor(effectiveProfile)
        return LocalLlmExecutionSettings(
            profile = effectiveProfile,
            promptProfile = promptProfile,
            runtimeOptions = mergeRuntimeOptions(profileDefaults, localConfig.runtimeOptions)
        )
    }

    fun applyToRequestConfig(
        baseConfig: RequestConfig,
        fitnessProfile: FitnessProfileType,
        executionSettings: LocalLlmExecutionSettings
    ): RequestConfig {
        val promptSuffix = promptSuffixFor(
            promptProfile = executionSettings.promptProfile,
            fitnessProfile = fitnessProfile
        )
        val runtime = executionSettings.runtimeOptions

        return baseConfig.copy(
            systemPrompt = listOf(baseConfig.systemPrompt, promptSuffix)
                .filter { it.isNotBlank() }
                .joinToString("\n\n"),
            temperature = runtime.temperature ?: baseConfig.temperature,
            maxTokens = runtime.numPredict ?: baseConfig.maxTokens,
            numCtx = runtime.numCtx ?: baseConfig.numCtx,
            topK = runtime.topK ?: baseConfig.topK,
            topP = runtime.topP ?: baseConfig.topP,
            repeatPenalty = runtime.repeatPenalty ?: baseConfig.repeatPenalty,
            seed = runtime.seed ?: baseConfig.seed,
            stop = runtime.stop ?: baseConfig.stop,
            keepAlive = runtime.keepAlive ?: baseConfig.keepAlive,
            promptProfile = executionSettings.promptProfile,
            localLlmProfile = executionSettings.profile
        )
    }

    private fun promptSuffixFor(
        promptProfile: PromptProfile,
        fitnessProfile: FitnessProfileType
    ): String {
        return when (promptProfile) {
            PromptProfile.BASELINE -> ""
            PromptProfile.OPTIMIZED_CHAT -> """
Отвечай кратко, по делу и без лишней воды.
Не выдумывай факты. Если уверенности мало, прямо обозначь это.
Сначала дай самый полезный вывод, затем 2-4 практичных детали.
Учитывай профиль пользователя: ${Prompts.getFitnessProfilePrompt(fitnessProfile).lineSequence().firstOrNull().orEmpty()}
""".trimIndent()
            PromptProfile.OPTIMIZED_RAG -> """
Отвечай кратко и по существу.
Если данных недостаточно, прямо скажи об этом.
Не добавляй факты, которых нет в retrieved context.
Если вопрос допускает несколько трактовок, выбери наиболее безопасную и явно обозначь ограничения.
""".trimIndent()
        }
    }

    private fun defaultsFor(profile: LocalLlmProfile): LocalLlmRuntimeOptions {
        return when (profile) {
            LocalLlmProfile.BASELINE -> LocalLlmRuntimeOptions()
            LocalLlmProfile.OPTIMIZED_CHAT -> LocalLlmRuntimeOptions(
                temperature = 0.2,
                numPredict = 320,
                numCtx = 4096,
                topK = 40,
                topP = 0.9,
                repeatPenalty = 1.1
            )
            LocalLlmProfile.OPTIMIZED_RAG -> LocalLlmRuntimeOptions(
                temperature = 0.1,
                numPredict = 280,
                numCtx = 6144,
                topK = 30,
                topP = 0.85,
                repeatPenalty = 1.15
            )
        }
    }

    private fun mergeRuntimeOptions(
        defaults: LocalLlmRuntimeOptions,
        overrides: LocalLlmRuntimeOptions
    ): LocalLlmRuntimeOptions {
        return LocalLlmRuntimeOptions(
            temperature = overrides.temperature ?: defaults.temperature,
            numPredict = overrides.numPredict ?: defaults.numPredict,
            numCtx = overrides.numCtx ?: defaults.numCtx,
            topK = overrides.topK ?: defaults.topK,
            topP = overrides.topP ?: defaults.topP,
            repeatPenalty = overrides.repeatPenalty ?: defaults.repeatPenalty,
            seed = overrides.seed ?: defaults.seed,
            stop = overrides.stop ?: defaults.stop,
            keepAlive = overrides.keepAlive ?: defaults.keepAlive
        )
    }
}
