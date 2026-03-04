package com.cleanlens.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.InputStream
import java.security.MessageDigest

/**
 * Detects duplicate images using two methods:
 *  1. MD5 hash — exact byte-level duplicates
 *  2. Perceptual hash (pHash) — visually similar images
 *     (cropped, compressed, brightness-changed)
 */
class DuplicateDetector(private val context: Context) {

    /**
     * Generate MD5 hash for exact duplicate detection.
     * Groups images with identical hashes together.
     *
     * @param images List of scanned images to check
     * @return Map of MD5 hash → list of images with that hash
     */
    fun findExactDuplicates(images: List<ScannedImage>): Map<String, List<ScannedImage>> {
        val hashMap = mutableMapOf<String, MutableList<ScannedImage>>()

        for (image in images) {
            try {
                val hash = computeMd5(image)
                if (hash.isNotEmpty()) {
                    image.duplicateGroupId = hash
                    hashMap.getOrPut(hash) { mutableListOf() }.add(image)
                }
            } catch (e: Exception) {
                // Skip images we can't read
                e.printStackTrace()
            }
        }

        // Only return groups with 2+ images (actual duplicates)
        return hashMap.filter { it.value.size > 1 }
    }

    /**
     * Generate a perceptual hash for near-duplicate detection.
     * Works by:
     *  1. Resize image to 8x8 grayscale
     *  2. Compute average pixel value
     *  3. Generate 64-bit hash (1 if pixel > avg, 0 otherwise)
     *
     * Images with hamming distance < 10 are considered similar.
     */
    fun computePerceptualHash(bitmap: Bitmap): Long {
        // Resize to 8x8
        val small = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
        val pixels = IntArray(64)
        small.getPixels(pixels, 0, 8, 0, 0, 8, 8)

        // Convert to grayscale values
        val grayValues = pixels.map { pixel ->
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            (0.299 * r + 0.587 * g + 0.114 * b)
        }

        // Compute average
        val avg = grayValues.average()

        // Generate hash
        var hash = 0L
        for (i in grayValues.indices) {
            if (grayValues[i] >= avg) {
                hash = hash or (1L shl i)
            }
        }

        return hash
    }

    /**
     * Compare two perceptual hashes.
     * Returns the hamming distance (number of different bits).
     * Distance < 10 means images are visually similar.
     */
    fun hammingDistance(hash1: Long, hash2: Long): Int {
        var xor = hash1 xor hash2
        var distance = 0
        while (xor != 0L) {
            distance += (xor and 1L).toInt()
            xor = xor shr 1
        }
        return distance
    }

    /**
     * Find near-duplicates using perceptual hashing.
     * Groups images that are visually similar even if not byte-identical.
     */
    fun findNearDuplicates(
        images: List<ScannedImage>,
        threshold: Int = 10
    ): Map<String, List<ScannedImage>> {
        val hashes = mutableListOf<Pair<ScannedImage, Long>>()

        for (image in images) {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(image.uri)
                inputStream?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        val hash = computePerceptualHash(bitmap)
                        hashes.add(image to hash)
                        bitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Group similar images
        val visited = BooleanArray(hashes.size)
        val groups = mutableMapOf<String, MutableList<ScannedImage>>()
        var groupId = 0

        for (i in hashes.indices) {
            if (visited[i]) continue
            val group = mutableListOf(hashes[i].first)
            visited[i] = true

            for (j in i + 1 until hashes.size) {
                if (visited[j]) continue
                if (hammingDistance(hashes[i].second, hashes[j].second) < threshold) {
                    group.add(hashes[j].first)
                    visited[j] = true
                }
            }

            if (group.size > 1) {
                val key = "pHash_group_$groupId"
                groups[key] = group
                group.forEach { it.isDuplicate = true; it.duplicateGroupId = key }
                groupId++
            }
        }

        return groups
    }

    /**
     * Compute MD5 hash of an image file's raw bytes.
     */
    private fun computeMd5(image: ScannedImage): String {
        val inputStream: InputStream? = context.contentResolver.openInputStream(image.uri)
        return inputStream?.use { stream ->
            val digest = MessageDigest.getInstance("MD5")
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }

            digest.digest().joinToString("") { "%02x".format(it) }
        } ?: ""
    }
}
