package com.example.fileassistant

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.system.exitProcess

private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    prettyPrint = true
}

fun main(args: Array<String>) {
    val exitCode = runCatching {
        val cli = CliArgs.parse(args.toList())
        val config = FileAssistantConfig.load(cli.configFile)
        val tools = ProjectFileTools(cli.repoRoot, config)
        val assistant = FileOpsAssistantUseCase(
            planner = FileAssistantPlanner(),
            tools = tools,
            config = config,
            runtime = RuntimeConfig.fromEnv()
        )
        val result = assistant.run(cli)
        println(if (cli.jsonOutput) json.encodeToString(result) else MarkdownRenderer.render(result))
        if (result.status == ResultStatus.FAILED) 1 else 0
    }.getOrElse { error ->
        System.err.println("File assistant failed: ${error.message ?: error::class.java.simpleName}")
        1
    }
    exitProcess(exitCode)
}

@Serializable
data class FileAssistantResult(
    val goal: String,
    val status: ResultStatus,
    val filesRead: List<String> = emptyList(),
    val filesChanged: List<String> = emptyList(),
    val summary: String,
    val diff: String = "",
    val warnings: List<String> = emptyList(),
    val nextSteps: List<String> = emptyList()
)

@Serializable
enum class ResultStatus {
    SUCCESS,
    PARTIAL,
    FAILED
}

data class CliArgs(
    val repoRoot: File,
    val configFile: File,
    val command: String?,
    val commandArgs: List<String>,
    val goal: String?,
    val apply: Boolean,
    val jsonOutput: Boolean
) {
    val dryRun: Boolean get() = !apply

    companion object {
        fun parse(rawArgs: List<String>): CliArgs {
            var repoRoot = File(".")
            var configFile = File("tools/file-assistant/config/file_assistant_config.yaml")
            var goal: String? = null
            var apply = false
            var jsonOutput = false
            val positional = mutableListOf<String>()
            var index = 0
            while (index < rawArgs.size) {
                val arg = rawArgs[index]
                when (arg) {
                    "--repo-root" -> {
                        repoRoot = File(requiredValue(rawArgs, index, arg))
                        index += 2
                    }
                    "--config" -> {
                        configFile = File(requiredValue(rawArgs, index, arg))
                        index += 2
                    }
                    "--goal" -> {
                        goal = requiredValue(rawArgs, index, arg)
                        index += 2
                    }
                    "--apply" -> {
                        apply = true
                        index += 1
                    }
                    "--dry-run" -> {
                        apply = false
                        index += 1
                    }
                    "--json" -> {
                        jsonOutput = true
                        index += 1
                    }
                    else -> {
                        positional += arg
                        index += 1
                    }
                }
            }

            return CliArgs(
                repoRoot = repoRoot,
                configFile = configFile,
                command = positional.firstOrNull(),
                commandArgs = positional.drop(1),
                goal = goal,
                apply = apply,
                jsonOutput = jsonOutput
            )
        }

        private fun requiredValue(args: List<String>, index: Int, flag: String): String {
            val value = args.getOrNull(index + 1)
            require(!value.isNullOrBlank() && !value.startsWith("--")) { "Missing value for $flag" }
            return value
        }
    }
}

