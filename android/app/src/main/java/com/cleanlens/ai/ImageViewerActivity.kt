package com.cleanlens.ai

import android.animation.ObjectAnimator
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import coil.load
import com.cleanlens.ai.databinding.ActivityImageViewerBinding

/**
 * Full-screen image viewer with pinch-to-zoom and double-tap-to-zoom support.
 * Shows image metadata and allows toggling selection from this screen.
 */
class ImageViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URI   = "extra_image_uri"
        const val EXTRA_IMAGE_NAME  = "extra_image_name"
        const val EXTRA_IMAGE_SIZE  = "extra_image_size"
        const val EXTRA_SPAM_SCORE  = "extra_spam_score"
        const val EXTRA_IS_SELECTED = "extra_is_selected"
        const val EXTRA_HAS_OCR     = "extra_has_ocr"
        const val EXTRA_IS_DUPE     = "extra_is_dupe"
        const val RESULT_SELECTION_CHANGED = 100
        const val EXTRA_NEW_SELECTION_STATE = "extra_new_selection_state"
    }

    private lateinit var binding: ActivityImageViewerBinding
    private var isSelected = false

    // ── Pinch-to-zoom state ──
    private var scaleFactor = 1f
    private var translateX = 0f
    private var translateY = 0f
    private lateinit var scaleDetector: ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Make it full-screen (hide status bar)
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        val uriString   = intent.getStringExtra(EXTRA_IMAGE_URI) ?: run { finish(); return }
        val name        = intent.getStringExtra(EXTRA_IMAGE_NAME) ?: "Unknown"
        val size        = intent.getStringExtra(EXTRA_IMAGE_SIZE) ?: ""
        val spamScore   = intent.getIntExtra(EXTRA_SPAM_SCORE, 0)
        val hasOcr      = intent.getBooleanExtra(EXTRA_HAS_OCR, false)
        val isDupe      = intent.getBooleanExtra(EXTRA_IS_DUPE, false)
        isSelected      = intent.getBooleanExtra(EXTRA_IS_SELECTED, false)

        val uri = Uri.parse(uriString)

        // Load full image
        binding.imgFullscreen.load(uri) {
            crossfade(true)
        }

        // Header info
        binding.txtFullFileName.text = name
        binding.txtFullFileInfo.text = "$size  •  Spam ${spamScore}%"

        // Spam badge colour
        val badgeBg = when {
            spamScore >= 80 -> R.drawable.badge_high_spam
            spamScore >= 50 -> R.drawable.badge_medium_spam
            else            -> R.drawable.badge_low_spam
        }
        binding.txtFullSpamBadge.text = "SPAM $spamScore%"
        binding.txtFullSpamBadge.setBackgroundResource(badgeBg)

        // Extra badges
        binding.txtFullOcrBadge.visibility  = if (hasOcr)  View.VISIBLE else View.GONE
        binding.txtFullDupeBadge.visibility = if (isDupe)  View.VISIBLE else View.GONE

        // Select toggle button
        updateSelectButton()
        binding.btnToggleSelect.setOnClickListener {
            isSelected = !isSelected
            updateSelectButton()
            // Send result back to ScanActivity immediately
            val data = Intent().putExtra(EXTRA_NEW_SELECTION_STATE, isSelected)
            setResult(RESULT_SELECTION_CHANGED, data)
        }

        // Close button
        binding.btnClose.setOnClickListener { finish() }

        setupZoom()
    }

    private fun updateSelectButton() {
        if (isSelected) {
            binding.btnToggleSelect.text = "Deselect"
            binding.btnToggleSelect.setBackgroundColor(
                android.graphics.Color.parseColor("#FC8181") // coral red
            )
        } else {
            binding.btnToggleSelect.text = "Select"
            binding.btnToggleSelect.setBackgroundColor(
                android.graphics.Color.parseColor("#4FD1C5") // teal
            )
        }
    }

    // ── Pinch-to-zoom + double-tap-to-zoom ──
    private fun setupZoom() {
        scaleDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor *= detector.scaleFactor
                scaleFactor = scaleFactor.coerceIn(1f, 5f)
                applyTransform()
                return true
            }
        })

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val targetScale = if (scaleFactor > 1.5f) 1f else 2.5f
                ObjectAnimator.ofFloat(scaleFactor, targetScale).apply {
                    duration = 280
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        scaleFactor = it.animatedValue as Float
                        if (scaleFactor == 1f) { translateX = 0f; translateY = 0f }
                        applyTransform()
                    }
                    start()
                }
                return true
            }

            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scaleFactor > 1f) {
                    translateX -= distanceX
                    translateY -= distanceY
                    applyTransform()
                }
                return true
            }
        })

        binding.imgFullscreen.setOnTouchListener { _, event ->
            val scale = scaleDetector.onTouchEvent(event)
            val gesture = gestureDetector.onTouchEvent(event)

            // Single tap — toggle overlays visibility
            if (event.action == MotionEvent.ACTION_UP && !scaleDetector.isInProgress) {
                toggleOverlays()
            }

            scale || gesture || true
        }
    }

    private fun applyTransform() {
        binding.imgFullscreen.scaleX = scaleFactor
        binding.imgFullscreen.scaleY = scaleFactor
        binding.imgFullscreen.translationX = translateX
        binding.imgFullscreen.translationY = translateY
    }

    private fun toggleOverlays() {
        val isVisible = binding.topOverlay.alpha > 0.5f
        val targetAlpha = if (isVisible) 0f else 1f
        binding.topOverlay.animate().alpha(targetAlpha).setDuration(200).start()
        binding.bottomOverlay.animate().alpha(targetAlpha).setDuration(200).start()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}
