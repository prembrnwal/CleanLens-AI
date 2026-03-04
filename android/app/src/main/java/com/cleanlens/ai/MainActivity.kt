package com.cleanlens.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cleanlens.ai.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var classifier: SpamClassifier
    private var currentPhotoUri: Uri? = null

    // ── Gallery Picker ──
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = uriToBitmap(it)
            bitmap?.let { bmp ->
                showImageAndClassify(bmp, it)
            }
        }
    }

    // ── Camera Capture ──
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            currentPhotoUri?.let { uri ->
                val bitmap = uriToBitmap(uri)
                bitmap?.let { bmp ->
                    showImageAndClassify(bmp, uri)
                }
            }
        }
    }

    // ── Camera Permission ──
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Storage Permission for Gallery Scan ──
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        if (granted) {
            showFolderPicker()
        } else {
            Toast.makeText(this, "Storage permission is required to scan gallery", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize classifier
        classifier = SpamClassifier(this)

        setupUI()
    }

    private fun setupUI() {
        // Initial state - show welcome, hide result
        binding.resultCard.visibility = View.GONE
        binding.imageCard.visibility = View.GONE

        // Gallery button (single image)
        binding.btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        // Camera button
        binding.btnCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        // Scan Gallery button (full scan)
        binding.btnScanGallery.setOnClickListener {
            requestStorageAndScan()
        }

        // Recently Deleted button
        binding.btnRecentlyDeleted.setOnClickListener {
            showRecentlyDeleted()
        }

        // Scan another button
        binding.btnScanAnother.setOnClickListener {
            resetUI()
        }
    }

    /**
     * Show the Recently Deleted (trash) dialog.
     * Users can restore or permanently delete trashed images.
     */
    private fun showRecentlyDeleted() {
        val trashManager = TrashManager(this)
        val trashedImages = trashManager.getTrashedImages()

        if (trashedImages.isEmpty()) {
            Toast.makeText(this, "🗑 Trash is empty", Toast.LENGTH_SHORT).show()
            return
        }

        // Build list items
        val items = trashedImages.map { image ->
            "📷 ${image.originalName}\n   ${image.sizeFormatted} • ${image.deletedDateFormatted} • ${image.daysRemaining} days left"
        }.toTypedArray()

        val trashSize = trashManager.getTrashSize()
        val trashMb = trashSize / (1024.0 * 1024.0)
        val title = "🗑 Recently Deleted (${trashedImages.size} items, ${String.format("%.1f MB", trashMb)})"

        AlertDialog.Builder(this, com.google.android.material.R.style.MaterialAlertDialog_Material3)
            .setTitle(title)
            .setItems(items) { _, which ->
                val selected = trashedImages[which]
                // Show restore/delete options for selected item
                AlertDialog.Builder(this, com.google.android.material.R.style.MaterialAlertDialog_Material3)
                    .setTitle(selected.originalName)
                    .setMessage("${selected.sizeFormatted} • Deleted ${selected.deletedDateFormatted}\n${selected.daysRemaining} days until permanent deletion")
                    .setPositiveButton("↩ Restore") { _, _ ->
                        val restored = trashManager.restoreFromTrash(selected)
                        if (restored) {
                            Toast.makeText(this, "✅ Restored to Pictures/Restored", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "❌ Failed to restore", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("🗑 Delete Forever") { _, _ ->
                        trashManager.permanentlyDelete(selected)
                        Toast.makeText(this, "Permanently deleted", Toast.LENGTH_SHORT).show()
                    }
                    .setNeutralButton("Cancel", null)
                    .show()
            }
            .setPositiveButton("🗑 Empty Trash") { _, _ ->
                AlertDialog.Builder(this, com.google.android.material.R.style.MaterialAlertDialog_Material3)
                    .setTitle("Empty trash?")
                    .setMessage("This will permanently delete all ${trashedImages.size} items. This cannot be undone.")
                    .setPositiveButton("Delete All") { _, _ ->
                        val count = trashManager.emptyTrash()
                        Toast.makeText(this, "🗑 Permanently deleted $count items", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    /**
     * Request storage permission, then show folder picker.
     */
    private fun requestStorageAndScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission)
            == PackageManager.PERMISSION_GRANTED
        ) {
            showFolderPicker()
        } else {
            storagePermissionLauncher.launch(permission)
        }
    }

    /**
     * Show a dialog listing all image folders on the device.
     * User picks which folder to scan for spam.
     */
    private fun showFolderPicker() {
        val scanner = GalleryScanner(this)

        // Show loading toast
        Toast.makeText(this, "Loading folders...", Toast.LENGTH_SHORT).show()

        Thread {
            val folders = scanner.getImageFolders()

            runOnUiThread {
                if (folders.isEmpty()) {
                    Toast.makeText(this, "No image folders found", Toast.LENGTH_SHORT).show()
                    return@runOnUiThread
                }

                // Build folder list with image counts
                val folderNames = folders.map { folder ->
                    "📁 ${folder.name}  (${folder.imageCount} images, ${folder.sizeFormatted})"
                }.toTypedArray()

                AlertDialog.Builder(this, com.google.android.material.R.style.MaterialAlertDialog_Material3)
                    .setTitle("📂 Select folder to scan")
                    .setItems(folderNames) { _, which ->
                        val selectedFolder = folders[which]
                        launchScanActivity(selectedFolder.path, selectedFolder.name)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }.start()
    }

    private fun launchScanActivity(folderPath: String, folderName: String) {
        val intent = Intent(this, ScanActivity::class.java).apply {
            putExtra(ScanActivity.EXTRA_FOLDER_PATH, folderPath)
            putExtra(ScanActivity.EXTRA_FOLDER_NAME, folderName)
        }
        startActivity(intent)
    }

    private fun launchCamera() {
        val photoFile = File(cacheDir, "captured_photo_${System.currentTimeMillis()}.jpg")
        currentPhotoUri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            photoFile
        )
        cameraLauncher.launch(currentPhotoUri)
    }

    private fun showImageAndClassify(bitmap: Bitmap, uri: Uri) {
        // Show image
        binding.imageCard.visibility = View.VISIBLE
        binding.selectedImage.setImageBitmap(bitmap)

        // Animate image card in
        val slideUp = AnimationUtils.loadAnimation(this, android.R.anim.fade_in)
        binding.imageCard.startAnimation(slideUp)

        // Show loading
        binding.progressBar.visibility = View.VISIBLE
        binding.resultCard.visibility = View.GONE

        // Hide welcome section
        binding.welcomeSection.visibility = View.GONE

        // Classify
        binding.root.postDelayed({
            val result = classifier.classify(bitmap)
            showResult(result)
        }, 500) // Small delay for visual feedback
    }

    private fun showResult(result: SpamClassifier.ClassificationResult) {
        binding.progressBar.visibility = View.GONE
        binding.resultCard.visibility = View.VISIBLE

        // Animate result card
        val slideUp = AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left)
        binding.resultCard.startAnimation(slideUp)

        if (result.isSpam) {
            // SPAM
            binding.resultIcon.text = "🚫"
            binding.resultLabel.text = "SPAM DETECTED"
            binding.resultLabel.setTextColor(ContextCompat.getColor(this, R.color.spam_red))
            binding.resultCard.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.spam_background)
            )
            binding.confidenceBar.setIndicatorColor(
                ContextCompat.getColor(this, R.color.spam_red)
            )
        } else {
            // NORMAL
            binding.resultIcon.text = "✅"
            binding.resultLabel.text = "CLEAN IMAGE"
            binding.resultLabel.setTextColor(ContextCompat.getColor(this, R.color.normal_green))
            binding.resultCard.setCardBackgroundColor(
                ContextCompat.getColor(this, R.color.normal_background)
            )
            binding.confidenceBar.setIndicatorColor(
                ContextCompat.getColor(this, R.color.normal_green)
            )
        }

        // Confidence
        binding.confidenceText.text = String.format("%.1f%% Confidence", result.confidence)
        binding.confidenceBar.progress = result.confidence.toInt()

        // Probabilities
        val normalProb = result.probabilities["Normal"] ?: 0f
        val spamProb = result.probabilities["Spam"] ?: 0f
        binding.probNormal.text = String.format("Normal: %.1f%%", normalProb)
        binding.probSpam.text = String.format("Spam: %.1f%%", spamProb)
    }

    private fun resetUI() {
        binding.resultCard.visibility = View.GONE
        binding.imageCard.visibility = View.GONE
        binding.welcomeSection.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
    }

    @Suppress("DEPRECATION")
    private fun uriToBitmap(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.setTargetSampleSize(1)
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                MediaStore.Images.Media.getBitmap(contentResolver, uri)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        classifier.close()
    }
}
