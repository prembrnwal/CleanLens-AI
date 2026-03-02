package com.cleanlens.ai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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

        // Gallery button
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

        // Scan another button
        binding.btnScanAnother.setOnClickListener {
            resetUI()
        }
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
