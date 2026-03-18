package com.example.aiadventchallenge.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.aiadventchallenge.domain.usecase.AskMode

@Composable
fun ModeSelector(
    currentMode: AskMode,
    onModeSelected: (AskMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth()
    ) {
        FilterChip(
            selected = currentMode == AskMode.WITHOUT_LIMITS,
            onClick = { onModeSelected(AskMode.WITHOUT_LIMITS) },
            label = { Text("Без ограничений") },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        FilterChip(
            selected = currentMode == AskMode.WITH_LIMITS,
            onClick = { onModeSelected(AskMode.WITH_LIMITS) },
            label = { Text("С ограничениями") },
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
    }
}
