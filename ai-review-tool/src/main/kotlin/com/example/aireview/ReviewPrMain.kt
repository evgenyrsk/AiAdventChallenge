package com.example.aireview

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.SocketTimeoutException
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    prettyPrint = false
}

private val defaultConfig = mapOf<String, Any>(
    "max_diff_chars" to 60000,
    "max_docs_context_chars" to 25000,
    "max_changed_files" to 80,
    "max_file_context_chars" to 2500,
    "max_doc_file_chars" to 5000,
    "max_keyword_terms" to 80,
    "request_timeout_seconds" to 90,
    "max_response_tokens" to 1600,
    "temperature" to 0.1,
    "doc_roots" to listOf(
        "README.md",
        "docs",
        "MCP_README.md",
        "MCP_INTEGRATION.md",
        "IMPLEMENTATION_SUMMARY.md",
        "ANDROID_INTEGRATION_SUMMARY.md",
        "CONTEXT_STRATEGIES.md"
    ),
    "source_extensions" to listOf(
        ".kt",
        ".kts",
        ".java",
        ".xml",
        ".gradle",
        ".properties",
        ".md",
        ".json",
        ".yaml",
        ".yml"
    ),
    "exclude_paths" to listOf(
        ".git",
        ".gradle",
        ".idea",
        "build",
        "generated",
        "node_modules",
        "mcp-server/output",
        "output",
        "tools/ai-review/output"
    ),
    "exclude_extensions" to listOf(
        ".png",
        ".jpg",
        ".jpeg",
        ".webp",
        ".gif",
        ".mp4",
        ".apk",
        ".aab",
        ".db",
        ".jar",
        ".keystore",
        ".lock"
    )
)

data class ReviewConfig(
    val maxDiffChars: Int,
    val maxDocsContextChars: Int,
    val maxChangedFiles: Int,
    val maxFileContextChars: Int,
    val maxDocFileChars: Int,
    val maxKeywordTerms: Int,
    val requestTimeoutSeconds: Int,
    val maxResponseTokens: Int,
    val temperature: Double,
    val docRoots: List<String>,
    val sourceExtensions: Set<String>,
    val excludePaths: List<String>,
    val excludeExtensions: Set<String>
)

data class RuntimeConfig(
    val backend: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val timeoutSeconds: Int,
    val maxResponseTokens: Int,
    val temperature: Double
)

data class ContextBundle(
    val text: String,
    val truncated: Boolean,
    val selectedSources: List<String>
)

data class CliArgs(
    val repoRoot: File,
    val diffFile: File,
    val changedFilesFile: File,
    val configFile: File,
    val promptTemplateFile: File,
    val outputFile: File
)

data class TruncatedText(
    val text: String,
    val truncated: Boolean
)

fun main(args: Array<String>) {
    val exitCode = runCatching {
        runReview(parseArgs(args))
        0
    }.getOrElse { error ->
        System.err.println("AI review runner failed: ${redact(error.message ?: error::class.java.simpleName)}")
        1
    }
    exitProcess(exitCode)
}

private fun runReview(args: CliArgs) {
    val repoRoot = args.repoRoot.canonicalFile
    val config = loadReviewConfig(args.configFile)
    val runtime = loadRuntimeConfig(config)

    val rawDiff = args.diffFile.readTextOrEmpty()
    val changedFiles = args.changedFilesFile
        .readTextOrEmpty()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .filter { shouldIncludePath(it, config) }
        .toList()

    val limitedChangedFiles = changedFiles.take(config.maxChangedFiles)
    val filesTruncated = changedFiles.size > limitedChangedFiles.size
    val filteredDiff = filterDiffByFiles(rawDiff, limitedChangedFiles.toSet(), config)
    val diff = truncate(filteredDiff, config.maxDiffChars)
    val keywords = extractKeywords(diff.text + "\n" + limitedChangedFiles.joinToString("\n"), config.maxKeywordTerms)
    val ragContext = buildRagContext(repoRoot, limitedChangedFiles, keywords, config)
    val promptTemplate = args.promptTemplateFile.readTextOrEmpty()
    val prompt = buildPrompt(
        promptTemplate = promptTemplate,
        diffText = diff.text,
        changedFiles = limitedChangedFiles,
        ragContext = ragContext,
        diffTruncated = diff.truncated,
        filesTruncated = filesTruncated,
        runtime = runtime
    )

    args.outputFile.parentFile?.mkdirs()
    val review = runCatching {
        callLlm(runtime, prompt).ifBlank {
            error("LLM backend returned an empty review")
        }
    }.getOrElse { error ->
        buildFailureReview(error, diff.truncated, filesTruncated, ragContext)
    }.trimEnd() + "\n"

    args.outputFile.writeText(review)
    printReviewSummary(review, args.outputFile, limitedChangedFiles, ragContext)
    appendGithubSummary(review)
}