data class FileAssistantConfig(
    val maxReadChars: Int = 120000,
    val maxWriteChars: Int = 120000,
    val maxSearchResults: Int = 120,
    val maxDiffChars: Int = 60000,
    val maxListFiles: Int = 5000,
    val searchContextLines: Int = 2,
    val allowedExtensions: Set<String> = setOf(".kt", ".kts", ".md", ".json", ".yaml", ".yml", ".xml", ".txt"),
    val excludePaths: List<String> = listOf(".git", ".gradle", ".idea", "build", "generated", "node_modules", "output"),
    val secretFileNames: Set<String> = setOf(".env", ".env.local", "local.properties", "secrets.properties", "keystore.properties"),
    val binaryExtensions: Set<String> = setOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4", ".apk", ".aab", ".db", ".jar", ".keystore", ".lock")
) {
    companion object {
        fun load(file: File): FileAssistantConfig {
            val raw = mutableMapOf<String, Any>()
            raw.putAll(defaultMap())
            raw.putAll(parseSimpleYaml(file))
            return FileAssistantConfig(
                maxReadChars = raw.intValue("max_read_chars"),
                maxWriteChars = raw.intValue("max_write_chars"),
                maxSearchResults = raw.intValue("max_search_results"),
                maxDiffChars = raw.intValue("max_diff_chars"),
                maxListFiles = raw.intValue("max_list_files"),
                searchContextLines = raw.intValue("search_context_lines"),
                allowedExtensions = raw.stringListValue("allowed_extensions").toSet(),
                excludePaths = raw.stringListValue("exclude_paths"),
                secretFileNames = raw.stringListValue("secret_file_names").toSet(),
                binaryExtensions = raw.stringListValue("binary_extensions").toSet()
            )
        }

        private fun defaultMap(): Map<String, Any> = mapOf(
            "max_read_chars" to 120000,
            "max_write_chars" to 120000,
            "max_search_results" to 120,
            "max_diff_chars" to 60000,
            "max_list_files" to 5000,
            "search_context_lines" to 2,
            "allowed_extensions" to listOf(".kt", ".kts", ".md", ".json", ".yaml", ".yml", ".xml", ".txt"),
            "exclude_paths" to listOf(".git", ".gradle", ".idea", "build", "generated", "node_modules", "output"),
            "secret_file_names" to listOf(".env", ".env.local", "local.properties", "secrets.properties", "keystore.properties"),
            "binary_extensions" to listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".mp4", ".apk", ".aab", ".db", ".jar", ".keystore", ".lock")
        )
    }
}

data class RuntimeConfig(
    val backend: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String
) {
    companion object {
        fun fromEnv(): RuntimeConfig {
            val env = System.getenv()
            return RuntimeConfig(
                backend = env["FILE_ASSISTANT_LLM_BACKEND"]?.trim()?.lowercase(Locale.US).orEmpty().ifBlank { "none" },
                baseUrl = env["FILE_ASSISTANT_BASE_URL"]?.trim()?.trimEnd('/').orEmpty(),
                apiKey = env["FILE_ASSISTANT_API_KEY"]?.trim().orEmpty(),
                model = env["FILE_ASSISTANT_MODEL"]?.trim().orEmpty()
            )
        }
    }
}

