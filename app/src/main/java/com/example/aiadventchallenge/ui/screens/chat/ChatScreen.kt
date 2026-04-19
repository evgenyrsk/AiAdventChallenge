package com.example.aiadventchallenge.ui.screens.chat

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel as viewModelCompose
import com.example.aiadventchallenge.domain.mcp.RetrievalSourceCard
import com.example.aiadventchallenge.domain.mcp.RetrievalSummary
import com.example.aiadventchallenge.domain.model.AnswerMode
import com.example.aiadventchallenge.domain.model.ChatMessage
import com.example.aiadventchallenge.domain.model.DialogTokenStats
import com.example.aiadventchallenge.domain.model.RequestLog
import com.example.aiadventchallenge.domain.model.ContextStrategyType
import com.example.aiadventchallenge.domain.model.mcp.McpConnectionStatus
import com.example.aiadventchallenge.ui.screens.chat.components.StrategySettingsBottomSheet
import com.example.aiadventchallenge.di.AppDependencies
import com.example.aiadventchallenge.ui.screens.chat.components.BranchChip
import com.example.aiadventchallenge.ui.screens.chat.components.BranchPickerSheet
import com.example.aiadventchallenge.ui.screens.chat.components.CreateBranchDialog
import com.example.aiadventchallenge.ui.screens.chat.components.BranchStartDivider
import com.example.aiadventchallenge.ui.screens.chat.components.BranchIndicatorBadge
import com.example.aiadventchallenge.ui.screens.chat.components.BranchInputHint
import com.example.aiadventchallenge.rag.memory.ConversationTaskState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val chatUiState by viewModel.chatUiState.collectAsStateWithLifecycle()
    val mcpConnectionStatus by viewModel.mcpConnectionStatus.collectAsStateWithLifecycle()
    var userInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )
    var showTokenStats by remember { mutableStateOf(false) }

    val lastRequestTokens by viewModel.lastRequestTokens.collectAsStateWithLifecycle()
    val dialogStats by viewModel.dialogStats.collectAsStateWithLifecycle()
    val allTimeStats by viewModel.allTimeStats.collectAsStateWithLifecycle()
    val requestLogs by viewModel.requestLogs.collectAsStateWithLifecycle()
    val activeStrategyConfig by viewModel.activeStrategyConfig.collectAsStateWithLifecycle()
    var showDebugLog by remember { mutableStateOf(false) }
    var showStrategySettings by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showRetrievalDetails by remember { mutableStateOf(false) }
    var pendingStrategyChange by remember { mutableStateOf<ContextStrategyType?>(null) }

    LaunchedEffect(chatUiState.latestRetrievalSummary) {
        if (chatUiState.latestRetrievalSummary == null) {
            showRetrievalDetails = false
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("Чат")
                                if (chatUiState.isBranchingStrategy && chatUiState.activeBranchName != null) {
                                    BranchChip(
                                        branchName = chatUiState.activeBranchName!!,
                                        onClick = { viewModel.onBranchChipClicked() }
                                    )
                                }
                            }
                            activeStrategyConfig?.let { config ->
                                Text(
                                    text = getStrategyDisplayName(config.type),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { /* показать детали */ },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (mcpConnectionStatus == McpConnectionStatus.CONNECTED) {
                                    Icons.Default.Cloud
                                } else {
                                    Icons.Default.CloudOff
                                },
                                contentDescription = "MCP Connection Status",
                                tint = if (mcpConnectionStatus == McpConnectionStatus.CONNECTED) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.error
                                }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .combinedClickable(
                                    onClick = { showTokenStats = true },
                                     onLongClick = { showDebugLog = true }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Статистика",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showStrategySettings = true },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Настройки стратегии",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )

                LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                    ) {
                        if (chatUiState.isBranchingStrategy && messages.isEmpty()) {
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "💡 Подсказки:",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "• Нажмите и удерживайте сообщение, чтобы создать новую ветку от этого места",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "• Нажмите на badge с количеством веток, чтобы переключиться между ними",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = "• Нажмите на название ветки вверху, чтобы просмотреть все ветки",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        items(messages, key = { it.id }) { message ->
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (chatUiState.isBranchingStrategy) {
                                    val branchCount = chatUiState.getMessageBranchCount(message.id)
                                    if (branchCount > 0) {
                                        BranchIndicatorBadge(
                                            branchCount = branchCount,
                                            onClick = { viewModel.onBranchIndicatorClicked(message.id) }
                                        )
                                    }
                                }

                                MessageBubble(
                                    message = message.content,
                                    isFromUser = message.isFromUser,
                                    isSystemMessage = message.isSystemMessage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                if (chatUiState.isBranchingStrategy && !message.isSystemMessage) {
                                                    viewModel.onCreateBranchFromMessage(message.id)
                                                }
                                            }
                                        )
                                )

                                if (chatUiState.isBranchingStrategy &&
                                    !chatUiState.isRootBranch &&
                                    message.id == chatUiState.currentBranchCheckpointMessageId) {
                                    BranchStartDivider()
                                }
                            }
                        }
                        if (isLoading) {
                            item {
                                MessageBubble(
                                    message = "Печатает...",
                                    isFromUser = false,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnswerModeToggle(
                        answerMode = chatUiState.answerMode,
                        enabled = !isLoading,
                        onModeSelected = viewModel::setAnswerMode,
                        modifier = Modifier.fillMaxWidth()
                    )

                    chatUiState.latestRetrievalSummary?.let { retrievalSummary ->
                        RetrievalSummaryCard(
                            summary = retrievalSummary,
                            onOpenDetails = { showRetrievalDetails = true },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (chatUiState.isBranchingStrategy && chatUiState.activeBranchName != null) {
                        BranchInputHint(
                            branchName = chatUiState.activeBranchName!!
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = userInput,
                            onValueChange = { userInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Сообщение") },
                            enabled = !isLoading,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (userInput.isNotBlank()) {
                                        viewModel.sendMessage(userInput)
                                        userInput = ""
                                        keyboardController?.hide()
                                    }
                                }
                            ),
                            shape = RoundedCornerShape(24.dp),
                            singleLine = true
                        )

                        IconButton(
                            onClick = {
                                if (userInput.isNotBlank()) {
                                    viewModel.sendMessage(userInput)
                                    userInput = ""
                                    keyboardController?.hide()
                                }
                            },
                            enabled = !isLoading && userInput.isNotBlank(),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Отправить",
                                tint = if (userInput.isNotBlank() && !isLoading) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                }
                }
            }
        }

        if (showTokenStats) {
            ModalBottomSheet(
                onDismissRequest = { showTokenStats = false },
                sheetState = sheetState
            ) {
                TokenStatsDisplay(
                    lastRequestTokens = lastRequestTokens,
                    dialogStats = dialogStats,
                    allTimeStats = allTimeStats,
                    requestLogs = requestLogs,
                    onClose = { showTokenStats = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }

        if (showDebugLog) {
            ModalBottomSheet(
                onDismissRequest = { showDebugLog = false },
                sheetState = sheetState
            ) {
                DebugLogDisplay(
                    requestLogs = requestLogs,
                     onClose = { showDebugLog = false },
                     modifier = Modifier
                         .fillMaxWidth()
                         .padding(16.dp)
                 )
             }
         }

        if (showStrategySettings) {
            ModalBottomSheet(
                onDismissRequest = { showStrategySettings = false },
                sheetState = sheetState
            ) {
                activeStrategyConfig?.let { config ->
                    StrategySettingsBottomSheet(
                        currentStrategy = config.type,
                        currentWindowSize = config.windowSize,
                        currentFitnessProfile = chatUiState.fitnessProfile,
                        onStrategyChange = { type ->
                            if (type != config.type) {
                                pendingStrategyChange = type
                                showClearChatDialog = true
                            }
                        },
                        onWindowSizeChange = { size -> viewModel.setWindowSize(size) },
                        onFitnessProfileChange = { profile -> viewModel.setFitnessProfile(profile) },
                        onClose = { showStrategySettings = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        if (showRetrievalDetails && chatUiState.latestRetrievalSummary != null) {
            ModalBottomSheet(
                onDismissRequest = { showRetrievalDetails = false },
                sheetState = sheetState
            ) {
                RetrievalDetailsSheet(
                    summary = chatUiState.latestRetrievalSummary!!,
                    taskState = chatUiState.latestTaskState,
                    onClose = { showRetrievalDetails = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }
        }

        if (showClearChatDialog) {
            AlertDialog(
                onDismissRequest = {
                    showClearChatDialog = false
                    pendingStrategyChange = null
                },
                title = { Text("Сменить стратегию?") },
                text = {
                    Text(
                        "При смене стратегии текущий чат будет очищен. " +
                                "Вы хотите сменить стратегию на ${pendingStrategyChange?.let { getStrategyDisplayName(it) }}?"
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingStrategyChange?.let { viewModel.setStrategyType(it) }
                            viewModel.clearChat()
                            showClearChatDialog = false
                            pendingStrategyChange = null
                            showStrategySettings = false
                        }
                    ) {
                        Text("Да, сменить и очистить")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showClearChatDialog = false
                            pendingStrategyChange = null
                        }
                    ) {
                        Text("Отмена")
                    }
                }
            )
        }

        if (chatUiState.showBranchPicker) {
            BranchPickerSheet(
                branches = chatUiState.availableBranches,
                onBranchSelected = { branchId -> viewModel.onBranchSelected(branchId) },
                onDismiss = { viewModel.onBranchPickerDismiss() },
                onDeleteBranch = { branchId -> viewModel.onDeleteBranch(branchId) }
            )
        }

        if (chatUiState.showCreateBranchDialog) {
            CreateBranchDialog(
                targetMessagePreview = chatUiState.branchCreationTargetPreview ?: "Сообщение",
                branchName = chatUiState.newBranchName,
                error = chatUiState.newBranchError,
                onNameChange = { name -> viewModel.onNewBranchNameChanged(name) },
                onCreate = { switchToNew -> viewModel.onCreateBranchConfirmed(switchToNew) },
                onDismiss = { viewModel.onCreateBranchDialogDismiss() }
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: String,
    isFromUser: Boolean,
    isSystemMessage: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (isFromUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isSystemMessage) 1.0f else 0.8f),
            shape = RoundedCornerShape(
                topStart = if (isFromUser) 12.dp else 4.dp,
                topEnd = if (isFromUser) 4.dp else 12.dp,
                bottomStart = if (isFromUser || isSystemMessage) 4.dp else 12.dp,
                bottomEnd = if (isFromUser || isSystemMessage) 12.dp else 4.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isSystemMessage -> MaterialTheme.colorScheme.surfaceContainerHighest
                    isFromUser -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Text(
                text = message,
                modifier = Modifier.padding(12.dp),
                color = when {
                    isSystemMessage -> MaterialTheme.colorScheme.onSurfaceVariant
                    isFromUser -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = if (isSystemMessage) {
                    MaterialTheme.typography.bodySmall
                } else {
                    MaterialTheme.typography.bodyMedium
                }
            )
        }
    }
}

@Composable
private fun AnswerModeToggle(
    answerMode: AnswerMode,
    enabled: Boolean,
    onModeSelected: (AnswerMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = answerMode == AnswerMode.PLAIN_LLM,
            onClick = { onModeSelected(AnswerMode.PLAIN_LLM) },
            label = { Text("Обычный") },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        FilterChip(
            selected = answerMode == AnswerMode.RAG_BASIC,
            onClick = { onModeSelected(AnswerMode.RAG_BASIC) },
            label = { Text("RAG Basic") },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
        FilterChip(
            selected = answerMode == AnswerMode.RAG_ENHANCED,
            onClick = { onModeSelected(AnswerMode.RAG_ENHANCED) },
            label = { Text("RAG Enhanced") },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        )
    }
}

@Composable
private fun RetrievalSummaryCard(
    summary: RetrievalSummary,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildRetrievalPreviewText(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = onOpenDetails,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp,
                    vertical = 0.dp
                )
            ) {
                Text("Детали")
            }
        }
    }
}

private fun buildRetrievalPreviewText(summary: RetrievalSummary): String {
    val primarySource = summary.chunks.firstOrNull()?.title
        ?.takeIf { it.isNotBlank() }
        ?: summary.chunks.firstOrNull()?.relativePath
        ?: "no-source"

    return buildString {
        append("KB:")
        if (!summary.rewrittenQuery.isNullOrBlank()) {
            append(" rewrite +")
        }
        append(" ${summary.finalTopK}/${summary.topKBeforeFilter} chunks")
        append(" • ${summary.postProcessingMode}")
        append(" • $primarySource")
        summary.groundedAnswer?.quotes?.takeIf { it.isNotEmpty() }?.let { append(" • quotes=${it.size}") }
        summary.groundedAnswer?.takeIf { it.isFallbackIDontKnow }?.let { append(" • no-answer") }
        if (summary.fallbackApplied) {
            append(" • fallback")
        }
    }
}

@Composable
private fun RetrievalDetailsSheet(
    summary: RetrievalSummary,
    taskState: ConversationTaskState?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Knowledge Base Context",
                style = MaterialTheme.typography.titleMedium
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Закрыть"
                )
            }
        }

        RetrievalDetailsSection(title = "Query") {
            RetrievalDetailText("Original: ${summary.originalQuery}")
            summary.rewrittenQuery?.takeIf { it.isNotBlank() }?.let {
                RetrievalDetailText("Rewritten: $it")
            }
            RetrievalDetailText("Effective: ${summary.effectiveQuery}")
            RetrievalDetailText("Rewrite applied: ${if (summary.rewriteApplied) "yes" else "no"}")
            summary.detectedIntent?.let { RetrievalDetailText("Detected intent: $it") }
            summary.rewriteStrategy?.let { RetrievalDetailText("Rewrite strategy: $it") }
            if (summary.addedTerms.isNotEmpty()) {
                RetrievalDetailText("Added terms: ${summary.addedTerms.joinToString(", ")}")
            }
            if (summary.removedPhrases.isNotEmpty()) {
                RetrievalDetailText("Removed phrases: ${summary.removedPhrases.joinToString(", ")}")
            }
        }

        RetrievalDetailsSection(title = "Pipeline") {
            RetrievalDetailText("Source: ${summary.source}")
            RetrievalDetailText("Strategy: ${summary.strategy}")
            RetrievalDetailText("topK: ${summary.topKBeforeFilter} -> ${summary.finalTopK}")
            RetrievalDetailText("Mode: ${summary.postProcessingMode}")
            RetrievalDetailText("Threshold: ${summary.similarityThreshold?.let { "%.2f".format(it) } ?: "none"}")
            if (summary.fallbackApplied) {
                RetrievalDetailText("Fallback: ${summary.fallbackReason ?: "applied"}")
            }
        }

        taskState?.let { state ->
            RetrievalDetailsSection(title = "Task Memory") {
                RetrievalDetailText("Goal: ${state.dialogGoal ?: "не зафиксирована"}")
                RetrievalDetailText(
                    "Constraints: ${
                        state.resolvedConstraints.takeIf { it.isNotEmpty() }?.joinToString("; ")
                            ?: "нет"
                    }"
                )
                RetrievalDetailText(
                    "Clarifications: ${
                        state.userClarifications.takeIf { it.isNotEmpty() }?.joinToString("; ")
                            ?: "нет"
                    }"
                )
                RetrievalDetailText(
                    "Open questions: ${
                        state.openQuestions.takeIf { it.isNotEmpty() }?.joinToString("; ")
                            ?: "нет"
                    }"
                )
                state.definedTerms.takeIf { it.isNotEmpty() }?.let { terms ->
                    RetrievalDetailText(
                        "Terms: ${terms.joinToString("; ") { "${it.term}=${it.meaning}" }}"
                    )
                }
                RetrievalDetailText("Summary: ${state.latestSummary ?: "нет"}")
                RetrievalDetailText("Updated at: ${state.updatedAt}")
            }
        }

        summary.groundedAnswer?.let { grounded ->
            RetrievalDetailsSection(title = "Grounded Answer") {
                RetrievalDetailText("Mode: ${grounded.answerMode}")
                RetrievalDetailText("Answerable: ${if (grounded.confidence.answerable) "yes" else "no"}")
                grounded.fallbackReason?.let { RetrievalDetailText("Fallback reason: $it") }
                RetrievalDetailText(
                    "Confidence: chunks=${grounded.confidence.finalChunkCount}, " +
                        "topSemantic=${grounded.confidence.topSemanticScore?.let { "%.2f".format(it) } ?: "n/a"}, " +
                        "topRerank=${grounded.confidence.topRerankScore?.let { "%.2f".format(it) } ?: "n/a"}"
                )
                if (grounded.answerText.isNotBlank()) {
                    RetrievalDetailText("Answer: ${grounded.answerText}")
                }
            }

            RetrievalDetailsSection(title = "Grounded Sources") {
                if (grounded.sources.isEmpty()) {
                    RetrievalDetailText("Нет источников")
                } else {
                    grounded.sources.forEach { source ->
                        RetrievalDetailText(
                            listOfNotNull(
                                source.title ?: source.relativePath ?: source.source,
                                source.section,
                                source.chunkId?.let { "chunk=$it" },
                                source.similarityScore?.let { "score=${"%.3f".format(it)}" },
                                source.rerankScore?.let { "rerank=${"%.3f".format(it)}" }
                            ).joinToString(" • ")
                        )
                    }
                }
            }

            RetrievalDetailsSection(title = "Grounded Quotes") {
                if (grounded.quotes.isEmpty()) {
                    RetrievalDetailText("Нет цитат")
                } else {
                    grounded.quotes.forEach { quote ->
                        RetrievalDetailText(
                            "“${quote.quotedText}”"
                        )
                        RetrievalDetailText(
                            listOfNotNull(
                                quote.title ?: quote.relativePath ?: quote.source,
                                quote.section,
                                quote.chunkId?.let { "chunk=$it" }
                            ).joinToString(" • ")
                        )
                    }
                }
            }
        }

        RetrievalDetailsSection(title = "Final Context") {
            if (summary.chunks.isEmpty()) {
                RetrievalDetailText("Нет финальных чанков")
            } else {
                summary.chunks.forEach { chunk ->
                    RetrievalSourceRow(chunk = chunk, detailed = true)
                }
            }
        }

        RetrievalDetailsSection(title = "Initial Candidates") {
            if (summary.initialCandidates.isEmpty()) {
                RetrievalDetailText("Нет данных")
            } else {
                summary.initialCandidates.forEach { chunk ->
                    RetrievalSourceRow(chunk = chunk, detailed = true)
                }
            }
        }

        RetrievalDetailsSection(title = "Filtered Out") {
            if (summary.filteredCandidates.isEmpty()) {
                RetrievalDetailText("Ничего не отброшено")
            } else {
                summary.filteredCandidates.forEach { chunk ->
                    RetrievalSourceRow(chunk = chunk, detailed = true)
                }
            }
        }
    }
}

@Composable
private fun RetrievalDetailsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        content()
    }
}