private fun parseArgs(args: Array<String>): CliArgs {
    val values = mutableMapOf<String, String>()
    var index = 0
    while (index < args.size) {
        val key = args[index]
        require(key.startsWith("--")) { "Unexpected argument: $key" }
        val value = args.getOrNull(index + 1)
        require(value != null && !value.startsWith("--")) { "Missing value for $key" }
        values[key.removePrefix("--")] = value
        index += 2
    }

    fun required(name: String): File = File(requireNotNull(values[name]) { "Missing --$name" })
    return CliArgs(
        repoRoot = File(values["repo-root"] ?: "."),
        diffFile = required("diff-file"),
        changedFilesFile = required("changed-files-file"),
        configFile = File(values["config"] ?: "tools/ai-review/config/review_config.yaml"),
        promptTemplateFile = File(values["prompt-template"] ?: "tools/ai-review/prompts/code_review_prompt.md"),
        outputFile = File(values["output"] ?: "tools/ai-review/output/review.md")
    )
}

private fun loadReviewConfig(file: File): ReviewConfig {
    val raw = defaultConfig.toMutableMap()
    raw.putAll(parseSimpleYaml(file))
    return ReviewConfig(
        maxDiffChars = raw.intValue("max_diff_chars"),
        maxDocsContextChars = raw.intValue("max_docs_context_chars"),
        maxChangedFiles = raw.intValue("max_changed_files"),
        maxFileContextChars = raw.intValue("max_file_context_chars"),
        maxDocFileChars = raw.intValue("max_doc_file_chars"),
        maxKeywordTerms = raw.intValue("max_keyword_terms"),
        requestTimeoutSeconds = raw.intValue("request_timeout_seconds"),
        maxResponseTokens = raw.intValue("max_response_tokens"),
        temperature = raw.doubleValue("temperature"),
        docRoots = raw.stringListValue("doc_roots"),
        sourceExtensions = raw.stringListValue("source_extensions").toSet(),
        excludePaths = raw.stringListValue("exclude_paths"),
        excludeExtensions = raw.stringListValue("exclude_extensions").toSet()
    )
}

private fun parseSimpleYaml(file: File): Map<String, Any> {
    if (!file.isFile) return emptyMap()

    val result = linkedMapOf<String, Any>()
    var currentKey: String? = null
    file.readLines().forEach { rawLine ->
        val line = rawLine.substringBefore("#").trimEnd()
        if (line.isBlank()) return@forEach
        if (line.startsWith("  - ") && currentKey != null) {
            val existing = result.getOrPut(currentKey!!) { mutableListOf<Any>() }
            @Suppress("UNCHECKED_CAST")
            val list = existing as? MutableList<Any>
                ?: error("Config key $currentKey cannot contain both scalar and list values")
            list += parseScalar(line.removePrefix("  - ").trim())
            return@forEach
        }
        if (!line.startsWith(" ") && ":" in line) {
            val key = line.substringBefore(":").trim()
            val value = line.substringAfter(":").trim()
            currentKey = key
            result[key] = if (value.isBlank()) mutableListOf<Any>() else parseScalar(value)
        }
    }
    return result
}

private fun parseScalar(value: String): Any {
    val unquoted = value.trim().trim('"', '\'')
    return when {
        unquoted.equals("true", ignoreCase = true) -> true
        unquoted.equals("false", ignoreCase = true) -> false
        unquoted.matches(Regex("-?\\d+")) -> unquoted.toInt()
        unquoted.matches(Regex("-?\\d+\\.\\d+")) -> unquoted.toDouble()
        else -> unquoted
    }
}

