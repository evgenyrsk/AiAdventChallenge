package com.example.aiadventchallenge.domain.usecase

import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.FitnessProfileType
import com.example.aiadventchallenge.domain.model.RagEvaluationEntry
import com.example.aiadventchallenge.domain.model.RagEvaluationRunResult
import com.example.aiadventchallenge.domain.model.RagEvaluationSample

class RunRagEvaluationUseCase(
    private val compareRagAnswersUseCase: CompareRagAnswersUseCase
) {

    suspend operator fun invoke(
        fitnessProfile: FitnessProfileType,
        answerMode: AnswerMode = AnswerMode.RAG_ENHANCED
    ): RagEvaluationRunResult {
        val startedAt = System.currentTimeMillis()
        val entries = samples.map { sample ->
            RagEvaluationEntry(
                sample = sample,
                comparison = compareRagAnswersUseCase(
                    question = sample.question,
                    fitnessProfile = fitnessProfile,
                    answerMode = answerMode
                )
            )
        }
        val finishedAt = System.currentTimeMillis()

        return RagEvaluationRunResult(
            entries = entries,
            startedAt = startedAt,
            finishedAt = finishedAt
        )
    }

    companion object {
        val samples = listOf(
            RagEvaluationSample(
                label = "Fat Loss Priority",
                question = "Что важнее для похудения: дефицит калорий или время приема пищи?"
            ),
            RagEvaluationSample(
                label = "Protein Intake",
                question = "Сколько белка обычно рекомендуют человеку, который хочет сохранить мышцы при похудении?"
            ),
            RagEvaluationSample(
                label = "Sleep Recovery",
                question = "Почему сон влияет на восстановление и контроль аппетита?"
            )
        )
    }
}