class FileOpsAssistantUseCase(
    private val planner: FileAssistantPlanner,
    private val tools: ProjectFileTools,
    private val config: FileAssistantConfig,
    private val runtime: RuntimeConfig
) {
    fun run(args: CliArgs): FileAssistantResult {
        val plan = planner.plan(args)
        val llmWarnings = llmWarnings()
        return when (plan) {
            is AssistantPlan.FindUsages -> runFindUsages(plan, llmWarnings)
            is AssistantPlan.GenerateAdr -> runGenerateAdr(plan, args.apply, llmWarnings)
            is AssistantPlan.Unsupported -> FileAssistantResult(
                goal = plan.goal,
                status = ResultStatus.FAILED,
                summary = plan.reason,
                warnings = llmWarnings,
                nextSteps = listOf("Use find-usages <Symbol> or generate-adr <topic>.")
            )
        }
    }

    private fun runFindUsages(plan: AssistantPlan.FindUsages, warnings: List<String>): FileAssistantResult {
        val search = tools.searchProjectFiles(
            query = plan.symbol,
            includeGlobs = listOf("**/*.kt", "**/*.kts", "**/*.md", "**/*.xml", "**/*.json", "**/*.yaml", "**/*.yml"),
            excludeGlobs = defaultExcludeGlobs()
        )
        if (search.matches.isEmpty()) {
            return FileAssistantResult(
                goal = plan.goal,
                status = ResultStatus.PARTIAL,
                summary = "No usages found for `${plan.symbol}`.",
                warnings = warnings + search.warnings,
                nextSteps = listOf("Check the symbol name or run with a broader query.")
            )
        }

        val filesToRead = search.matches.map { it.path }.distinct().take(12)
        val readResults = filesToRead.mapNotNull { path ->
            tools.readProjectFile(path).getOrElse { null }
        }
        val report = UsageReportBuilder.build(plan.symbol, search.matches, search.truncated)
        return FileAssistantResult(
            goal = plan.goal,
            status = if (search.truncated) ResultStatus.PARTIAL else ResultStatus.SUCCESS,
            filesRead = readResults.map { it.path },
            summary = report,
            warnings = warnings + search.warnings,
            nextSteps = listOf("Review listed call sites before changing the component API.")
        )
    }

    private fun runGenerateAdr(plan: AssistantPlan.GenerateAdr, apply: Boolean, warnings: List<String>): FileAssistantResult {
        val context = collectAdrContext(plan.topic)
        if (context.filesRead.isEmpty()) {
            return FileAssistantResult(
                goal = plan.goal,
                status = ResultStatus.FAILED,
                summary = "No relevant files found for ADR topic `${plan.topic}`.",
                warnings = warnings,
                nextSteps = listOf("Try one of: file-assistant, local-rag, private-ai-service, mcp.")
            )
        }

        val targetPath = nextAdrPath(plan.topic)
        val content = AdrDraftBuilder.build(plan.topic, context.filesRead, context.findings)
        val write = tools.writeProjectFile(
            path = targetPath,
            content = content,
            mode = WriteMode.CREATE_NEW,
            apply = apply
        )

        return if (write.success) {
            val diff = if (apply) tools.getGitDiff(config.maxDiffChars).diff else write.diff
            FileAssistantResult(
                goal = plan.goal,
                status = ResultStatus.SUCCESS,
                filesRead = context.filesRead,
                filesChanged = if (apply) listOf(targetPath) else emptyList(),
                summary = if (apply) {
                    "Created ADR `${targetPath}` from deterministic project context."
                } else {
                    "Dry-run preview prepared for ADR `${targetPath}`. No files were changed."
                },
                diff = diff,
                warnings = warnings + write.warnings,
                nextSteps = if (apply) {
                    listOf("Review `git diff` and edit the ADR decision/status if needed.")
                } else {
                    listOf("Re-run with `--apply` to create the ADR.")
                }
            )
        } else {
            FileAssistantResult(
                goal = plan.goal,
                status = ResultStatus.FAILED,
                filesRead = context.filesRead,
                summary = "Could not create ADR `${targetPath}`: ${write.message}",
                warnings = warnings + write.warnings,
                nextSteps = listOf("Fix the reported file operation error and retry.")
            )
        }
    }

    private fun collectAdrContext(topic: String): AdrContext {
        val keywords = when (topic) {
            "file-assistant" -> listOf("file assistant", "file-assistant", "ProjectFileTools", "FileOpsAssistant")
            "local-rag" -> listOf("RAG", "retrieve_relevant_chunks", "DocumentIndexingService", "McpRagRetriever")
            "private-ai-service" -> listOf("private AI", "PrivateAi", "OllamaGatewayService", "PRIVATE_AI_SERVICE")
            "mcp" -> listOf("MCP", "McpServer", "McpTool", "DocumentIndexServer")
            else -> listOf(topic)
        }
        val matches = keywords.flatMap { keyword ->
            tools.searchProjectFiles(
                query = keyword,
                includeGlobs = listOf("**/*.kt", "**/*.kts", "**/*.md"),
                excludeGlobs = defaultExcludeGlobs()
            ).matches
        }
            .groupBy { it.path }
            .mapValues { (_, values) -> values.size }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
            .take(10)

        val docsFirst = matches.sortedWith(compareByDescending<String> { it.endsWith(".md") }.thenBy { it })
        val files = docsFirst.mapNotNull { path -> tools.readProjectFile(path).getOrNull() }
        val findings = files.map { file ->
            val lines = file.content.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .filterNot { it.startsWith("import ") || it.startsWith("package ") }
                .take(8)
                .toList()
            AdrFinding(file.path, lines)
        }
        return AdrContext(files.map { it.path }, findings)
    }

    private fun nextAdrPath(topic: String): String {
        val existing = tools.listProjectFiles(
            includeGlobs = listOf("docs/adr/*.md"),
            excludeGlobs = defaultExcludeGlobs()
        ).files
        val nextNumber = existing.mapNotNull { path ->
            Regex("""docs/adr/(\d{4})-.*\.md""").matchEntire(path)?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull()?.plus(1) ?: 1
        return "docs/adr/${nextNumber.toString().padStart(4, '0')}-$topic.md"
    }

    private fun llmWarnings(): List<String> {
        if (runtime.backend == "none") {
            return listOf("LLM backend is disabled; using deterministic file analysis only.")
        }
        if (runtime.backend !in setOf("private", "ollama", "cloud")) {
            return listOf("Unsupported FILE_ASSISTANT_LLM_BACKEND `${runtime.backend}`; using deterministic file analysis only.")
        }
        if (runtime.baseUrl.isBlank()) {
            return listOf("FILE_ASSISTANT_BASE_URL is not set for `${runtime.backend}` backend; using deterministic file analysis only.")
        }
        return listOf("LLM backend `${runtime.backend}` is configured but v1 keeps file operations deterministic; no file write depends on LLM output.")
    }

    private fun defaultExcludeGlobs(): List<String> = listOf(
        "**/build/**",
        "**/.gradle/**",
        "**/.git/**",
        "**/.idea/**",
        "**/node_modules/**"
    )
}

class FileAssistantPlanner {
    fun plan(args: CliArgs): AssistantPlan {
        val explicitGoal = args.goal
        val goal = explicitGoal ?: listOfNotNull(args.command, args.commandArgs.joinToString(" ").takeIf { it.isNotBlank() })
            .joinToString(" ")
            .ifBlank { "No goal provided" }
        val command = args.command?.lowercase(Locale.US)
        return when {
            command == "find-usages" -> {
                val symbol = args.commandArgs.firstOrNull()?.trim().orEmpty()
                if (symbol.isBlank()) {
                    AssistantPlan.Unsupported(goal, "Missing symbol for find-usages.")
                } else {
                    AssistantPlan.FindUsages(goal, symbol)
                }
            }
            command == "generate-adr" -> {
                AssistantPlan.GenerateAdr(goal, normalizeTopic(args.commandArgs.joinToString("-")))
            }
            explicitGoal != null -> planFromGoal(explicitGoal)
            else -> AssistantPlan.Unsupported(goal, "Unsupported command or goal.")
        }
    }

    private fun planFromGoal(goal: String): AssistantPlan {
        val lower = goal.lowercase(Locale.US)
        if (lower.contains("найди") || lower.contains("find")) {
            val symbol = Regex("""(?:используется|uses?|usage of|for)\s+([A-Za-z_][A-Za-z0-9_.$-]*)""", RegexOption.IGNORE_CASE)
                .find(goal)
                ?.groupValues
                ?.getOrNull(1)
                ?: Regex("""([A-Za-z_][A-Za-z0-9_.$-]{2,})""").findAll(goal).lastOrNull()?.value.orEmpty()
            return if (symbol.isBlank()) {
                AssistantPlan.Unsupported(goal, "Could not extract a symbol from goal.")
            } else {
                AssistantPlan.FindUsages(goal, symbol.trim('.', ',', '`'))
            }
        }
        if (lower.contains("adr") || lower.contains("сгенерируй") || lower.contains("generate")) {
            val topic = when {
                lower.contains("private") -> "private-ai-service"
                lower.contains("rag") -> "local-rag"
                lower.contains("mcp") -> "mcp"
                lower.contains("file") || lower.contains("файл") -> "file-assistant"
                else -> "file-assistant"
            }
            return AssistantPlan.GenerateAdr(goal, topic)
        }
        return AssistantPlan.Unsupported(goal, "Could not map goal to a supported scenario.")
    }

    private fun normalizeTopic(raw: String): String {
        val topic = raw.trim().lowercase(Locale.US).replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
        return topic.ifBlank { "file-assistant" }
    }
}

sealed class AssistantPlan {
    data class FindUsages(val goal: String, val symbol: String) : AssistantPlan()
    data class GenerateAdr(val goal: String, val topic: String) : AssistantPlan()
    data class Unsupported(val goal: String, val reason: String) : AssistantPlan()
}

class ProjectFileTools(
    projectRoot: File,
    private val config: FileAssistantConfig
) {
    private val root: File = findProjectRoot(projectRoot).canonicalFile

    fun readProjectFile(path: String): Result<ReadFileResult> = runCatching {
        val file = resolvePath(path, forWrite = false)
        require(file.isFile) { "File not found: $path" }
        require(file.length() <= config.maxReadChars) {
            "File too large: $path (${file.length()} bytes, limit ${config.maxReadChars})"
        }
        val content = file.readText()
        ReadFileResult(
            path = relativePath(file),
            content = content,
            sizeBytes = file.length(),
            lineCount = content.lineSequence().count()
        )
    }

    fun listProjectFiles(includeGlobs: List<String>, excludeGlobs: List<String>): ListFilesResult {
        val files = root.walkTopDown()
            .onEnter { dir -> isDirectoryAllowed(dir, excludeGlobs) }
            .filter { it.isFile }
            .mapNotNull { file ->
                val relative = relativePath(file)
                if (isPathAllowedForRead(relative, file) && matchesAny(relative, includeGlobs) && !matchesAny(relative, excludeGlobs)) {
                    relative
                } else {
                    null
                }
            }
            .sorted()
            .take(config.maxListFiles + 1)
            .toList()
        return ListFilesResult(
            files = files.take(config.maxListFiles),
            totalCount = files.size,
            truncated = files.size > config.maxListFiles
        )
    }

    fun searchProjectFiles(query: String, includeGlobs: List<String>, excludeGlobs: List<String>): SearchFilesResult {
        require(query.isNotBlank()) { "Search query must not be blank" }
        val matches = mutableListOf<SearchMatch>()
        val warnings = mutableListOf<String>()
        val files = listProjectFiles(includeGlobs, excludeGlobs).files
        for (relative in files) {
            if (matches.size >= config.maxSearchResults) break
            val read = readProjectFile(relative).getOrElse { error ->
                warnings += "${relative}: ${error.message}"
                null
            }
            if (read == null) continue
            val lines = read.content.lines()
            lines.forEachIndexed { index, line ->
                if (matches.size >= config.maxSearchResults) return@forEachIndexed
                if (line.contains(query, ignoreCase = false)) {
                    matches += SearchMatch(
                        path = relative,
                        line = index + 1,
                        snippet = snippet(lines, index, config.searchContextLines)
                    )
                }
            }
        }
        val totalCouldBeMore = matches.size >= config.maxSearchResults
        return SearchFilesResult(matches, totalCouldBeMore, warnings)
    }

    fun writeProjectFile(path: String, content: String, mode: WriteMode, apply: Boolean): WriteFileResult {
        return runCatching {
            require(content.length <= config.maxWriteChars) {
                "Content too large: ${content.length} chars, limit ${config.maxWriteChars}"
            }
            val target = resolvePath(path, forWrite = true)
            val existed = target.exists()
            require(!(mode == WriteMode.CREATE_NEW && existed)) { "File already exists: $path" }
            val oldContent = if (existed) target.readText() else ""
            val diff = unifiedDiff(path, oldContent, content, existed)
            if (apply) {
                target.parentFile?.mkdirs()
                target.writeText(content)
            }
            WriteFileResult(
                success = true,
                path = relativePath(target),
                message = if (apply) "File written" else "Dry-run preview",
                diff = truncate(diff, config.maxDiffChars).text,
                warnings = if (diff.length > config.maxDiffChars) listOf("Diff output truncated.") else emptyList()
            )
        }.getOrElse { error ->
            WriteFileResult(false, path, error.message ?: "Write failed", "", emptyList())
        }
    }

    fun patchProjectFile(path: String, patch: String, apply: Boolean): WriteFileResult {
        return runCatching {
            val target = resolvePath(path, forWrite = true)
            require(target.isFile) { "File not found: $path" }
            val oldContent = target.readText()
            val newContent = UnifiedPatchApplier.apply(oldContent, patch)
            writeProjectFile(path, newContent, WriteMode.CREATE_OR_OVERWRITE, apply)
        }.getOrElse { error ->
            WriteFileResult(false, path, error.message ?: "Patch failed", "", emptyList())
        }
    }

    fun getGitDiff(maxChars: Int = config.maxDiffChars): GitDiffResult {
        val output = runGit("diff").getOrElse { error ->
            return GitDiffResult("", false, error.message ?: "git diff unavailable")
        }
        val truncated = truncate(output, maxChars)
        return GitDiffResult(truncated.text, truncated.truncated, null)
    }

    private fun resolvePath(rawPath: String, forWrite: Boolean): File {
        require(rawPath.isNotBlank()) { "Path must not be blank" }
        val normalizedInput = rawPath.replace("\\", "/").trim()
        require(!File(normalizedInput).isAbsolute) { "Absolute paths are not allowed: $rawPath" }
        require(normalizedInput.split('/').none { it == ".." }) { "Path traversal is not allowed: $rawPath" }
        val candidate = File(root, normalizedInput).canonicalFile
        require(candidate.toPath().startsWith(root.toPath())) { "Path resolves outside project root: $rawPath" }
        val relative = relativePath(candidate)
        require(isPathAllowed(relative, candidate, forWrite)) { "Path is not allowed: $relative" }
        return candidate
    }

    private fun isPathAllowedForRead(relative: String, file: File): Boolean = isPathAllowed(relative, file, forWrite = false)

    private fun isPathAllowed(relative: String, file: File, forWrite: Boolean): Boolean {
        val normalized = relative.replace("\\", "/").trim('/')
        if (normalized.isBlank()) return false
        val parts = normalized.split('/').filter { it.isNotBlank() }
        if (parts.any { it in config.excludePaths }) return false
        if (config.excludePaths.any { excluded ->
                normalized == excluded.trim('/') || normalized.startsWith("${excluded.trim('/')}/")
            }
        ) return false
        if (file.name in config.secretFileNames || file.name.startsWith(".env")) return false
        val extension = file.extension.lowercase(Locale.US).takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        if (extension in config.binaryExtensions) return false
        if (extension !in config.allowedExtensions) return false
        if (forWrite && (normalized.startsWith("docs/adr/") || normalized.startsWith("tools/file-assistant/"))) return true
        return !forWrite || normalized.startsWith("docs/") || normalized.startsWith("tools/")
    }

    private fun isDirectoryAllowed(directory: File, excludeGlobs: List<String>): Boolean {
        val relative = if (directory == root) "" else relativePath(directory)
        if (relative.isBlank()) return true
        val parts = relative.split('/').filter { it.isNotBlank() }
        if (parts.any { it in config.excludePaths }) return false
        return !matchesAny(relative, excludeGlobs)
    }

    private fun matchesAny(path: String, globs: List<String>): Boolean {
        if (globs.isEmpty()) return false
        return globs.any { glob -> globMatches(path, glob) }
    }

    private fun globMatches(path: String, glob: String): Boolean {
        val normalizedGlob = glob.replace("\\", "/").trim()
        if (normalizedGlob == "**/*") return true
        if (normalizedGlob.startsWith("**/*.")) {
            return path.endsWith(normalizedGlob.removePrefix("**/*"), ignoreCase = true)
        }
        if (normalizedGlob.endsWith("/**")) {
            return path.startsWith(normalizedGlob.removeSuffix("/**").trimStart('/'))
        }
        if (normalizedGlob.contains("*")) {
            val regex = globToRegex(normalizedGlob)
            return regex.matches(path)
        }
        return path == normalizedGlob || path.startsWith("${normalizedGlob.trimEnd('/')}/")
    }

    private fun globToRegex(glob: String): Regex {
        val builder = StringBuilder()
        var index = 0
        while (index < glob.length) {
            val char = glob[index]
            if (char == '*') {
                val next = glob.getOrNull(index + 1)
                if (next == '*') {
                    val slash = glob.getOrNull(index + 2)
                    if (slash == '/') {
                        builder.append("(?:.*/)?")
                        index += 3
                    } else {
                        builder.append(".*")
                        index += 2
                    }
                } else {
                    builder.append("[^/]*")
                    index += 1
                }
            } else {
                if (char in setOf('.', '(', ')', '+', '?', '^', '$', '{', '}', '[', ']', '|', '\\')) {
                    builder.append('\\')
                }
                builder.append(char)
                index += 1
            }
        }
        return builder.toString().toRegex()
    }

    private fun snippet(lines: List<String>, hitIndex: Int, contextLines: Int): String {
        val start = max(0, hitIndex - contextLines)
        val end = minOf(lines.lastIndex, hitIndex + contextLines)
        return (start..end).joinToString("\n") { index ->
            val prefix = if (index == hitIndex) ">" else " "
            "$prefix ${index + 1}: ${lines[index].trimEnd()}"
        }
    }

    private fun relativePath(file: File): String = file.canonicalFile.relativeTo(root).invariantSeparatorsPath

    private fun runGit(vararg args: String): Result<String> = runCatching {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(root)
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("git command timed out")
        }
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (process.exitValue() != 0) error(output.ifBlank { "git command failed" })
        output
    }

    companion object {
        private fun findProjectRoot(start: File): File {
            var current = start.canonicalFile
            if (current.isFile) current = current.parentFile
            while (true) {
                if (File(current, "settings.gradle.kts").isFile || File(current, ".git").isDirectory) return current
                current = current.parentFile ?: return start.canonicalFile
            }
        }
    }
}

