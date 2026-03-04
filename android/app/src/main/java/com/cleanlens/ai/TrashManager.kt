package com.cleanlens.ai

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Manages a "Recently Deleted" trash folder.
 * Instead of permanently deleting images, they are moved to a trash folder.
 * Images in trash are auto-purged after 30 days.
 *
 * Storage: App's internal files directory → "trash/"
 * Metadata: "trash/trash_metadata.json"
 */
class TrashManager(private val context: Context) {

    companion object {
        private const val TRASH_DIR = "trash"
        private const val METADATA_FILE = "trash_metadata.json"
        private const val AUTO_DELETE_DAYS = 30
    }

    private val trashDir: File
        get() {
            val dir = File(context.filesDir, TRASH_DIR)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val metadataFile: File
        get() = File(trashDir, METADATA_FILE)

    /**
     * Represents a trashed image with metadata for restore.
     */
    data class TrashedImage(
        val trashFileName: String,    // Name in trash folder
        val originalName: String,     // Original display name
        val originalSize: Long,       // Size in bytes
        val deletedTimestamp: Long,    // When it was trashed (millis)
        val daysRemaining: Int,       // Days before permanent deletion
    ) {
        val sizeFormatted: String
            get() {
                val mb = originalSize / (1024.0 * 1024.0)
                return if (mb >= 1.0) String.format("%.1f MB", mb)
                else String.format("%.0f KB", originalSize / 1024.0)
            }

        val deletedDateFormatted: String
            get() {
                val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                return sdf.format(java.util.Date(deletedTimestamp))
            }
    }

    /**
     * Move an image to the trash folder instead of permanently deleting it.
     *
     * @param uri The content URI of the image to trash.
     * @param originalName The display name of the image.
     * @param originalSize The size in bytes.
     * @return true if successfully trashed.
     */
    fun moveToTrash(uri: Uri, originalName: String, originalSize: Long): Boolean {
        return try {
            // Generate unique trash filename
            val timestamp = System.currentTimeMillis()
            val trashFileName = "${timestamp}_${originalName}"
            val trashFile = File(trashDir, trashFileName)

            // Copy image bytes to trash folder
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(trashFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Save metadata
            val metadata = loadMetadata()
            val entry = JSONObject().apply {
                put("trashFileName", trashFileName)
                put("originalName", originalName)
                put("originalSize", originalSize)
                put("deletedTimestamp", timestamp)
            }
            metadata.put(entry)
            saveMetadata(metadata)

            // Now delete from MediaStore (actual removal from gallery)
            context.contentResolver.delete(uri, null, null)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Get all images currently in the trash.
     */
    fun getTrashedImages(): List<TrashedImage> {
        val metadata = loadMetadata()
        val images = mutableListOf<TrashedImage>()
        val now = System.currentTimeMillis()

        for (i in 0 until metadata.length()) {
            val entry = metadata.getJSONObject(i)
            val deletedTime = entry.getLong("deletedTimestamp")
            val daysSinceDeleted = ((now - deletedTime) / (1000 * 60 * 60 * 24)).toInt()
            val daysRemaining = AUTO_DELETE_DAYS - daysSinceDeleted

            // Check if file still exists in trash
            val trashFile = File(trashDir, entry.getString("trashFileName"))
            if (trashFile.exists() && daysRemaining > 0) {
                images.add(
                    TrashedImage(
                        trashFileName = entry.getString("trashFileName"),
                        originalName = entry.getString("originalName"),
                        originalSize = entry.getLong("originalSize"),
                        deletedTimestamp = deletedTime,
                        daysRemaining = daysRemaining,
                    )
                )
            }
        }

        return images.sortedByDescending { it.deletedTimestamp }
    }

    /**
     * Restore a trashed image back to the device gallery.
     * Uses MediaStore to insert it back.
     *
     * @return true if successfully restored.
     */
    fun restoreFromTrash(trashedImage: TrashedImage): Boolean {
        return try {
            val trashFile = File(trashDir, trashedImage.trashFileName)
            if (!trashFile.exists()) return false

            // Insert back into MediaStore
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, trashedImage.originalName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Restored")
            }

            val insertUri = context.contentResolver.insert(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )

            insertUri?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    trashFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }

            // Remove from trash
            trashFile.delete()
            removeFromMetadata(trashedImage.trashFileName)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Permanently delete a trashed image.
     */
    fun permanentlyDelete(trashedImage: TrashedImage): Boolean {
        return try {
            val trashFile = File(trashDir, trashedImage.trashFileName)
            val deleted = trashFile.delete()
            removeFromMetadata(trashedImage.trashFileName)
            deleted
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Permanently delete all trashed images.
     */
    fun emptyTrash(): Int {
        val images = getTrashedImages()
        var count = 0
        for (image in images) {
            if (permanentlyDelete(image)) count++
        }
        return count
    }

    /**
     * Auto-purge images that have been in trash for more than 30 days.
     * Call this on app startup.
     */
    fun autoPurgeExpired(): Int {
        val metadata = loadMetadata()
        val now = System.currentTimeMillis()
        val expiredThreshold = AUTO_DELETE_DAYS * 24 * 60 * 60 * 1000L
        var purgedCount = 0

        val toKeep = JSONArray()
        for (i in 0 until metadata.length()) {
            val entry = metadata.getJSONObject(i)
            val deletedTime = entry.getLong("deletedTimestamp")

            if (now - deletedTime > expiredThreshold) {
                // Expired — permanently delete file
                val trashFile = File(trashDir, entry.getString("trashFileName"))
                trashFile.delete()
                purgedCount++
            } else {
                toKeep.put(entry)
            }
        }

        saveMetadata(toKeep)
        return purgedCount
    }

    /**
     * Get total size of trash in bytes.
     */
    fun getTrashSize(): Long {
        return getTrashedImages().sumOf { it.originalSize }
    }

    /**
     * Get trash item count.
     */
    fun getTrashCount(): Int {
        return getTrashedImages().size
    }

    // ── Private helpers ──

    private fun loadMetadata(): JSONArray {
        return try {
            if (metadataFile.exists()) {
                JSONArray(metadataFile.readText())
            } else {
                JSONArray()
            }
        } catch (e: Exception) {
            JSONArray()
        }
    }

    private fun saveMetadata(metadata: JSONArray) {
        metadataFile.writeText(metadata.toString())
    }

    private fun removeFromMetadata(trashFileName: String) {
        val metadata = loadMetadata()
        val updated = JSONArray()
        for (i in 0 until metadata.length()) {
            val entry = metadata.getJSONObject(i)
            if (entry.getString("trashFileName") != trashFileName) {
                updated.put(entry)
            }
        }
        saveMetadata(updated)
    }
}
