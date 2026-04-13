package com.example.mcp.server.service.training

import com.example.mcp.server.model.training.TrainingGuidanceRequest
import com.example.mcp.server.model.training.TrainingGuidanceResponse
import com.example.mcp.server.model.training.TrainingDay
import com.example.mcp.server.model.training.Exercise

class TrainingGuidanceService {
    
    fun generate(request: TrainingGuidanceRequest): TrainingGuidanceResponse {
        val trainingDaysPerWeek = request.trainingDaysPerWeek ?: 3
        val trainingLevel = request.trainingLevel ?: "beginner"
        val equipment = request.availableEquipment ?: listOf("gym")
        
        return TrainingGuidanceResponse(
            trainingSplit = determineTrainingSplit(trainingDaysPerWeek, trainingLevel),
            weeklyPlan = generateWeeklyPlan(
                trainingDaysPerWeek,
                trainingLevel,
                request.goal,
                equipment
            ),
            exercisePrinciples = determineExercisePrinciples(trainingLevel),
            recoveryNotes = determineRecoveryNotes(trainingDaysPerWeek, trainingLevel),
            notes = generateNotes(trainingLevel, trainingDaysPerWeek)
        )
    }
    
    private fun determineTrainingSplit(daysPerWeek: Int, level: String): String {
        return when (daysPerWeek) {
            1 -> "full body"
            2 -> "full body 2x week"
            3 -> if (level == "beginner") "full body 3x week" else "push/pull/legs"
            4 -> "upper/lower split"
            5 -> "push/pull/legs 2x week"
            6 -> "push/pull/legs + extra"
            7 -> "full body daily"
            else -> "full body"
        }
    }
    
    private fun generateWeeklyPlan(
        daysPerWeek: Int,
        level: String,
        goal: String,
        equipment: List<String>
    ): List<TrainingDay> {
        val hasGym = equipment.any { it.contains("gym", ignoreCase = true) }
        val hasDumbbells = equipment.any { it.contains("dumbbell", ignoreCase = true) }
        val hasBodyweight = equipment.contains("bodyweight") || !hasGym && !hasDumbbells
        
        val exercisesByLevel = when (level) {
            "beginner" -> getBeginnerExercises(hasGym, hasDumbbells, hasBodyweight)
            "intermediate" -> getIntermediateExercises(hasGym, hasDumbbells, hasBodyweight)
            "advanced" -> getAdvancedExercises(hasGym, hasDumbbells, hasBodyweight)
            else -> getBeginnerExercises(hasGym, hasDumbbells, hasBodyweight)
        }
        
        val days = mutableListOf<TrainingDay>()
        val splits = when (daysPerWeek) {
            1 -> listOf(listOf("full body"))
            2 -> listOf(listOf("full body"), listOf("full body"))
            3 -> if (level == "beginner") {
                listOf(listOf("full body"), listOf("full body"), listOf("full body"))
            } else {
                listOf(listOf("push"), listOf("pull"), listOf("legs"))
            }
            4 -> listOf(
                listOf("upper body", "push"),
                listOf("lower body", "legs"),
                listOf("upper body", "pull"),
                listOf("lower body", "legs")
            )
            5 -> listOf(
                listOf("upper body", "push"),
                listOf("lower body", "legs"),
                listOf("rest"),
                listOf("upper body", "pull"),
                listOf("full body")
            )
            6 -> listOf(
                listOf("upper body", "push"),
                listOf("lower body", "legs"),
                listOf("upper body", "pull"),
                listOf("lower body", "legs"),
                listOf("shoulders", "arms"),
                listOf("full body")
            )
            7 -> listOf(
                listOf("full body"),
                listOf("full body"),
                listOf("full body"),
                listOf("full body"),
                listOf("full body"),
                listOf("full body"),
                listOf("full body")
            )
            else -> listOf(listOf("full body"))
        }
        
        val dayNumbers = listOf(1, 2, 3, 4, 5, 6, 7)
        val dayPattern = listOf(1, 2, 3, 4, 5, 6, 0).take(daysPerWeek)
        
        var dayIndex = 0
        for ((i, splitIndex) in dayPattern.withIndex()) {
            if (i >= daysPerWeek) break
            
            val split = splits[splitIndex % splits.size]
            val dayOfWeek = (dayIndex % 7) + 1
            
            val focus = split.last()
            val category = split.first()
            
            val exercises = when (category) {
                "push" -> exercisesByLevel.pushExercises
                "pull" -> exercisesByLevel.pullExercises
                "legs" -> exercisesByLevel.legExercises
                "upper body" -> (exercisesByLevel.pushExercises + exercisesByLevel.pullExercises)
                "lower body" -> exercisesByLevel.legExercises
                "shoulders", "arms" -> (exercisesByLevel.pushExercises.take(2) + listOf(
                    Exercise("lateral raises", 3, "12-15"),
                    Exercise("bicep curls", 3, "10-12"),
                    Exercise("tricep dips", 3, "10-12")
                ))
                "full body" -> (exercisesByLevel.pushExercises.take(1) + 
                                 exercisesByLevel.pullExercises.take(1) + 
                                 exercisesByLevel.legExercises.take(2))
                else -> exercisesByLevel.pushExercises
            }.take(5)
            
            days.add(TrainingDay(dayOfWeek, focus, exercises))
            dayIndex++
        }
        
        return days
    }
    
