package com.example.fileassistant

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File
import java.nio.file.Files

class ProjectFileToolsTest {
    @Test
    fun `rejects path traversal and absolute paths`() {
        val root = tempProject()
        File(root, "docs/readme.md").writeWithParents("hello")
        val tools = ProjectFileTools(root, testConfig())

        assertTrue(tools.readProjectFile("../outside.md").isFailure)
        assertTrue(tools.readProjectFile(File(root, "docs/readme.md").absolutePath).isFailure)
    }

    @Test
    fun `rejects symlink outside project root`() {
        val root = tempProject()
        val outside = createTempDirectory("file-assistant-outside").toFile()
        File(outside, "secret.md").writeText("secret")
        val link = File(root, "docs/link.md").toPath()
        link.parent.toFile().mkdirs()
        runCatching { Files.createSymbolicLink(link, File(outside, "secret.md").toPath()) }
            .getOrElse { return }

        val tools = ProjectFileTools(root, testConfig())

        assertTrue(tools.readProjectFile("docs/link.md").isFailure)
    }

    @Test
    fun `enforces read and write size limits`() {
        val root = tempProject()
        File(root, "docs/large.md").writeWithParents("abcdef")
        val tools = ProjectFileTools(root, testConfig(maxReadChars = 3, maxWriteChars = 4))

        assertTrue(tools.readProjectFile("docs/large.md").isFailure)
        val write = tools.writeProjectFile("docs/new.md", "12345", WriteMode.CREATE_NEW, apply = false)
        assertFalse(write.success)
        assertContains(write.message, "Content too large")
    }

    @Test
    fun `rejects forbidden directories and unsupported extensions`() {
        val root = tempProject()
        File(root, ".git/config.md").writeWithParents("no")
        File(root, "docs/image.png").writeWithParents("no")
        val tools = ProjectFileTools(root, testConfig())

        assertTrue(tools.readProjectFile(".git/config.md").isFailure)
        assertTrue(tools.readProjectFile("docs/image.png").isFailure)
    }

    @Test
    fun `search returns snippets and truncates at configured limit`() {
        val root = tempProject()
        File(root, "src/A.kt").writeWithParents(
            """
            class A {
                val client = LocalOllamaClient()
                fun call() = LocalOllamaClient()
            }
            """.trimIndent()
        )
        val tools = ProjectFileTools(root, testConfig(maxSearchResults = 1))

        val result = tools.searchProjectFiles("LocalOllamaClient", listOf("**/*.kt"), emptyList())

        assertEquals(1, result.matches.size)
        assertTrue(result.truncated)
        assertContains(result.matches.single().snippet, "LocalOllamaClient")
    }

    @Test
    fun `dry run write does not change file and apply writes file`() {
        val root = tempProject()
        val tools = ProjectFileTools(root, testConfig())

        val dryRun = tools.writeProjectFile("docs/adr/0001-test.md", "# Test\n", WriteMode.CREATE_NEW, apply = false)
        assertTrue(dryRun.success)
        assertFalse(File(root, "docs/adr/0001-test.md").exists())

        val apply = tools.writeProjectFile("docs/adr/0001-test.md", "# Test\n", WriteMode.CREATE_NEW, apply = true)
        assertTrue(apply.success)
        assertEquals("# Test\n", File(root, "docs/adr/0001-test.md").readText())
    }

    @Test
    fun `patch dry run returns diff without changing file`() {
        val root = tempProject()
        File(root, "docs/a.md").writeWithParents("one\ntwo")
        val tools = ProjectFileTools(root, testConfig())

        val result = tools.patchProjectFile(
            path = "docs/a.md",
            patch = """
                @@ -1,2 +1,2 @@
                 one
                -two
                +three
            """.trimIndent(),
            apply = false
        )

        assertTrue(result.success)
        assertContains(result.diff, "+three")
        assertEquals("one\ntwo", File(root, "docs/a.md").readText())
    }

    private fun tempProject(): File {
        val root = createTempDirectory("file-assistant-test").toFile()
        File(root, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        return root
    }

    private fun testConfig(
        maxReadChars: Int = 120000,
        maxWriteChars: Int = 120000,
        maxSearchResults: Int = 120
    ): FileAssistantConfig = FileAssistantConfig(
        maxReadChars = maxReadChars,
        maxWriteChars = maxWriteChars,
        maxSearchResults = maxSearchResults,
        maxDiffChars = 60000,
        maxListFiles = 5000,
        searchContextLines = 1
    )

    private fun File.writeWithParents(content: String) {
        parentFile?.mkdirs()
        writeText(content)
    }
}
