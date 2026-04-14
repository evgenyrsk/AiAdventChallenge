package demo.corpus

class SampleRetriever(
    private val chunkRepository: ChunkRepository,
    private val embeddingProvider: EmbeddingProvider
) {
    fun search(query: String, topK: Int): List<RetrievedChunk> {
        val queryEmbedding = embeddingProvider.embed(query)
        return chunkRepository
            .loadAll()
            .map { chunk ->
                val score = cosine(queryEmbedding, chunk.embedding)
                RetrievedChunk(
                    chunkId = chunk.chunkId,
                    title = chunk.title,
                    section = chunk.section,
                    score = score,
                    text = chunk.text
                )
            }
            .sortedByDescending { it.score }
            .take(topK)
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0

        for (index in a.indices) {
            val left = a[index].toDouble()
            val right = b[index].toDouble()
            dot += left * right
            leftNorm += left * left
            rightNorm += right * right
        }

        return if (leftNorm == 0.0 || rightNorm == 0.0) {
            0.0
        } else {
            dot / (kotlin.math.sqrt(leftNorm) * kotlin.math.sqrt(rightNorm))
        }
    }
}

interface ChunkRepository {
    fun loadAll(): List<StoredChunk>
}

interface EmbeddingProvider {
    fun embed(text: String): FloatArray
}

data class StoredChunk(
    val chunkId: String,
    val title: String,
    val section: String,
    val text: String,
    val embedding: FloatArray
)

data class RetrievedChunk(
    val chunkId: String,
    val title: String,
    val section: String,
    val score: Double,
    val text: String
)
