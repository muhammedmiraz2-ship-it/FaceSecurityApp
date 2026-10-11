package com.example.facesecurityapp

import kotlin.math.sqrt

object FaceMatcher {

    fun cosineSimilarity(
        embedding1: FloatArray,
        embedding2: FloatArray
    ): Float {

        if (embedding1.size != embedding2.size) {
            return 0f
        }

        var dotProduct = 0f
        var magnitude1 = 0f
        var magnitude2 = 0f

        for (i in embedding1.indices) {
            dotProduct += embedding1[i] * embedding2[i]
            magnitude1 += embedding1[i] * embedding1[i]
            magnitude2 += embedding2[i] * embedding2[i]
        }

        val denominator =
            sqrt(magnitude1) * sqrt(magnitude2)

        if (denominator == 0f) {
            return 0f
        }

        return dotProduct / denominator
    }

    fun isMatch(
        embedding1: FloatArray,
        embedding2: FloatArray,
        threshold: Float = 0.70f
    ): Boolean {

        val similarity =
            cosineSimilarity(embedding1, embedding2)

        return similarity >= threshold
    }
}