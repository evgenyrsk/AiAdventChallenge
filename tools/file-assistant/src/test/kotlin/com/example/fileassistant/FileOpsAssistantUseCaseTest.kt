package com.example.fileassistant

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileOpsAssistantUseCaseTest {
    @Test
    fun `find usages returns files and line references`() {
        val root = tempProject()
        File(root, "app/src/main/java/LocalRepo.kt").writeWithParents(
            """
            class LocalRepo {
                val client = LocalOllamaClient()
            }
            """.trimIndent()
        )
        val assistant = assistant(root)

        val result = assistant.run(
            CliArgs(
                repoRoot = root,
                configFile = File(root, "missing.yaml"),
                command = "find-usages",
                commandArgs = listOf("LocalOllamaClient"),
                goal = null,
                apply = false,
                jsonOutput = false
            )
        )

        assertEquals(ResultStatus.SUCCESS, result.status)
        assertContains(result.summary, "LocalOllamaClient")
        assertTrue(result.filesRead.contains("app/src/main/java/LocalRepo.kt"))
        assertTrue(result.filesChanged.isEmpty())
    }

    @Test
    fun `generate adr dry run returns preview without writing file`() {
        val root = tempProject()
        File(root, "docs/PRIVATE_AI_SERVICE.md").writeWithParents("# Private AI Service\nGateway docs")
        File(root, "private-ai-service/src/main/kotlin/PrivateAiServiceMain.kt").writeWithParents("class PrivateAiServiceMain")
        val assistant = assistant(root)

        val result = assistant.run(
            CliArgs(
                repoRoot = root,
                configFile = File(root, "missing.yaml"),
                command = "generate-adr",
                commandArgs = listOf("private-ai-service"),
                goal = null,
                apply = false,
                jsonOutput = false
            )
        )

        assertEquals(ResultStatus.SUCCESS, result.status)
        assertContains(result.diff, "docs/adr/0001-private-ai-service.md")
        assertTrue(result.filesChanged.isEmpty())
        assertFalse(File(root, "docs/adr/0001-private-ai-service.md").exists())
    }

    private fun assistant(root: File): FileOpsAssistantUseCase {
        val config = FileAssistantConfig()
        return FileOpsAssistantUseCase(
            planner = FileAssistantPlanner(),
            tools = ProjectFileTools(root, config),
            config = config,
            runtime = RuntimeConfig("none", "", "", "")
        )
    }

    private fun tempProject(): File {
        val root = createTempDirectory("file-assistant-usecase-test").toFile()
        File(root, "settings.gradle.kts").writeText("rootProject.name = \"test\"")
        return root
    }

    private fun File.writeWithParents(content: String) {
        parentFile?.mkdirs()
        writeText(content)
    }
}
