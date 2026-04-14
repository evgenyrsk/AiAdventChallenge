package com.example.mcp.server.documentindex.embedding

import com.example.mcp.server.documentindex.model.EmbeddingVector
import kotlin.math.sqrt

class HashingEmbeddingProvider(
    override val dimensions: Int = 256
) : EmbeddingProvider {

    override val providerId: String = "local_hashing_v1"

    override fun embed(text: String): EmbeddingVector {
        val vector = FloatArray(dimensions)
        tokenize(text).forEach { token ->
            val index = positiveModulo(token.hashCode(), dimensions)
            vector[index] += 1f
        }

        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) {
            vector.indices.forEach { i -> vector[i] = vector[i] / norm }
        }

        return EmbeddingVector(
            providerId = providerId,
            dimensions = dimensions,
            values = vector.toList()
        )
    }

    private fun tokenize(text: String): List<String> = text
        .lowercase()
        .split(Regex("[^\\p{L}\\p{N}_]+"))
        .filter { it.length >= 2 }

    private fun positiveModulo(value: Int, mod: Int): Int {
        val remainder = value % mod
        return if (remainder >= 0) remainder else remainder + mod
    }
}