    private fun getBeginnerExercises(hasGym: Boolean, hasDumbbells: Boolean, hasBodyweight: Boolean): ExerciseSets {
        return ExerciseSets(
            pushExercises = listOf(
                Exercise("push-ups", 3, "10-15"),
                if (hasGym || hasDumbbells) Exercise("dumbbell chest press", 3, "10-12") else Exercise("diamond push-ups", 3, "8-10"),
                Exercise("dumbbell shoulder press", 3, "10-12")
            ),
            pullExercises = listOf(
                Exercise("dumbbell rows", 3, "10-12"),
                if (hasGym) Exercise("lat pulldown", 3, "10-12") else Exercise("inverted rows", 3, "8-10"),
                Exercise("face pulls", 3, "12-15")
            ),
            legExercises = listOf(
                if (hasGym) Exercise("leg press", 3, "12-15") else Exercise("squats", 3, "12-15"),
                Exercise("lunges", 3, "10 each leg"),
                Exercise("glute bridges", 3, "15"),
                if (hasGym) Exercise("leg curls", 3, "12-15") else Exercise("single leg glute bridges", 3, "10 each leg")
            )
        )
    }
    
    private fun getIntermediateExercises(hasGym: Boolean, hasDumbbells: Boolean, hasBodyweight: Boolean): ExerciseSets {
        return ExerciseSets(
            pushExercises = listOf(
                if (hasGym) Exercise("bench press", 4, "8-10") else Exercise("weighted push-ups", 4, "8-10"),
                Exercise("dumbbell chest press", 4, "10-12"),
                if (hasGym) Exercise("overhead press", 3, "8-10") else Exercise("pike push-ups", 3, "8-10"),
                if (hasGym) Exercise("lateral raises", 3, "12-15") else Exercise("side raises", 3, "12-15")
            ),
            pullExercises = listOf(
                if (hasGym) Exercise("pull-ups", 3, "6-10") else Exercise("dumbbell rows", 4, "10-12"),
                if (hasGym) Exercise("lat pulldown", 4, "10-12") else Exercise("inverted rows", 4, "8-10"),
                Exercise("face pulls", 3, "15"),
                if (hasGym) Exercise("bicep curls", 3, "10-12") else Exercise("hammer curls", 3, "10-12")
            ),
            legExercises = listOf(
                if (hasGym) Exercise("squats", 4, "8-10") else Exercise("lunges", 4, "10 each leg"),
                if (hasGym) Exercise("romanian deadlift", 4, "8-10") else Exercise("glute bridges", 4, "12"),
                if (hasGym) Exercise("leg press", 3, "12-15") else Exercise("bulgarian split squats", 3, "8 each leg"),
                if (hasGym) Exercise("leg curls", 3, "12-15") else Exercise("single leg glute bridges", 3, "12 each leg")
            )
        )
    }
    
