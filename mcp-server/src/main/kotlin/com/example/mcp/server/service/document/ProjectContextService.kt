package com.example.mcp.server.service.document

import com.example.mcp.server.documentindex.document.DocumentLoader
import java.io.File
import java.util.concurrent.TimeUnit

class ProjectContextService(
    private val projectRoot: File = findProjectRoot()
) {
    fun getGitBranch(): GitBranchResult {
        if (!File(projectRoot, ".git").exists()) {
            return GitBranchResult(
                branch = null,
                isGitRepo = false,
                error = "Project root is not a git repository"
            )
        }

        return sequenceOf(
            arrayOf("symbolic-ref", "--short", "HEAD"),
            arrayOf("rev-parse", "--abbrev-ref", "HEAD")
        ).firstNotNullOfOrNull { command ->
            runGitCommand(*command).getOrNull()
        }?.let { output ->
            GitBranchResult(
                branch = output.lineSequence().firstOrNull()?.trim().orEmpty(),
                isGitRepo = true,
                error = null
            )
        } ?: GitBranchResult(
            branch = null,
            isGitRepo = true,
            error = "git command failed"
        )
    }

    fun listProjectFiles(limit: Int = 200): ProjectFilesResult {
        val allowedExtensions = setOf(
            "md", "markdown", "txt", "kt", "kts", "java", "xml", "json", "yaml", "yml", "gradle", "properties"
        )

        val files = projectRoot.walkTopDown()
            .onEnter { directory ->
                !containsIgnoredPathSegment(directory.relativePathFrom(projectRoot))
            }
            .filter { it.isFile }
            .filterNot { file ->
                containsIgnoredPathSegment(file.relativePathFrom(projectRoot)) ||
                    file.name.endsWith(".iml") ||
                    file.name.endsWith(".log") ||
                    file.name.endsWith(".db")
            }
            .filter { file ->
                val extension = file.extension.lowercase()
                extension in allowedExtensions || file.name.equals("README.md", ignoreCase = true)
            }
            .map { it.relativeTo(projectRoot).invariantSeparatorsPath }
            .sorted()
            .toList()

        return ProjectFilesResult(
            files = files.take(limit),
            totalCount = files.size,
            truncated = files.size > limit
        )
    }

    private fun containsIgnoredPathSegment(relativePath: String): Boolean {
        if (relativePath.isBlank()) return false
        return relativePath
            .split('/')
            .any { it in IGNORED_PATH_SEGMENTS }
    }

    private fun File.relativePathFrom(root: File): String {
        return if (this == root) "" else this.relativeTo(root).invariantSeparatorsPath
    }

    fun getGitDiffSummary(maxChars: Int = 4000): GitDiffSummaryResult {
        if (!File(projectRoot, ".git").exists()) {
            return GitDiffSummaryResult(
                summary = null,
                isGitRepo = false,
                error = "Project root is not a git repository"
            )
        }

        val status = runGitCommand("status", "--short").getOrElse {
            return GitDiffSummaryResult(
                summary = null,
                isGitRepo = true,
                error = it.message ?: "git status failed"
            )
        }
        val diffStat = runGitCommand("diff", "--stat").getOrElse {
            return GitDiffSummaryResult(
                summary = null,
                isGitRepo = true,
                error = it.message ?: "git diff --stat failed"
            )
        }

        val summary = buildString {
            appendLine("git status --short:")
            appendLine(status.ifBlank { "clean" })
            appendLine()
            appendLine("git diff --stat:")
            append(diffStat.ifBlank { "no unstaged diff" })
        }.trim()

        return GitDiffSummaryResult(
            summary = summary.take(maxChars),
            isGitRepo = true,
            truncated = summary.length > maxChars,
            error = null
        )
    }

    private fun runGitCommand(vararg args: String): Result<String> {
        return runCatching {
            val process = ProcessBuilder(listOf("git", *args))
                .directory(projectRoot)
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                error("git command timed out")
            }
            val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
            if (process.exitValue() != 0) {
                error(output.ifBlank { "git command failed" })
            }
            output
        }
    }

    companion object {
        private val IGNORED_PATH_SEGMENTS: Set<String> = DocumentLoader.DEFAULT_IGNORED_DIRECTORIES

        private fun findProjectRoot(): File {
            var current = File(System.getProperty("user.dir")).canonicalFile
            while (true) {
                val hasSettings = File(current, "settings.gradle.kts").isFile
                val hasGradlew = File(current, "gradlew").isFile
                val hasApp = File(current, "app/build.gradle.kts").isFile
                if (hasSettings && hasGradlew && hasApp) {
                    return current
                }
                current = current.parentFile ?: return File(System.getProperty("user.dir")).canonicalFile
            }
        }
    }
}
