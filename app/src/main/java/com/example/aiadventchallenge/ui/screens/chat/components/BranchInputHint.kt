package com.example.aiadventchallenge.ui.screens.chat.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun BranchInputHint(
    branchName: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = "Вы пишете в ветку: $branchName",
        modifier = modifier.padding(vertical = 4.dp, horizontal = 4.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Start
    )
}