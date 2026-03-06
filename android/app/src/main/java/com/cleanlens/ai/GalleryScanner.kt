package com.cleanlens.ai

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.io.File

/**
 * Scans the device gallery using MediaStore API.
 * Supports scanning all images or a specific folder.
 */
class GalleryScanner(private val context: Context) {

    /**
     * Represents an image folder on the device.
     */
    data class ImageFolder(
        val path: String,      // Full folder path
        val name: String,      // Folder display name (e.g. "WhatsApp Images")
        val imageCount: Int,   // Number of images in folder
        val totalSize: Long,   // Total size of images in bytes
    ) {
        val sizeFormatted: String
            get() {
                val mb = totalSize / (1024.0 * 1024.0)
                return if (mb >= 1024) String.format("%.1f GB", mb / 1024)
                else String.format("%.0f MB", mb)
            }
    }

    /**
     * Get all folders on the device that contain images.
     * @return List of ImageFolder sorted by image count (most images first).
     */
    fun getImageFolders(): List<ImageFolder> {
        val folderMap = mutableMapOf<String, MutableList<Long>>() // path → list of sizes

        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
        )

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null
        )

        cursor?.use {
            val pathCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (it.moveToNext()) {
                val filePath = it.getString(pathCol) ?: continue
                val size = it.getLong(sizeCol)

                if (size < 10_000) continue // Skip tiny files

                val folderPath = File(filePath).parent ?: continue
                folderMap.getOrPut(folderPath) { mutableListOf() }.add(size)
            }
        }

        return folderMap.map { (path, sizes) ->
            ImageFolder(
                path = path,
                name = File(path).name,
                imageCount = sizes.size,
                totalSize = sizes.sum(),
            )
        }.sortedByDescending { it.imageCount }
    }

    /**
     * Query images from a specific folder.
     * @param folderPath The full path of the folder to scan.
     * @return List of ScannedImage with metadata populated.
     */
    fun getImagesFromFolder(folderPath: String): List<ScannedImage> {
        val images = mutableListOf<ScannedImage>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
        )

        // Filter by folder path
        val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
        val selectionArgs = arrayOf("$folderPath/%")
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )

        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val pathCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val dateCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                val name = it.getString(nameCol) ?: "unknown"
                val path = it.getString(pathCol) ?: ""
                val size = it.getLong(sizeCol)
                val dateAdded = it.getLong(dateCol)

                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                )

                // Skip tiny images (icons, thumbnails < 10KB)
                if (size > 10_000) {
                    images.add(
                        ScannedImage(
                            uri = uri,
                            path = path,
                            name = name,
                            size = size,
                            dateAdded = dateAdded,
                        )
                    )
                }
            }
        }

        return images
    }

    /**
     * Query images from multiple folders at once.
     * @param folderPaths List of folder paths to scan.
     * @return Combined list of ScannedImage from all folders.
     */
    fun getImagesFromFolders(folderPaths: List<String>): List<ScannedImage> {
        val allImages = mutableListOf<ScannedImage>()
        for (path in folderPaths) {
            allImages.addAll(getImagesFromFolder(path))
        }
        return allImages
    }

    /**
     * Get combined media counts (Photos, Videos, Folders).
     */
    fun getMediaStats(): MediaStats {
        var photoCount = 0
        var videoCount = 0
        val folders = mutableSetOf<String>()

        try {
            // Count Photos
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media.DATA),
                null, null, null
            )?.use { cursor ->
                photoCount = cursor.count
                val pathCol = cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                if (pathCol != -1) {
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(pathCol) ?: continue
                        File(path).parent?.let { folders.add(it) }
                    }
                }
            }

            // Count Videos
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media.DATA),
                null, null, null
            )?.use { cursor ->
                videoCount = cursor.count
                val pathCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                if (pathCol != -1) {
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(pathCol) ?: continue
                        File(path).parent?.let { folders.add(it) }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return MediaStats(photoCount, videoCount, folders.size)
    }

    data class MediaStats(
        val totalPhotos: Int,
        val totalVideos: Int,
        val totalFolders: Int
    )
}
