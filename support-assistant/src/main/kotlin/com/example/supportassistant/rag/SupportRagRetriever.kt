package com.example.supportassistant.rag

import com.example.supportassistant.model.FaqEntry
import com.example.supportassistant.model.RetrievedSupportChunk
import com.example.supportassistant.service.SupportDataUnavailableException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.math.min

class SupportRagRetriever(
    private val dataDirectory: String,
    private val topK: Int,
    private val maxContextChars: Int,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
) {
    fun isReady(): Boolean = File(dataDirectory, "faq.json").exists()

    fun retrieve(question: String): List<RetrievedSupportChunk> {
        val entries = loadFaq()
        if (entries.isEmpty()) return emptyList()

        val queryTokens = tokenize(question)
        if (queryTokens.isEmpty()) return emptyList()

        val ranked = entries.mapNotNull { entry ->
            val text = "${entry.title} ${entry.section} ${entry.question} ${entry.answer} ${entry.tags.joinToString(" ")}"
            val entryTokens = tokenize(text)
            val overlap = queryTokens.count { it in entryTokens }
            val tagBoost = entry.tags.count { tag ->
                val normalizedTag = tag.lowercase()
                queryTokens.any { normalizedTag.contains(it) || it.contains(normalizedTag) }
            }
            val exactBoost = when {
                entry.question.contains(question, ignoreCase = true) -> 3
                question.contains(entry.section, ignoreCase = true) -> 2
                else -> 0
            }
            val score = overlap + tagBoost * 1.5 + exactBoost
            if (score < MIN_SCORE) null else {
                RetrievedSupportChunk(
                    entry = entry,
                    score = score,
                    text = "${entry.question}\n${entry.answer}"
                )
            }
        }.sortedByDescending { it.score }

        val selected = mutableListOf<RetrievedSupportChunk>()
        var chars = 0
        for (chunk in ranked) {
            val additional = chunk.text.length + 2
            if (selected.isNotEmpty() && chars + additional > maxContextChars) break
            selected += chunk
            chars += additional
            if (selected.size >= topK.coerceAtLeast(1)) break
        }
        return selected
    }

    private fun loadFaq(): List<FaqEntry> {
        val file = File(dataDirectory, "faq.json")
        if (!file.exists()) {
            throw SupportDataUnavailableException("Support FAQ data file is missing: ${file.canonicalFile.path}")
        }
        return runCatching {
            json.decodeFromString(ListSerializer(FaqEntry.serializer()), file.readText())
        }.getOrElse { error ->
            throw SupportDataUnavailableException("Support FAQ data file is invalid: ${file.canonicalFile.path} (${error.message})")
        }
    }

    private fun tokenize(text: String): Set<String> {
        return text
            .lowercase()
            .replace('ё', 'е')
            .split(Regex("[^\\p{L}\\p{N}_]+"))
            .map { normalizeToken(it) }
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()
    }

    private fun normalizeToken(token: String): String {
        var result = token.trim()
        commonSuffixes.forEach { suffix ->
            if (result.length > suffix.length + 3 && result.endsWith(suffix)) {
                result = result.dropLast(min(suffix.length, result.length))
                return@forEach
            }
        }
        return result
    }

    private companion object {
        val stopWords = setOf(
            "почему", "что", "как", "или", "для", "при", "это", "the", "and", "with", "why", "how"
        )
        val commonSuffixes = listOf("ами", "ями", "ого", "ему", "ыми", "ими", "ать", "ять", "ает", "уют", "ая", "ое", "ый", "ий", "ом", "ам", "ям", "ах", "ях", "ов", "ев", "ия", "ие", "ой", "ей", "ы", "и", "а", "я", "е", "у")
        const val MIN_SCORE = 2.0
    }
}
