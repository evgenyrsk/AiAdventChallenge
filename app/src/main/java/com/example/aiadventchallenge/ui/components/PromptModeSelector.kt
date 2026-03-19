package com.example.aiadventchallenge.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aiadventchallenge.domain.model.PromptMode

@Composable
fun PromptModeSelector(
    currentMode: PromptMode,
    onModeSelected: (PromptMode) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        PromptMode.entries.forEach { mode ->
            FilterChip(
                selected = currentMode == mode,
                onClick = { if (enabled) onModeSelected(mode) },
                label = { 
                    Text(
                        text = mode.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                enabled = enabled,
                modifier = Modifier.weight(1f),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
