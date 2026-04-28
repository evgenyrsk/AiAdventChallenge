package com.example.mcp.server.service.document

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class ProjectContextServiceTest {
    @Test
    fun `returns branch and diff summary from git repo`() {
        val root = createTempDirectory("project-context-service").toFile()
        run(root, "git", "init")
        run(root, "git", "checkout", "-b", "main")
        File(root, "README.md").writeText("hello")

        val service = ProjectContextService(root)
        val branch = service.getGitBranch()
        val diff = service.getGitDiffSummary()
        val files = service.listProjectFiles(limit = 10)

        assertTrue(branch.isGitRepo)
        assertTrue(branch.branch.isNullOrBlank().not())
        assertNull(branch.error)
        assertTrue(diff.summary.orEmpty().contains("README.md"))
        assertNull(diff.error)
        assertFalse(files.files.isEmpty())
        assertTrue(files.totalCount >= files.files.size)
    }

    @Test
    fun `returns typed non git results`() {
        val root = createTempDirectory("project-context-non-git").toFile()
        val service = ProjectContextService(root)

        val branch = service.getGitBranch()
        val diff = service.getGitDiffSummary()

        assertFalse(branch.isGitRepo)
        assertTrue(branch.error.orEmpty().contains("not a git repository"))
        assertFalse(diff.isGitRepo)
        assertTrue(diff.error.orEmpty().contains("not a git repository"))
    }

    @Test
    fun `list project files excludes service directories by path segment`() {
        val root = createTempDirectory("project-context-files").toFile()
        File(root, "README.md").writeText("root")
        File(root, "docs").mkdirs()
        File(root, "docs/architecture.md").writeText("docs")
        File(root, "app/src/main/java").mkdirs()
        File(root, "app/src/main/java/App.kt").writeText("class App")

        File(root, ".opencode/plans").mkdirs()
        File(root, ".opencode/plans/plan.md").writeText("hidden")
        File(root, "mcp-server/output").mkdirs()
        File(root, "mcp-server/output/report.md").writeText("generated")
        File(root, "rag-core/build/tmp").mkdirs()
        File(root, "rag-core/build/tmp/build.log").writeText("generated")
        File(root, ".idea").mkdirs()
        File(root, ".idea/workspace.xml").writeText("ide")

        val service = ProjectContextService(root)
        val result = service.listProjectFiles(limit = 50)

        assertTrue(result.files.contains("README.md"))
        assertTrue(result.files.contains("docs/architecture.md"))
        assertTrue(result.files.contains("app/src/main/java/App.kt"))

        assertFalse(result.files.any { it.startsWith(".opencode/") })
        assertFalse(result.files.any { it.contains("/output/") || it.startsWith("output/") })
        assertFalse(result.files.any { it.contains("/build/") || it.startsWith("build/") })
        assertFalse(result.files.any { it.startsWith(".idea/") })
        assertEquals(result.files.size, result.totalCount)
    }

    private fun run(root: File, vararg command: String) {
        val process = ProcessBuilder(command.toList())
            .directory(root)
            .redirectErrorStream(true)
            .start()
        check(process.waitFor() == 0) {
            process.inputStream.bufferedReader().use { it.readText() }
        }
    }
}