private fun loadRuntimeConfig(config: ReviewConfig): RuntimeConfig {
    val env = System.getenv()
    return RuntimeConfig(
        backend = env["AI_REVIEW_BACKEND"]?.trim()?.lowercase().orEmpty().ifBlank { "private" },
        baseUrl = env["AI_REVIEW_BASE_URL"]?.trim()?.trimEnd('/').orEmpty(),
        apiKey = env["AI_REVIEW_API_KEY"]?.trim().orEmpty(),
        model = env["AI_REVIEW_MODEL"]?.trim().orEmpty(),
        timeoutSeconds = env["AI_REVIEW_TIMEOUT_SECONDS"]?.toIntOrNull() ?: config.requestTimeoutSeconds,
        maxResponseTokens = env["AI_REVIEW_MAX_TOKENS"]?.toIntOrNull() ?: config.maxResponseTokens,
        temperature = env["AI_REVIEW_TEMPERATURE"]?.toDoubleOrNull() ?: config.temperature
    )
}

private fun shouldIncludePath(path: String, config: ReviewConfig): Boolean {
    val normalized = path.replace("\\", "/").trim('/')
    val parts = normalized.split('/').filter { it.isNotBlank() }
    if (parts.any { it in config.excludePaths }) return false
    if (config.excludePaths.any { normalized == it.trimEnd('/') || normalized.startsWith("${it.trimEnd('/')}/") }) {
        return false
    }
    val extension = File(normalized).extension.lowercase().takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
    return extension !in config.excludeExtensions
}

private fun filterDiffByFiles(diffText: String, allowedFiles: Set<String>, config: ReviewConfig): String {
    if (diffText.isBlank() || allowedFiles.isEmpty()) return ""

    val blocks = mutableListOf<String>()
    val current = StringBuilder()
    var currentPath: String? = null

    diffText.lineSequence().forEach { line ->
        val lineWithBreak = "$line\n"
        if (line.startsWith("diff --git ")) {
            if (current.isNotEmpty() && currentPath != null && shouldKeepDiffBlock(currentPath!!, allowedFiles, config)) {
                blocks += current.toString()
            }
            current.clear()
            current.append(lineWithBreak)
            currentPath = parseDiffPath(line)
        } else {
            current.append(lineWithBreak)
        }
    }
    if (current.isNotEmpty() && currentPath != null && shouldKeepDiffBlock(currentPath!!, allowedFiles, config)) {
        blocks += current.toString()
    }
    return blocks.joinToString("")
}

private fun parseDiffPath(line: String): String? {
    val match = Regex("""diff --git a/(.*?) b/(.*)""").find(line.trim()) ?: return null
    val before = match.groupValues[1]
    val after = match.groupValues[2]
    return if (after == "/dev/null") before else after
}

private fun shouldKeepDiffBlock(path: String, allowedFiles: Set<String>, config: ReviewConfig): Boolean {
    return path in allowedFiles && shouldIncludePath(path, config)
}

private fun truncate(text: String, maxChars: Int): TruncatedText {
    if (text.length <= maxChars) return TruncatedText(text, false)
    val marker = "\n\n[TRUNCATED: original content had ${text.length} chars; kept first $maxChars chars]\n"
    val keep = max(0, maxChars - marker.length)
    return TruncatedText(text.take(keep) + marker, true)
}

private fun extractKeywords(text: String, limit: Int): List<String> {
    val stop = setOf(
        "the",
        "and",
        "for",
        "with",
        "this",
        "that",
        "from",
        "import",
        "class",
        "private",
        "public",
        "return",
        "package",
        "val",
        "var",
        "fun",
        "как",
        "для",
        "что",
        "или",
        "это"
    )
    return Regex("""[A-Za-z][A-Za-z0-9_]{2,}|[А-Яа-яЁё][А-Яа-яЁё0-9_]{2,}""")
        .findAll(text)
        .map { it.value.lowercase() }
        .filter { it !in stop && it.length >= 3 }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .take(limit)
        .map { it.key }
}