data class ReadFileResult(
    val path: String,
    val content: String,
    val sizeBytes: Long,
    val lineCount: Int
)

data class ListFilesResult(
    val files: List<String>,
    val totalCount: Int,
    val truncated: Boolean
)

data class SearchFilesResult(
    val matches: List<SearchMatch>,
    val truncated: Boolean,
    val warnings: List<String>
)

data class SearchMatch(
    val path: String,
    val line: Int,
    val snippet: String
)

data class WriteFileResult(
    val success: Boolean,
    val path: String,
    val message: String,
    val diff: String,
    val warnings: List<String>
)

data class GitDiffResult(
    val diff: String,
    val truncated: Boolean,
    val error: String?
)

enum class WriteMode {
    CREATE_NEW,
    CREATE_OR_OVERWRITE
}

data class AdrContext(
    val filesRead: List<String>,
    val findings: List<AdrFinding>
)

data class AdrFinding(
    val path: String,
    val lines: List<String>
)

object UsageReportBuilder {
    fun build(symbol: String, matches: List<SearchMatch>, truncated: Boolean): String {
        val grouped = matches.groupBy { it.path }
        return buildString {
            appendLine("Found ${matches.size} usage(s) of `$symbol` in ${grouped.size} file(s).")
            if (truncated) appendLine("Search results were truncated; narrow the query if exact completeness matters.")
            appendLine()
            grouped.forEach { (path, fileMatches) ->
                appendLine("### $path")
                appendLine("- Role: ${inferRole(path)}")
                appendLine("- Lines: ${fileMatches.joinToString { it.line.toString() }}")
                fileMatches.take(4).forEach { match ->
                    appendLine()
                    appendLine("```text")
                    appendLine(match.snippet)
                    appendLine("```")
                }
                appendLine()
            }
            appendLine("Potential issues to review:")
            appendLine("- API changes to `$symbol` can affect every call site listed above.")
            appendLine("- Test files indicate expected behavior and should be updated together with production changes.")
        }.trim()
    }

