package com.example.aiadventchallenge.domain.utils

object JsonUtils {
    fun extractJson(content: String): String {
        val trimmed = content.trim()

        val codeBlockMatch = Regex("""```(?:json|javascript)?\s*\{[\s\S]*?\}\s*```""").find(trimmed)
        if (codeBlockMatch != null) {
            val innerContent = codeBlockMatch.value
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
                .let {
                    if (it.startsWith("json", ignoreCase = true)) it.substring(4).trimStart()
                    else if (it.startsWith("javascript", ignoreCase = true)) it.substring(10).trimStart()
                    else it
                }
            return innerContent
        }

        val jsonMatch = Regex("""\{[\s\S]*\}""").find(trimmed)
        if (jsonMatch != null) return jsonMatch.value.trim()

        return trimmed
    }

    fun tryFixMalformedJson(content: String): String? {
        var fixedContent = content.trim()

        if (!fixedContent.startsWith("{")) {
            return null
        }

        val openBraces = fixedContent.count { it == '{' }
        val closeBraces = fixedContent.count { it == '}' }
        val openArrays = fixedContent.count { it == '[' }
        val closeArrays = fixedContent.count { it == ']' }

        if (openBraces > closeBraces) {
            val missingBraces = openBraces - closeBraces
            fixedContent += "}".repeat(missingBraces)
        }

        if (openArrays > closeArrays) {
            val missingArrays = openArrays - closeArrays
            fixedContent += "]".repeat(missingArrays)
        }

        val lastComma = fixedContent.lastIndexOf(',')
        if (lastComma > 0) {
            val lastBrace = fixedContent.indexOf('}', lastComma)
            val lastArray = fixedContent.indexOf(']', lastComma)

            if (lastBrace == -1 || (lastArray != -1 && lastArray < lastBrace)) {
                if (lastBrace == -1 || lastArray != -1) {
                    fixedContent = fixedContent.substring(0, lastComma) + fixedContent.substring(lastComma + 1)
                }
            }
        }

        if (fixedContent.endsWith(",") || fixedContent.endsWith("])")) {
            if (fixedContent.endsWith(",") || fixedContent.endsWith(",}")) {
                fixedContent = fixedContent.trimEnd(',')
            }
            if (fixedContent.endsWith("])")) {
                if (!fixedContent.contains("\"assistant\"")) {
                    fixedContent = fixedContent.substring(0, fixedContent.length - 1) + ",\"assistant\":[]}]"
                }
            }
        }

        val lastChar = fixedContent.lastOrNull()
        if (lastChar != '}' && lastChar != ']') {
            val hasUser = fixedContent.contains("\"user\"")
            val hasAssistant = fixedContent.contains("\"assistant\"")

            when {
                hasUser && hasAssistant -> fixedContent += "]}"
                hasUser && !hasAssistant -> fixedContent += "],\"assistant\":[]}"
                !hasUser && hasAssistant -> fixedContent += "}"
                else -> fixedContent += "}"
            }
        }

        return fixedContent
    }

    fun validateJsonStructure(content: String): Boolean {
        val trimmed = content.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false
        }

        val openBraces = trimmed.count { it == '{' }
        val closeBraces = trimmed.count { it == '}' }
        if (openBraces != closeBraces) {
            return false
        }

        val openArrays = trimmed.count { it == '[' }
        val closeArrays = trimmed.count { it == ']' }
        if (openArrays != closeArrays) {
            return false
        }

        return true
    }
}