private fun buildRagContext(
    root: File,
    changedFiles: List<String>,
    keywords: List<String>,
    config: ReviewConfig
): ContextBundle {
    val candidates = collectDocCandidates(root, config) + collectNearbyFileCandidates(root, changedFiles, config)
    val seen = mutableSetOf<String>()
    val scored = candidates
        .filter { (source, text) -> source !in seen && text.isNotBlank() && seen.add(source) }
        .map { (source, text) -> Triple(scoreText(source, text, keywords, changedFiles), source, text) }
        .sortedWith(compareByDescending<Triple<Int, String, String>> { it.first }.thenBy { it.second })

    val selectedBlocks = mutableListOf<String>()
    val selectedSources = mutableListOf<String>()
    var totalChars = 0
    var truncated = false

    for ((_, source, text) in scored) {
        val maxChars = if (source.endsWith(".md") || source.endsWith(".markdown") || source.endsWith(".txt")) {
            config.maxDocFileChars
        } else {
            config.maxFileContextChars
        }
        val snippet = truncate(text, maxChars)
        val block = "\n\n--- Source: $source ---\n${snippet.text.trim()}\n"
        if (totalChars + block.length > config.maxDocsContextChars) {
            truncated = true
            break
        }
        selectedBlocks += block
        selectedSources += source
        totalChars += block.length
        if (totalChars >= config.maxDocsContextChars) {
            truncated = true
            break
        }
    }

    if (selectedBlocks.isEmpty()) {
        return ContextBundle(
            text = "No repository documentation or nearby source context was selected.",
            truncated = false,
            selectedSources = emptyList()
        )
    }
    return ContextBundle(
        text = selectedBlocks.joinToString("").trim(),
        truncated = truncated,
        selectedSources = selectedSources
    )
}

private fun collectDocCandidates(root: File, config: ReviewConfig): List<Pair<String, String>> {
    val candidates = mutableListOf<Pair<String, String>>()
    config.docRoots.forEach { entry ->
        val path = safeJoin(root, entry) ?: return@forEach
        when {
            path.isFile -> candidates += entry to path.readTextOrEmpty()
            path.isDirectory -> path.walkTopDown()
                .filter { it.isFile }
                .filter { it.extension.lowercase() in setOf("md", "markdown", "txt") }
                .forEach { file ->
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    if (shouldIncludePath(relative, config)) {
                        candidates += relative to file.readTextOrEmpty()
                    }
                }
        }
    }
    root.listFiles { file -> file.isFile && file.extension.equals("md", ignoreCase = true) }
        ?.sortedBy { it.name }
        ?.forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            if (shouldIncludePath(relative, config)) {
                candidates += relative to file.readTextOrEmpty()
            }
        }
    return candidates
}

private fun collectNearbyFileCandidates(root: File, changedFiles: List<String>, config: ReviewConfig): List<Pair<String, String>> {
    val candidates = mutableListOf<Pair<String, String>>()
    changedFiles.forEach { changed ->
        val path = safeJoin(root, changed)
        if (path?.isFile == true && ".${path.extension.lowercase()}" in config.sourceExtensions) {
            candidates += changed to path.readTextOrEmpty()
        }
        val parent = path?.parentFile ?: safeJoin(root, File(changed).parent.orEmpty())
        if (parent?.isDirectory == true) {
            parent.listFiles()
                ?.filter { it.isFile }
                ?.sortedBy { it.name }
                ?.forEach { sibling ->
                    val relative = sibling.relativeTo(root).invariantSeparatorsPath
                    if (
                        relative != changed &&
                        ".${sibling.extension.lowercase()}" in config.sourceExtensions &&
                        shouldIncludePath(relative, config)
                    ) {
                        candidates += relative to sibling.readTextOrEmpty()
                    }
                }
        }
    }
    return candidates
}

private fun safeJoin(root: File, relative: String): File? {
    val candidate = File(root, relative).canonicalFile
    return if (candidate.toPath().startsWith(root.canonicalFile.toPath())) candidate else null
}

