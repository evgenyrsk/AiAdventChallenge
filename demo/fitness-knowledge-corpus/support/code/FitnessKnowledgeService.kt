package demo.fitness.knowledge

data class RecoverySignal(
    val sleepHours: Double,
    val sorenessLevel: Int,
    val motivationLevel: Int
)

class FitnessKnowledgeService {
    fun recommendedProteinRange(weightKg: Double, inCalorieDeficit: Boolean): String {
        val lower = if (inCalorieDeficit) 1.8 else 1.4
        val upper = 2.2
        return "${lower}g-${upper}g protein per kg body weight"
    }

    fun chooseTrainingSplit(beginner: Boolean, trainingDaysPerWeek: Int): String {
        return when {
            beginner && trainingDaysPerWeek <= 3 -> "full_body"
            trainingDaysPerWeek <= 4 -> "upper_lower"
            else -> "push_pull_legs"
        }
    }

    fun recoveryPriority(signal: RecoverySignal): String {
        return when {
            signal.sleepHours < 6.5 -> "sleep"
            signal.sorenessLevel >= 8 -> "reduce training stress"
            signal.motivationLevel <= 3 -> "fatigue management"
            else -> "maintain current plan"
        }
    }
}
