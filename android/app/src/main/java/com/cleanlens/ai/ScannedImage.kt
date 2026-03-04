package com.cleanlens.ai

import android.net.Uri

/**
 * Represents a single image found during gallery scanning.
 * Contains the image metadata and all analysis results.
 */
data class ScannedImage(
    val uri: Uri,
    val path: String,
    val name: String,
    val size: Long,              // bytes
    val dateAdded: Long,         // timestamp in seconds

    // Analysis results
    var spamProbability: Float = 0f,   // CNN model output (0.0 - 1.0)
    var ocrText: String = "",          // Extracted text via OCR
    var ocrKeywordFound: Boolean = false,
    var isDuplicate: Boolean = false,
    var duplicateGroupId: String = "",  // MD5 hash for grouping
    var spamScore: Float = 0f,         // Final combined score (0.0 - 1.0)
    var isSelected: Boolean = false,   // UI selection state
) {
    /**
     * Calculate the combined spam score from all signals.
     *
     * Scoring:
     *   CNN spam > 0.7  → +0.5
     *   OCR keyword hit  → +0.3
     *   Is duplicate     → +0.3
     *   Older than 6mo   → +0.2
     *
     * Final score is clamped to [0, 1].
     */
    fun calculateSpamScore(): Float {
        var score = 0f

        // CNN spam probability
        if (spamProbability > 0.7f) score += 0.5f
        else if (spamProbability > 0.4f) score += spamProbability * 0.5f

        // OCR keyword detection
        if (ocrKeywordFound) score += 0.3f

        // Duplicate detection
        if (isDuplicate) score += 0.3f

        // Old image bonus (older than 6 months)
        val sixMonthsAgo = (System.currentTimeMillis() / 1000) - (180L * 24 * 3600)
        if (dateAdded < sixMonthsAgo) score += 0.2f

        spamScore = score.coerceIn(0f, 1f)
        return spamScore
    }

    val sizeFormatted: String
        get() {
            val kb = size / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) String.format("%.1f MB", mb)
            else String.format("%.0f KB", kb)
        }

    val isLikelySpam: Boolean
        get() = spamScore >= 0.5f
}