private fun scoreText(source: String, text: String, keywords: List<String>, changedFiles: List<String>): Int {
    val haystack = (source + "\n" + text.take(20000)).lowercase()
    var score = keywords.count { it in haystack }
    val sourceParts = source.lowercase().replace(".", "/").split('/').toSet()
    changedFiles.forEach { changed ->
        val changedParts = changed.lowercase().replace(".", "/").split('/').toSet()
        score += 4 * sourceParts.intersect(changedParts).size
    }
    if (source == "README.md") score += 8
    if (source.startsWith("docs/")) score += 5
    return score
}

private fun buildPrompt(
    promptTemplate: String,
    diffText: String,
    changedFiles: List<String>,
    ragContext: ContextBundle,
    diffTruncated: Boolean,
    filesTruncated: Boolean,
    runtime: RuntimeConfig
): String {
    val changedFilesText = changedFiles.joinToString("\n") { "- $it" }.ifBlank {
        "- No changed files after filtering."
    }
    val metadata = buildJsonObject {
        put("backend", runtime.backend)
        put("diff_truncated", diffTruncated)
        put("changed_files_truncated", filesTruncated)
        put("rag_context_truncated", ragContext.truncated)
        put(
            "selected_context_sources",
            buildJsonArray { ragContext.selectedSources.forEach { add(JsonPrimitive(it)) } }
        )
    }

    return """
        |${promptTemplate.trim()}
        |
        |## Review Metadata
        |```json
        |${json.encodeToString(JsonObject.serializer(), metadata)}
        |```
        |
        |## Changed Files
        |$changedFilesText
        |
        |## RAG Context
        |${ragContext.text}
        |
        |## Pull Request Diff
        |```diff
        |$diffText
        |```
    """.trimMargin()
}

private fun callLlm(runtime: RuntimeConfig, prompt: String): String {
    require(runtime.baseUrl.isNotBlank()) { "AI_REVIEW_BASE_URL is required" }
    return when (runtime.backend) {
        "private" -> callPrivateBackend(runtime, prompt)
        "cloud" -> callCloudBackend(runtime, prompt)
        "ollama" -> callOllamaBackend(runtime, prompt)
        else -> error("Unsupported AI_REVIEW_BACKEND: ${runtime.backend}")
    }
}

private fun callPrivateBackend(runtime: RuntimeConfig, prompt: String): String {
    val payload = buildJsonObject {
        putMessages(prompt)
        put("temperature", runtime.temperature)
        put("maxTokens", runtime.maxResponseTokens)
        if (runtime.model.isNotBlank()) put("model", runtime.model)
    }
    val response = postJson("${runtime.baseUrl}/v1/chat", payload, runtime)
    return response["message"]
        ?.jsonObject
        ?.get("content")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        .orEmpty()
}

private fun callCloudBackend(runtime: RuntimeConfig, prompt: String): String {
    val payload = buildJsonObject {
        put("model", runtime.model)
        putMessages(prompt)
        put("temperature", runtime.temperature)
        put("max_tokens", runtime.maxResponseTokens)
    }
    val response = postJson("${runtime.baseUrl}/v1/chat/completions", payload, runtime)
    return response["choices"]
        ?.jsonArray
        ?.firstOrNull()
        ?.jsonObject
        ?.get("message")
        ?.jsonObject
        ?.get("content")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        .orEmpty()
}

private fun callOllamaBackend(runtime: RuntimeConfig, prompt: String): String {
    val payload = buildJsonObject {
        put("model", runtime.model)
        putMessages(prompt)
        put("stream", false)
        put(
            "options",
            buildJsonObject {
                put("temperature", runtime.temperature)
                put("num_predict", runtime.maxResponseTokens)
            }
        )
    }
    val response = postJson("${runtime.baseUrl}/api/chat", payload, runtime)
    return response["message"]
        ?.jsonObject
        ?.get("content")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.trim()
        .orEmpty()
}

private fun JsonObjectBuilder.putMessages(prompt: String) {
    put(
        "messages",
        buildJsonArray {
            add(
                buildJsonObject {
                    put("role", "system")
                    put("content", "You are a precise code reviewer. Return Markdown only.")
                }
            )
            add(
                buildJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            )
        }
    )
}

private typealias JsonObjectBuilder = kotlinx.serialization.json.JsonObjectBuilder

