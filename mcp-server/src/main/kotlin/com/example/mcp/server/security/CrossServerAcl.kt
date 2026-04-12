package com.example.mcp.server.security

import kotlinx.serialization.Serializable

@Serializable
data class AclRule(
    val sourceServer: String,
    val targetServer: String,
    val allowedTools: Set<String> = emptySet(),
    val blockedTools: Set<String> = emptySet(),
    val allowAllTools: Boolean = false
) {
    fun canCall(toolName: String): Boolean {
        if (blockedTools.contains(toolName)) {
            return false
        }
        
        if (allowAllTools) {
            return true
        }
        
        return allowedTools.contains(toolName)
    }
}

object CrossServerAcl {
    private val rules = mutableListOf<AclRule>()
    
    init {
        initializeDefaultRules()
    }
    
    private fun initializeDefaultRules() {
        rules.clear()
        
        rules.add(
            AclRule(
                sourceServer = "fitness-server-1",
                targetServer = "reminder-server-1",
                allowedTools = setOf(
                    "create_reminder",
                    "create_reminder_from_summary"
                ),
                blockedTools = emptySet(),
                allowAllTools = false
            )
        )
        
        rules.add(
            AclRule(
                sourceServer = "reminder-server-1",
                targetServer = "fitness-server-1",
                allowedTools = setOf(
                    "search_fitness_logs",
                    "get_fitness_summary",
                    "get_latest_scheduled_summary"
                ),
                blockedTools = emptySet(),
                allowAllTools = false
            )
        )
        
        rules.add(
            AclRule(
                sourceServer = "fitness-server-1",
                targetServer = "fitness-server-1",
                allowedTools = emptySet(),
                blockedTools = emptySet(),
                allowAllTools = true
            )
        )
        
        rules.add(
            AclRule(
                sourceServer = "reminder-server-1",
                targetServer = "reminder-server-1",
                allowedTools = emptySet(),
                blockedTools = emptySet(),
                allowAllTools = true
            )
        )
    }
    
    fun checkAccess(
        sourceServerId: String,
        targetServerId: String,
        toolName: String
    ): Boolean {
        val applicableRule = rules.find { rule ->
            rule.sourceServer == sourceServerId && rule.targetServer == targetServerId
        }
        
        if (applicableRule == null) {
            return false
        }
        
        return applicableRule.canCall(toolName)
    }
    
    fun addRule(rule: AclRule) {
        val existingIndex = rules.indexOfFirst {
            it.sourceServer == rule.sourceServer && it.targetServer == rule.targetServer
        }
        
        if (existingIndex >= 0) {
            rules[existingIndex] = rule
        } else {
            rules.add(rule)
        }
    }
    
    fun removeRule(sourceServer: String, targetServer: String) {
        rules.removeIf {
            it.sourceServer == sourceServer && it.targetServer == targetServer
        }
    }
    
    fun getAllRules(): List<AclRule> = rules.toList()
    
    fun getRule(sourceServer: String, targetServer: String): AclRule? {
        return rules.find {
            it.sourceServer == sourceServer && it.targetServer == targetServer
        }
    }
    
    fun clear() {
        rules.clear()
    }
    
    fun resetToDefaults() {
        initializeDefaultRules()
    }
}
