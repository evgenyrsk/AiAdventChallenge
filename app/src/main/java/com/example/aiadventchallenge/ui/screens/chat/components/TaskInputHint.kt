package com.example.aiadventchallenge.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aiadventchallenge.domain.model.TaskContext

@Composable
fun TaskInputHint(
    taskContext: TaskContext?,
    modifier: Modifier = Modifier
) {
    val task = taskContext ?: return

    val (icon, hintText) = if (task.awaitingUserConfirmation) {
        "⏳" to "${task.phase.label}: Ждём вашего подтверждения"
    } else {
        "📋" to "${task.phase.label}: ${task.currentAction}"
    }

    Text(
        text = "$icon $hintText",
        modifier = modifier
            .background(
                color = if (task.awaitingUserConfirmation) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                },
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = if (task.awaitingUserConfirmation) {
            MaterialTheme.colorScheme.onTertiaryContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        textAlign = TextAlign.Start,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}