private fun postJson(url: String, payload: JsonObject, runtime: RuntimeConfig): JsonObject {
    val client = OkHttpClient.Builder()
        .connectTimeout(runtime.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .readTimeout(runtime.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .writeTimeout(runtime.timeoutSeconds.toLong(), TimeUnit.SECONDS)
        .build()
    val body = json.encodeToString(JsonObject.serializer(), payload)
        .toRequestBody("application/json; charset=utf-8".toMediaType())
    val requestBuilder = Request.Builder()
        .url(url)
        .post(body)
        .header("Accept", "application/json")
    if (runtime.apiKey.isNotBlank()) {
        requestBuilder.header("Authorization", "Bearer ${runtime.apiKey}")
    }

    var responseText = ""
    val elapsedMs = try {
        measureTimeMillis {
            client.newCall(requestBuilder.build()).execute().use { response ->
                responseText = response.body.string()
                if (!response.isSuccessful) {
                    error("LLM request failed with HTTP ${response.code}: ${redact(responseText)}")
                }
            }
        }
    } catch (timeout: SocketTimeoutException) {
        error("LLM request timed out after ${runtime.timeoutSeconds}s")
    }
    println("LLM request completed in ${elapsedMs}ms using backend=${runtime.backend}")
    return json.parseToJsonElement(responseText).jsonObject
}

private fun buildFailureReview(
    error: Throwable,
    diffTruncated: Boolean,
    filesTruncated: Boolean,
    ragContext: ContextBundle
): String {
    return """
        |# AI Code Review
        |
        |## Summary
        |AI review could not call the configured LLM backend. The workflow still collected the PR diff, changed files, and lightweight RAG context.
        |
        |Backend error: `${redact(error.message ?: error::class.java.simpleName)}`
        |
        |Context status: diff_truncated=$diffTruncated, changed_files_truncated=$filesTruncated, rag_context_truncated=${ragContext.truncated}.
        |
        |## Potential Bugs
        |- None found from the provided context because the LLM backend call failed.
        |
        |## Architecture Concerns
        |- None found from the provided context because the LLM backend call failed.
        |
        |## Maintainability
        |- None found from the provided context because the LLM backend call failed.
        |
        |## Recommendations
        |- Check `AI_REVIEW_BACKEND`, `AI_REVIEW_BASE_URL`, `AI_REVIEW_API_KEY`, and network reachability from the runner.
        |
        |## Questions
        |- Should this repository use a GitHub-reachable private AI service, a cloud-compatible endpoint, or a self-hosted runner with Ollama?
    """.trimMargin()
}

private fun printReviewSummary(
    review: String,
    outputFile: File,
    changedFiles: List<String>,
    ragContext: ContextBundle
) {
    println("AI review written to ${outputFile.path}")
    println("Filtered changed files: ${changedFiles.size}")
    println("Selected RAG context sources: ${ragContext.selectedSources.size}")
    ragContext.selectedSources.take(20).forEach { source -> println("- $source") }
    println()
    println(review)
}

private fun appendGithubSummary(review: String) {
    val summaryPath = System.getenv("GITHUB_STEP_SUMMARY")?.takeIf { it.isNotBlank() } ?: return
    File(summaryPath).appendText(review.trimEnd() + "\n")
}

private fun File.readTextOrEmpty(charset: Charset = Charsets.UTF_8): String {
    return if (isFile) readText(charset) else ""
}

private fun Map<String, Any>.intValue(key: String): Int = when (val value = requireNotNull(this[key])) {
    is Number -> value.toInt()
    is String -> value.toInt()
    else -> error("Config value $key must be an integer")
}

private fun Map<String, Any>.doubleValue(key: String): Double = when (val value = requireNotNull(this[key])) {
    is Number -> value.toDouble()
    is String -> value.toDouble()
    else -> error("Config value $key must be a number")
}

private fun Map<String, Any>.stringListValue(key: String): List<String> {
    val value = requireNotNull(this[key])
    return when (value) {
        is List<*> -> value.map { it.toString() }
        is String -> listOf(value)
        else -> error("Config value $key must be a list")
    }
}

private fun redact(text: String): String {
    return text.replace(Regex("""Bearer\s+[A-Za-z0-9._~+/=-]+"""), "Bearer [REDACTED]")
}
