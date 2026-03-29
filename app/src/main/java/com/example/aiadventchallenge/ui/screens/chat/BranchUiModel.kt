package com.example.aiadventchallenge.ui.screens.chat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BranchUiModel(
    val id: String,
    val title: String,
    val isActive: Boolean,
    val parentBranchId: String?,
    val checkpointMessageId: String?,
    val lastMessagePreview: String?,
    val updatedAt: Long,
    val messageCount: Int = 0,
    val checkpointLabel: String? = null
) {
    fun getFormattedDate(): String {
        val dateFormat = SimpleDateFormat("dd MMM HH:mm", Locale.getDefault())
        return dateFormat.format(Date(updatedAt))
    }

    fun getCreatedFromLabel(): String {
        if (checkpointLabel != null) {
            return "ответвление от: $checkpointLabel"
        }
        return "создана из предыдущего состояния"
    }

    companion object {
        fun fromDomain(
            id: String,
            title: String,
            isActive: Boolean,
            parentBranchId: String?,
            checkpointMessageId: String?,
            lastMessagePreview: String?,
            updatedAt: Long,
            messageCount: Int = 0,
            checkpointLabel: String? = null
        ): BranchUiModel {
            return BranchUiModel(
                id = id,
                title = title,
                isActive = isActive,
                parentBranchId = parentBranchId,
                checkpointMessageId = checkpointMessageId,
                lastMessagePreview = lastMessagePreview,
                updatedAt = updatedAt,
                messageCount = messageCount,
                checkpointLabel = checkpointLabel
            )
        }
    }
}