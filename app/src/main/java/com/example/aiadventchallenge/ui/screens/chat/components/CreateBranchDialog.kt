package com.example.aiadventchallenge.ui.screens.chat.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun CreateBranchDialog(
    targetMessagePreview: String,
    branchName: String,
    error: String?,
    onNameChange: (String) -> Unit,
    onCreate: (switchToNew: Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Создать новую ветку") },
        text = {
            Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Создание ветки от сообщения:",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = targetMessagePreview,
                        modifier = Modifier.padding(12.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                }
                
                OutlinedTextField(
                    value = branchName,
                    onValueChange = onNameChange,
                    label = { Text("Название ветки") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } }
                )
            }
        },
        confirmButton = {
            Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onCreate(true) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Создать и перейти")
                }
                
                TextButton(
                    onClick = { onCreate(false) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Создать")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    )
}