    private fun getAdvancedExercises(hasGym: Boolean, hasDumbbells: Boolean, hasBodyweight: Boolean): ExerciseSets {
        return ExerciseSets(
            pushExercises = listOf(
                if (hasGym) Exercise("bench press", 4, "6-8") else Exercise("weighted push-ups", 4, "6-8"),
                Exercise("incline dumbbell press", 4, "8-10"),
                if (hasGym) Exercise("overhead press", 4, "6-8") else Exercise("pistol squats", 3, "5 each leg"),
                if (hasGym) Exercise("lateral raises", 3, "15") else Exercise("weighted side raises", 3, "10")
            ),
            pullExercises = listOf(
                if (hasGym) Exercise("weighted pull-ups", 4, "6-8") else Exercise("dumbbell rows", 4, "8-10"),
                if (hasGym) Exercise("lat pulldown", 4, "8-10") else Exercise("chin-ups", 4, "6-10"),
                if (hasGym) Exercise("face pulls", 3, "15") else Exercise("recline rows", 3, "12"),
                if (hasGym) Exercise("bicep curls", 4, "10-12") else Exercise("concentration curls", 3, "10-12")
            ),
            legExercises = listOf(
                if (hasGym) Exercise("squats", 4, "5-8") else Exercise("lunges", 4, "8 each leg"),
                if (hasGym) Exercise("deadlift", 4, "5-6") else Exercise("single leg deadlift", 4, "8 each leg"),
                if (hasGym) Exercise("bulgarian split squats", 3, "8 each leg") else Exercise("glute bridges", 4, "15"),
                if (hasGym) Exercise("leg curls", 3, "15") else Exercise("single leg glute bridges", 3, "12 each leg")
            )
        )
    }
    
    private fun determineExercisePrinciples(level: String): String {
        return when (level) {
            "beginner" -> "progressive overload, фокус на технику, отдых 60-90s между подходами"
            "intermediate" -> "progressive overload, 2-3 упражнения на мышечную группу, отдых 90-120s"
            "advanced" -> "периодизация, variation protocols, drop sets, supersets, отдых 120-150s"
            else -> "progressive overload, фокус на технику"
        }
    }
    
    private fun determineRecoveryNotes(daysPerWeek: Int, level: String): String {
        val baseNote = when (daysPerWeek) {
            1, 2 -> "При 1-2 тренировках в неделю старайтесь тренировать все мышечные группы."
            3 -> "Равномерно распределите нагрузку. Отдыхайте минимум 48ч между тренировками одной группы мышц."
            4 -> "Upper/Lower split позволяет оптимально распределить нагрузку и восстановление."
            5, 6 -> "Следите за достаточным восстановлением. Сон 7-8 часов обязателен."
            7 -> "Тренируйтесь в низкоинтенсивном темпе 2-3 дня в неделю для восстановления."
            else -> "Следите за восстановлением."
        }
        
        val levelNote = when (level) {
            "beginner" -> "Начните с лёгких весов, фокус на технику."
            "intermediate" -> "Увеличивайте нагрузку постепенно, следите за восстановлением."
            "advanced" -> "Используйте периодизацию для избежания перетренированности."
            else -> ""
        }
        
        return "$baseNote $levelNote"
    }
    
    private fun generateNotes(level: String, daysPerWeek: Int): String {
        val levelAdvice = when (level) {
            "beginner" -> "Начните с фундаментальных движений. Увеличивайте вес только когда техника идеальна."
            "intermediate" -> "Добавляйте новые упражнения и вариации. Следите за прогрессом."
            "advanced" -> "Используйте продвинутые техники: supersets, drop sets, rest-pause."
            else -> ""
        }
        
        val daysAdvice = "При $daysPerWeek тренировках в неделю, ориентируйтесь на вышеуказанный план, корректируйте по самочувствию."
        
        return "$levelAdvice $daysAdvice"
    }
    
    private data class ExerciseSets(
        val pushExercises: List<Exercise>,
        val pullExercises: List<Exercise>,
        val legExercises: List<Exercise>
    )
}
