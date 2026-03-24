package com.example.aiadventchallenge.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.aiadventchallenge.domain.model.ActivityLevel
import com.example.aiadventchallenge.domain.model.Goal
import com.example.aiadventchallenge.domain.model.UserProfile

@Composable
fun UserProfileInput(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = profile.age?.toString().orEmpty(),
                onValueChange = { value ->
                    val age = value.toIntOrNull()
                    onProfileChange(profile.copy(age = age))
                },
                label = { Text("Возраст") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = profile.weight?.toString().orEmpty(),
                onValueChange = { value ->
                    val weight = value.toDoubleOrNull()
                    onProfileChange(profile.copy(weight = weight))
                },
                label = { Text("Вес (кг)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value = profile.height?.toString().orEmpty(),
                onValueChange = { value ->
                    val height = value.toIntOrNull()
                    onProfileChange(profile.copy(height = height))
                },
                label = { Text("Рост (см)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            GoalDropdown(
                selectedGoal = profile.goal,
                onGoalSelected = { goal ->
                    onProfileChange(profile.copy(goal = goal))
                },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )

            ActivityLevelDropdown(
                selectedLevel = profile.activityLevel,
                onLevelSelected = { level ->
                    onProfileChange(profile.copy(activityLevel = level))
                },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDropdown(
    selectedGoal: Goal?,
    onGoalSelected: (Goal?) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && enabled },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedGoal?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Цель") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            enabled = enabled,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Не указана") },
                onClick = {
                    onGoalSelected(null)
                    expanded = false
                }
            )
            Goal.entries.forEach { goal ->
                DropdownMenuItem(
                    text = { Text(goal.label) },
                    onClick = {
                        onGoalSelected(goal)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityLevelDropdown(
    selectedLevel: ActivityLevel?,
    onLevelSelected: (ActivityLevel?) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && enabled },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLevel?.label.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Активность") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            enabled = enabled,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Не указана") },
                onClick = {
                    onLevelSelected(null)
                    expanded = false
                }
            )
            ActivityLevel.entries.forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.label) },
                    onClick = {
                        onLevelSelected(level)
                        expanded = false
                    }
                )
            }
        }
    }
}