    private fun inferRole(path: String): String = when {
        path.contains("/test/") -> "test coverage or fixture usage"
        path.endsWith(".md") -> "documentation reference"
        path.contains("/data/") -> "data layer integration"
        path.contains("/domain/") -> "domain/business logic usage"
        path.contains("/api/") -> "service/API boundary usage"
        else -> "project source usage"
    }
}

object AdrDraftBuilder {
    fun build(topic: String, filesRead: List<String>, findings: List<AdrFinding>): String {
        val title = topic.split('-').joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        return buildString {
            appendLine("# ADR: $title Architecture")
            appendLine()
            appendLine("## Status")
            appendLine()
            appendLine("Proposed")
            appendLine()
            appendLine("## Context")
            appendLine()
            appendLine("This ADR was generated by the file assistant from deterministic repository context.")
            appendLine("The assistant read ${filesRead.size} relevant file(s) and did not rely on unverifiable file names.")
            appendLine()
            appendLine("Relevant files:")
            filesRead.forEach { appendLine("- `$it`") }
            appendLine()
            appendLine("## Decision")
            appendLine()
            appendLine("Keep `$topic` concerns isolated in developer tooling or service modules, and avoid coupling them to the Android fitness chat runtime unless an explicit product flow requires it.")
            appendLine()
            appendLine("## Evidence")
            appendLine()
            findings.forEach { finding ->
                appendLine("### `${finding.path}`")
                finding.lines.take(6).forEach { line ->
                    appendLine("- ${line.take(180)}")
                }
                appendLine()
            }
            appendLine("## Consequences")
            appendLine()
            appendLine("- Developer workflows remain reproducible from CLI commands.")
            appendLine("- File and documentation changes can be reviewed through `git diff`.")
            appendLine("- Future integrations should preserve clear boundaries between developer tooling and user-facing fitness chat behavior.")
            appendLine()
        }
    }
}

