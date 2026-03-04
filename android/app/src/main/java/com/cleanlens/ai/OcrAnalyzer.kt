package com.cleanlens.ai

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Uses Google ML Kit to extract text from images
 * and detect spam keywords commonly found in forwarded images.
 */
class OcrAnalyzer(private val context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    companion object {
        // Common spam keywords found in forwarded images
        private val SPAM_KEYWORDS = listOf(
            // English greetings
            "good morning", "good night", "good evening", "good afternoon",
            "happy sunday", "happy monday", "happy tuesday", "happy wednesday",
            "happy thursday", "happy friday", "happy saturday",
            "have a nice day", "have a great day", "blessed day",
            "wonderful day", "beautiful day",

            // Festival / Religious
            "happy diwali", "happy holi", "happy christmas", "happy new year",
            "happy birthday", "happy anniversary", "happy eid",
            "jai shree ram", "jai mata di", "om namah shivaya",
            "happy navratri", "happy ganesh", "happy pongal",
            "happy independence", "happy republic", "happy raksha",

            // Forwarding cues
            "forward this", "share this", "send to", "forward to",
            "share with", "pass this", "spread this",
            "send this to", "forward this to",

            // Motivational spam
            "believe in yourself", "never give up", "stay positive",
            "life is beautiful", "think positive", "be happy",
            "inspirational", "motivational",

            // Hindi greetings (romanized)
            "suprabhat", "shubh prabhat", "shubh ratri",
            "shubh din", "namaste", "pranam",
        )
    }

    /**
     * Extract text from bitmap and check for spam keywords.
     * @return Pair(extractedText, hasSpamKeyword)
     */
    suspend fun analyzeImage(bitmap: Bitmap): OcrResult {
        val text = extractText(bitmap)
        val lowerText = text.lowercase()

        val foundKeywords = SPAM_KEYWORDS.filter { keyword ->
            lowerText.contains(keyword)
        }

        return OcrResult(
            extractedText = text,
            foundKeywords = foundKeywords,
            hasSpamKeyword = foundKeywords.isNotEmpty()
        )
    }

    /**
     * Extract text from bitmap using ML Kit OCR.
     */
    private suspend fun extractText(bitmap: Bitmap): String {
        return suspendCancellableCoroutine { continuation ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            recognizer.process(inputImage)
                .addOnSuccessListener { result ->
                    continuation.resume(result.text)
                }
                .addOnFailureListener {
                    continuation.resume("")
                }
        }
    }

    fun close() {
        recognizer.close()
    }

    data class OcrResult(
        val extractedText: String,
        val foundKeywords: List<String>,
        val hasSpamKeyword: Boolean,
    )
}
