package com.example.aiadventchallenge.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AnswerDisplay(
    answer: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = answer,
        style = MaterialTheme.typography.bodyLarge,
        modifier = modifier.padding(top = 16.dp)
    )
}
