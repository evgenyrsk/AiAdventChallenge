package com.example.mcp.server.documentindex.index

import com.example.mcp.server.documentindex.model.IndexStats
import com.example.mcp.server.documentindex.model.IndexedChunk
import com.example.mcp.server.documentindex.model.IndexedDocumentSummary
import com.example.mcp.server.documentindex.model.MetadataCoverage
import com.example.mcp.server.documentindex.model.ChunkMetadata
import com.example.mcp.server.documentindex.model.StoredIndexedChunk
import com.example.mcp.server.documentindex.model.StrategyIndexingSummary
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet

class SQLiteVectorIndexStorage(
    private val databasePath: String,
    private val json: Json = Json { encodeDefaults = true }
) : VectorIndexStorage {

    override fun initialize() {
        withConnection { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS indexed_chunks (
                        chunk_id TEXT PRIMARY KEY,
                        document_id TEXT NOT NULL,
                        source TEXT NOT NULL,
                        title TEXT NOT NULL,
                        file_path TEXT NOT NULL,
                        relative_path TEXT NOT NULL,
                        section TEXT NOT NULL,
                        chunking_strategy TEXT NOT NULL,
                        document_type TEXT NOT NULL,
                        position_start INTEGER NOT NULL,
                        position_end INTEGER NOT NULL,
                        page_number INTEGER,
                        text_content TEXT NOT NULL,
                        embedding_provider TEXT NOT NULL,
                        embedding_dimensions INTEGER NOT NULL,
                        embedding_json TEXT NOT NULL,
                        metadata_json TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS strategy_summaries (
                        source TEXT NOT NULL,
                        strategy TEXT NOT NULL,
                        document_count INTEGER NOT NULL,
                        chunk_count INTEGER NOT NULL,
                        average_chunk_length REAL NOT NULL,
                        min_chunk_length INTEGER NOT NULL,
                        max_chunk_length INTEGER NOT NULL,
                        metadata_coverage_json TEXT NOT NULL,
                        index_size_bytes INTEGER NOT NULL,
                        notes_json TEXT NOT NULL,
                        PRIMARY KEY (source, strategy)
                    )
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE INDEX IF NOT EXISTS idx_chunks_source_strategy
                    ON indexed_chunks(source, chunking_strategy)
                    """.trimIndent()
                )
                statement.executeUpdate(
                    """
                    CREATE INDEX IF NOT EXISTS idx_chunks_document
                    ON indexed_chunks(source, document_id)
                    """.trimIndent()
                )
            }
        }
    }

    override fun replaceStrategyIndex(
        source: String,
        strategy: String,
        chunks: List<IndexedChunk>,
        summary: StrategyIndexingSummary
    ) {
        withConnection { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    "DELETE FROM indexed_chunks WHERE source = ? AND chunking_strategy = ?"
                ).use { statement ->
                    statement.setString(1, source)
                    statement.setString(2, strategy)
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    "DELETE FROM strategy_summaries WHERE source = ? AND strategy = ?"
                ).use { statement ->
                    statement.setString(1, source)
                    statement.setString(2, strategy)
                    statement.executeUpdate()
                }

                connection.prepareStatement(
                    """
                    INSERT INTO indexed_chunks (
                        chunk_id, document_id, source, title, file_path, relative_path, section,
                        chunking_strategy, document_type, position_start, position_end, page_number,
                        text_content, embedding_provider, embedding_dimensions, embedding_json, metadata_json
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    chunks.forEach { indexedChunk ->
                        val metadata = indexedChunk.chunk.metadata
                        statement.setString(1, indexedChunk.chunk.chunkId)
                        statement.setString(2, indexedChunk.chunk.documentId)
                        statement.setString(3, metadata.source)
                        statement.setString(4, metadata.title)
                        statement.setString(5, metadata.filePath)
                        statement.setString(6, metadata.relativePath)
                        statement.setString(7, metadata.section)
                        statement.setString(8, metadata.chunkingStrategy)
                        statement.setString(9, metadata.documentType)
                        statement.setInt(10, metadata.positionStart)
                        statement.setInt(11, metadata.positionEnd)
                        statement.setObject(12, metadata.pageNumber)
                        statement.setString(13, indexedChunk.chunk.text)
                        statement.setString(14, indexedChunk.embedding.providerId)
                        statement.setInt(15, indexedChunk.embedding.dimensions)
                        statement.setString(16, json.encodeToString(indexedChunk.embedding.values))
                        statement.setString(17, json.encodeToString(metadata))
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }

                connection.prepareStatement(
                    """
                    INSERT INTO strategy_summaries (
                        source, strategy, document_count, chunk_count, average_chunk_length,
                        min_chunk_length, max_chunk_length, metadata_coverage_json, index_size_bytes, notes_json
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { statement ->
                    statement.setString(1, source)
                    statement.setString(2, strategy)
                    statement.setInt(3, summary.documentCount)
                    statement.setInt(4, summary.chunkCount)
                    statement.setDouble(5, summary.averageChunkLength)
                    statement.setInt(6, summary.minChunkLength)
                    statement.setInt(7, summary.maxChunkLength)
                    statement.setString(8, json.encodeToString(summary.metadataCoverage))
                    statement.setLong(9, summary.indexSizeBytes)
                    statement.setString(10, json.encodeToString(summary.notes))
                    statement.executeUpdate()
                }

                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }
    }

    override fun getStats(source: String): IndexStats {
        initialize()

        val documentCount = queryInt(
            "SELECT COUNT(DISTINCT document_id) FROM indexed_chunks WHERE source = ?",
            source
        )
        val chunkCount = queryInt(
            "SELECT COUNT(*) FROM indexed_chunks WHERE source = ?",
            source
        )
        val strategies = mutableListOf<String>()
        var provider = ""
        var dimensions = 0

        withConnection { connection ->
            connection.prepareStatement(
                "SELECT DISTINCT chunking_strategy FROM indexed_chunks WHERE source = ? ORDER BY chunking_strategy"
            ).use { statement ->
                statement.setString(1, source)
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        strategies += resultSet.getString(1)
                    }
                }
            }

            connection.prepareStatement(
                "SELECT embedding_provider, embedding_dimensions FROM indexed_chunks WHERE source = ? LIMIT 1"
            ).use { statement ->
                statement.setString(1, source)
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) {
                        provider = resultSet.getString("embedding_provider")
                        dimensions = resultSet.getInt("embedding_dimensions")
                    }
                }
            }
        }

        val file = File(databasePath)
        return IndexStats(
            source = source,
            documentCount = documentCount,
            chunkCount = chunkCount,
            strategies = strategies,
            embeddingsProvider = provider,
            dimensions = dimensions,
            databasePath = file.absolutePath,
            databaseSizeBytes = if (file.exists()) file.length() else 0L
        )
    }

    override fun getStrategySummary(source: String, strategy: String): StrategyIndexingSummary? {
        initialize()

        return withConnection { connection ->
            connection.prepareStatement(
                "SELECT * FROM strategy_summaries WHERE source = ? AND strategy = ?"
            ).use { statement ->
                statement.setString(1, source)
                statement.setString(2, strategy)
                statement.executeQuery().use { resultSet ->
                    if (!resultSet.next()) return@withConnection null
                    mapStrategySummary(resultSet, strategy)
                }
            }
        }
    }

    override fun listIndexedDocuments(source: String): List<IndexedDocumentSummary> {
        initialize()

        return withConnection { connection ->
            connection.prepareStatement(
                """
                SELECT
                    document_id,
                    title,
                    relative_path,
                    document_type,
                    COUNT(*) AS chunk_count,
                    GROUP_CONCAT(DISTINCT chunking_strategy) AS strategies
                FROM indexed_chunks
                WHERE source = ?
                GROUP BY document_id, title, relative_path, document_type
                ORDER BY title
                """.trimIndent()
            ).use { statement ->
                statement.setString(1, source)
                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            add(
                                IndexedDocumentSummary(
                                    documentId = resultSet.getString("document_id"),
                                    title = resultSet.getString("title"),
                                    relativePath = resultSet.getString("relative_path"),
                                    documentType = resultSet.getString("document_type"),
                                    chunkCount = resultSet.getInt("chunk_count"),
                                    strategies = resultSet.getString("strategies")
                                        ?.split(",")
                                        ?.filter { it.isNotBlank() }
                                        ?: emptyList()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override fun loadChunks(
        source: String,
        strategy: String?,
        documentType: String?,
        relativePathContains: String?
    ): List<StoredIndexedChunk> {
        initialize()

        val sql = buildString {
            append(
                """
                SELECT *
                FROM indexed_chunks
                WHERE source = ?
                """.trimIndent()
            )
            if (strategy != null) append(" AND chunking_strategy = ?")
            if (documentType != null) append(" AND document_type = ?")
            if (relativePathContains != null) append(" AND relative_path LIKE ?")
            append(" ORDER BY title, position_start")
        }

        return withConnection { connection ->
            connection.prepareStatement(sql).use { statement ->
                var index = 1
                statement.setString(index++, source)
                strategy?.let { statement.setString(index++, it) }
                documentType?.let { statement.setString(index++, it.lowercase()) }
                relativePathContains?.let { statement.setString(index++, "%$it%") }

                statement.executeQuery().use { resultSet ->
                    buildList {
                        while (resultSet.next()) {
                            val metadata = json.decodeFromString<ChunkMetadata>(
                                resultSet.getString("metadata_json")
                            )
                            val embeddingValues = json.decodeFromString<List<Float>>(
                                resultSet.getString("embedding_json")
                            )
                            add(
                                StoredIndexedChunk(
                                    chunkId = resultSet.getString("chunk_id"),
                                    documentId = resultSet.getString("document_id"),
                                    source = resultSet.getString("source"),
                                    title = resultSet.getString("title"),
                                    filePath = resultSet.getString("file_path"),
                                    relativePath = resultSet.getString("relative_path"),
                                    section = resultSet.getString("section"),
                                    chunkingStrategy = resultSet.getString("chunking_strategy"),
                                    documentType = resultSet.getString("document_type"),
                                    positionStart = resultSet.getInt("position_start"),
                                    positionEnd = resultSet.getInt("position_end"),
                                    pageNumber = resultSet.getObject("page_number") as? Int,
                                    text = resultSet.getString("text_content"),
                                    embeddingProvider = resultSet.getString("embedding_provider"),
                                    embeddingDimensions = resultSet.getInt("embedding_dimensions"),
                                    embeddingValues = embeddingValues,
                                    metadata = metadata
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun mapStrategySummary(resultSet: ResultSet, strategy: String): StrategyIndexingSummary {
        val coverage = json.decodeFromString<MetadataCoverage>(
            resultSet.getString("metadata_coverage_json")
        )
        val notes = json.decodeFromString<List<String>>(resultSet.getString("notes_json"))
        return StrategyIndexingSummary(
            strategy = strategy,
            documentCount = resultSet.getInt("document_count"),
            chunkCount = resultSet.getInt("chunk_count"),
            averageChunkLength = resultSet.getDouble("average_chunk_length"),
            minChunkLength = resultSet.getInt("min_chunk_length"),
            maxChunkLength = resultSet.getInt("max_chunk_length"),
            metadataCoverage = coverage,
            indexSizeBytes = resultSet.getLong("index_size_bytes"),
            notes = notes
        )
    }

    private fun queryInt(sql: String, source: String): Int = withConnection { connection ->
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, source)
            statement.executeQuery().use { resultSet ->
                if (resultSet.next()) resultSet.getInt(1) else 0
            }
        }
    }

    private fun <T> withConnection(block: (Connection) -> T): T {
        Class.forName("org.sqlite.JDBC")
        val file = File(databasePath)
        file.parentFile?.mkdirs()
        return DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use(block)
    }
}