@Composable
private fun RetrievalDetailText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun RetrievalSourceRow(
    chunk: RetrievalSourceCard,
    detailed: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = chunk.title.ifBlank { chunk.relativePath },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = listOf(chunk.section, chunk.relativePath)
                    .filter { it.isNotBlank() }
                    .joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (detailed) 4 else 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append("score=${"%.3f".format(chunk.score)}")
                    chunk.rerankScore?.let { append(" • rerank=${"%.3f".format(it)}") }
                    chunk.filterReason?.let { append(" • $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (detailed) {
                chunk.explanation?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun TokenStatsDisplay(
    lastRequestTokens: ChatViewModel.LastRequestTokens?,
    dialogStats: DialogTokenStats,
    allTimeStats: DialogTokenStats,
    requestLogs: List<RequestLog>,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 Статистика токенов",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Закрыть",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            lastRequestTokens?.let { last ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Последний запрос:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    last.promptTokens?.let {
                        Text(
                            text = "• Prompt: $it ток.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    last.completionTokens?.let {
                        Text(
                            text = "• Completion: $it ток.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    last.totalTokens?.let {
                        Text(
                            text = "• Всего: $it ток.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
            
            if (dialogStats.requestsCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "За весь диалог (${dialogStats.requestsCount} запросов):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Prompt: ${dialogStats.totalPromptTokens} ток.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Completion: ${dialogStats.totalCompletionTokens} ток.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Всего: ${dialogStats.totalTokens} ток.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (allTimeStats.requestsCount > 0) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "За всё время (${allTimeStats.requestsCount} запросов):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Prompt: ${allTimeStats.totalPromptTokens} ток.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Completion: ${allTimeStats.totalCompletionTokens} ток.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "• Всего: ${allTimeStats.totalTokens} ток.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val validLogs = requestLogs.filter { it.totalTokens != null }
            val recentLogs = validLogs.take(10)
            
            if (recentLogs.isNotEmpty()) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "История запросов (последние 10):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "№",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(30.dp)
                        )
                        Text(
                            text = "Prompt",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Comp",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Всего",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    HorizontalDivider()
                    
                    recentLogs.forEachIndexed { index, log ->
                        val isSummary = log.requestConfig.systemPrompt == "Summary creation"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (isSummary) "📝${index + 1}" else "${index + 1}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(30.dp)
                            )
                            Text(
                                text = "${log.promptTokens ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${log.completionTokens ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${log.totalTokens ?: 0}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getStrategyDisplayName(type: ContextStrategyType): String {
    return when (type) {
        ContextStrategyType.SLIDING_WINDOW -> "Sliding Window"
        ContextStrategyType.STICKY_FACTS -> "Sticky Facts"
        ContextStrategyType.BRANCHING -> "Branching"
        ContextStrategyType.MEMORY_BASED -> "Memory Based"
    }
}