object UnifiedPatchApplier {
    fun apply(oldContent: String, patch: String): String {
        val lines = oldContent.lines().toMutableList()
        val patchLines = patch.lines()
        val hunkHeaderIndex = patchLines.indexOfFirst { it.startsWith("@@") }
        require(hunkHeaderIndex >= 0) { "Only unified patches with @@ hunks are supported" }
        val header = patchLines[hunkHeaderIndex]
        val match = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""").find(header)
            ?: error("Invalid unified patch hunk header: $header")
        var cursor = match.groupValues[1].toInt() - 1
        patchLines.drop(hunkHeaderIndex + 1).forEach { line ->
            if (line.isEmpty()) return@forEach
            when (line.first()) {
                ' ' -> {
                    val expected = line.drop(1)
                    require(lines.getOrNull(cursor) == expected) {
                        "Patch context mismatch at line ${cursor + 1}"
                    }
                    cursor += 1
                }
                '-' -> {
                    val expected = line.drop(1)
                    require(lines.getOrNull(cursor) == expected) {
                        "Patch removal mismatch at line ${cursor + 1}"
                    }
                    lines.removeAt(cursor)
                }
                '+' -> {
                    lines.add(cursor, line.drop(1))
                    cursor += 1
                }
                '\\' -> Unit
                else -> error("Unsupported patch line: $line")
            }
        }
        return lines.joinToString("\n")
    }
}

object MarkdownRenderer {
    fun render(result: FileAssistantResult): String = buildString {
        appendLine("# File Assistant Result")
        appendLine()
        appendLine("## Goal")
        appendLine(result.goal)
        appendLine()
        appendLine("## Status")
        appendLine(result.status.name.lowercase(Locale.US))
        appendLine()
        appendLine("## Summary")
        appendLine(result.summary)
        appendLine()
        appendLine("## Files Read")
        appendLine(result.filesRead.ifEmpty { listOf("None") }.joinToString("\n") { "- $it" })
        appendLine()
        appendLine("## Files Changed")
        appendLine(result.filesChanged.ifEmpty { listOf("None") }.joinToString("\n") { "- $it" })
        appendLine()
        appendLine("## Diff")
        if (result.diff.isBlank()) {
            appendLine("No diff.")
        } else {
            appendLine("```diff")
            appendLine(result.diff.trimEnd())
            appendLine("```")
        }
        appendLine()
        appendLine("## Warnings")
        appendLine(result.warnings.ifEmpty { listOf("None") }.joinToString("\n") { "- $it" })
        appendLine()
        appendLine("## Next Steps")
        appendLine(result.nextSteps.ifEmpty { listOf("None") }.joinToString("\n") { "- $it" })
    }.trimEnd()
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
        else -> unquoted
    }
}

private fun Map<String, Any>.intValue(key: String): Int = when (val value = this[key]) {
    is Int -> value
    is String -> value.toInt()
    else -> error("Missing or invalid config value: $key")
}

private fun Map<String, Any>.stringListValue(key: String): List<String> = when (val value = this[key]) {
    is List<*> -> value.map { it.toString() }
    is String -> listOf(value)
    else -> error("Missing or invalid config list: $key")
}

private fun truncate(text: String, maxChars: Int): TruncatedText {
    if (text.length <= maxChars) return TruncatedText(text, false)
    val marker = "\n\n[TRUNCATED: original content had ${text.length} chars; kept first $maxChars chars]\n"
    val keep = max(0, maxChars - marker.length)
    return TruncatedText(text.take(keep) + marker, true)
}

private data class TruncatedText(val text: String, val truncated: Boolean)

private fun unifiedDiff(path: String, oldContent: String, newContent: String, existed: Boolean): String {
    val oldLines = oldContent.lines()
    val newLines = newContent.lines()
    return buildString {
        appendLine("diff --git a/$path b/$path")
        appendLine(if (existed) "--- a/$path" else "--- /dev/null")
        appendLine("+++ b/$path")
        appendLine("@@ -1,${oldLines.size} +1,${newLines.size} @@")
        if (oldContent.isNotEmpty()) oldLines.forEach { appendLine("-$it") }
        if (newContent.isNotEmpty()) newLines.forEach { appendLine("+$it") }
    }
}
