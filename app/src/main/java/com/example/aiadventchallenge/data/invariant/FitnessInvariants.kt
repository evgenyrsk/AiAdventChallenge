package com.example.aiadventchallenge.data.invariant

import com.example.aiadventchallenge.domain.model.*
import com.example.aiadventchallenge.data.invariant.TaskFlowInvariant

object FitnessInvariants {

    class TopicRestrictionInvariant : Invariant {
        override val id = "topic_restriction"
        override val category = InvariantCategory.TOPIC
        override val description = "Только темы фитнеса, питания и здоровья"
        override val priority = InvariantPriority.HARD
        override val isEnabled = true

        private val forbiddenTopics = listOf(
            "политика", "религия", "финансы", "инвестиции",
            "акции", "облигации", "криптовалюта", "торговля",
            "налоги", "законы", "юридические", "суд"
        )

        override fun validate(
            content: String,
            context: TaskContext?,
            role: MessageRole
        ): InvariantViolation? {
            val lowerContent = content.lowercase()

            for (topic in forbiddenTopics) {
                if (topic in lowerContent) {
                    return InvariantViolation(
                        invariantId = id,
                        invariantDescription = description,
                        reason = "Запрос содержит запрещённую тему: \"$topic\"",
                        suggestion = "Пожалуйста, задайте вопрос, связанный с фитнесом, питанием или здоровьем",
                        canProceed = false
                    )
                }
            }
            return null
        }
    }

    class MedicalDiagnosisInvariant : Invariant {
        override val id = "medical_diagnosis"
        override val category = InvariantCategory.SAFETY
        override val description = "Запрет постановки медицинских диагнозов"
        override val priority = InvariantPriority.HARD
        override val isEnabled = true

        private val diagnosisPatterns = listOf(
            Regex("\\bдиагноз\\b"),
            Regex("\\bпостав(ить|ить)\\s+(мне\\s+)?диагноз\\b"),
            Regex("\\bчто\\s+у\\s+меня\\b.*?(?:болит|заболело|ноет|простреливает)"),
            Regex("\\bу\\s+меня\\s+(?:болит|ноет|простреливает)(?:\\s+(?:голова|живот|спина|грудь|сердце|желудок))\\b")
        )

        private val fitnessContextPatterns = listOf(
            Regex("\\b(?:тренировк|упражнени|программ|комплекс|зал|нагрузк|спорт|мышц|сустав|колено|спина|ног|рук)\\b"),
            Regex("\\b(?:фитнес|бег|приседани|жим|тяга|отжимани)\\b")
        )

        override fun validate(
            content: String,
            context: TaskContext?,
            role: MessageRole
        ): InvariantViolation? {
            if (role == MessageRole.USER) {
                val hasDiagnosisPattern = diagnosisPatterns.any { it.containsMatchIn(content) }

                if (hasDiagnosisPattern) {
                    val hasFitnessContext = fitnessContextPatterns.any { it.containsMatchIn(content) }

                    if (!hasFitnessContext) {
                        return InvariantViolation(
                            invariantId = id,
                            invariantDescription = description,
                            reason = "Запрос содержит попытку поставить медицинский диагноз вне фитнес-контекста",
                            suggestion = "Я не врач и не могу ставить диагнозы. Консультируйтесь со специалистом.",
                            canProceed = false
                        )
                    }
                }
            }
            return null
        }
    }

    class PrescriptionProhibitionInvariant : Invariant {
        override val id = "prescription_prohibition"
        override val category = InvariantCategory.PROFESSIONALISM
        override val description = "Запрет назначения лекарств"
        override val priority = InvariantPriority.HARD
        override val isEnabled = true

        private val prescriptionKeywords = listOf(
            "пропишу", "назначаю", "принимать лекарство", "препарат", "таблетки", "лекарство"
        )

        override fun validate(
            content: String,
            context: TaskContext?,
            role: MessageRole
        ): InvariantViolation? {
            if (role == MessageRole.USER) {
                if (prescriptionKeywords.any { it in content.lowercase() }) {
                    return InvariantViolation(
                        invariantId = id,
                        invariantDescription = description,
                        reason = "Запрос содержит просьбу назначить лекарства",
                        suggestion = "Я не могу назначать лекарства. Обратитесь к врачу.",
                        canProceed = false
                    )
                }
            }
            return null
        }
    }

    class PrescriptionForAiInvariant : Invariant {
        override val id = "prescription_for_ai"
        override val category = InvariantCategory.PROFESSIONALISM
        override val description = "Запрет назначения лекарств AI"
        override val priority = InvariantPriority.HARD
        override val isEnabled = true

        private val prescriptionPatterns = listOf(
            Regex("\\b(?:пропишу|назначаю)\\s+.*(?:лекарств|препарат|таблеток|мазей)\\b"),
            Regex("\\b(?:принимать|пить)\\s+.*(?:лекарство|препарат|таблетку)\\b"),
            Regex("\\b(?:ибупрофен|парацетамол|анальгин|аспирин)\\b")
        )

        override fun validate(
            content: String,
            context: TaskContext?,
            role: MessageRole
        ): InvariantViolation? {
            if (role == MessageRole.ASSISTANT) {
                if (prescriptionPatterns.any { it.containsMatchIn(content) }) {
                    return InvariantViolation(
                        invariantId = id,
                        invariantDescription = description,
                        reason = "Ответ содержит назначение лекарств",
                        suggestion = "Я не могу назначать лекарства. Обратитесь к врачу.",
                        canProceed = false
                    )
                }
            }
            return null
        }
    }

    fun createDefaultConfig(): InvariantConfig {
        return InvariantConfig(
            invariants = listOf(
                TaskFlowInvariant(),
                PlanningPhaseInvariant(),
                TopicRestrictionInvariant(),
                PrescriptionProhibitionInvariant(),
                MedicalDiagnosisInvariant(),
                PrescriptionForAiInvariant()
            )
        )
    }
}
