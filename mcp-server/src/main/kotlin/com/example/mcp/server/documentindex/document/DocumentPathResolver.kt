package com.example.mcp.server.documentindex.document

import java.io.File

class DocumentPathResolver(
    private val workingDirectory: File = File(System.getProperty("user.dir")).canonicalFile
) {

    fun resolve(path: String): File {
        val input = File(path)
        val candidates = buildList {
            if (input.isAbsolute) {
                add(input)
            } else {
                add(File(workingDirectory, path))
                findProjectRoot(workingDirectory)?.let { root ->
                    val rootCandidate = File(root, path)
                    if (rootCandidate != lastOrNull()) {
                        add(rootCandidate)
                    }
                }
            }
        }

        candidates.forEach { candidate ->
            val normalized = candidate.canonicalFile
            if (normalized.exists()) {
                return normalized
            }
        }

        val attemptedPaths = candidates.joinToString(", ") { it.canonicalPath }
        throw IllegalArgumentException(
            "Path does not exist: $path (resolved from cwd=${workingDirectory.canonicalPath}; tried: $attemptedPaths)"
        )
    }

    private fun findProjectRoot(start: File): File? {
        var current: File? = start
        while (current != null) {
            val hasSettings = File(current, "settings.gradle.kts").isFile
            val hasGradlew = File(current, "gradlew").isFile
            val hasModule = File(current, "mcp-server/build.gradle.kts").isFile
            if (hasSettings && hasGradlew && hasModule) {
                return current
            }
            current = current.parentFile
        }
        return null
    }
}
