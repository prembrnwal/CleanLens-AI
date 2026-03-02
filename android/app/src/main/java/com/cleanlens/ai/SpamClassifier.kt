package com.cleanlens.ai

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * TFLite-based image classifier for spam detection.
 * Classifies images as "Normal" or "Spam".
 */
class SpamClassifier(private val context: Context) {

    private var interpreter: Interpreter? = null

    companion object {
        private const val MODEL_FILE = "spam_model.tflite"
        private const val IMG_SIZE = 224
        private const val NUM_CLASSES = 2
        private val CLASS_NAMES = arrayOf("Normal", "Spam")
    }

    data class ClassificationResult(
        val label: String,
        val confidence: Float,
        val isSpam: Boolean,
        val probabilities: Map<String, Float>
    )

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val modelBuffer = loadModelFile()
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Classify a bitmap image.
     * @param bitmap The image to classify
     * @return ClassificationResult with label, confidence, and probabilities
     */
    fun classify(bitmap: Bitmap): ClassificationResult {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, IMG_SIZE, IMG_SIZE, true)
        val inputBuffer = convertBitmapToByteBuffer(resizedBitmap)

        // Output array: [1][NUM_CLASSES]
        val output = Array(1) { FloatArray(NUM_CLASSES) }

        interpreter?.run(inputBuffer, output)

        val probabilities = output[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val confidence = probabilities[maxIndex]
        val label = CLASS_NAMES[maxIndex]

        val probabilityMap = mutableMapOf<String, Float>()
        for (i in CLASS_NAMES.indices) {
            probabilityMap[CLASS_NAMES[i]] = probabilities[i] * 100f
        }

        return ClassificationResult(
            label = label,
            confidence = confidence * 100f,
            isSpam = maxIndex == 1,
            probabilities = probabilityMap
        )
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4 * IMG_SIZE * IMG_SIZE * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(IMG_SIZE * IMG_SIZE)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            // Extract RGB and normalize to [0, 1]
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }

        return byteBuffer
    }

    fun close() {
        interpreter?.close()
    }
}
