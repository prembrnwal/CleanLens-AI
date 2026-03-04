package com.cleanlens.ai

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/**
 * Scans the device gallery using MediaStore API.
 * Returns a list of all images with metadata (path, size, date).
 */
class GalleryScanner(private val context: Context) {

    /**
     * Query all images from the device storage.
     * @return List of ScannedImage with metadata populated.
     */
    fun getAllImages(): List<ScannedImage> {
        val images = mutableListOf<ScannedImage>()

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED,
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val cursor = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
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
}
