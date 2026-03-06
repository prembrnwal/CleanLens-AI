package com.cleanlens.ai

import android.animation.ValueAnimator
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
 * Full-screen image viewer with pinch-to-zoom, double-tap-to-zoom and pan.
 */
class ImageViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_IMAGE_URI            = "extra_image_uri"
        const val EXTRA_IMAGE_NAME           = "extra_image_name"
        const val EXTRA_IMAGE_SIZE           = "extra_image_size"
        const val EXTRA_SPAM_SCORE           = "extra_spam_score"
        const val EXTRA_IS_SELECTED          = "extra_is_selected"
        const val EXTRA_HAS_OCR              = "extra_has_ocr"
        const val EXTRA_IS_DUPE              = "extra_is_dupe"
        const val RESULT_SELECTION_CHANGED   = 100
        const val EXTRA_NEW_SELECTION_STATE  = "extra_new_selection_state"
    }

    private lateinit var binding: ActivityImageViewerBinding
    private var isSelected = false

    // Zoom / pan state
    private var scaleFactor  = 1f
    private var translateX   = 0f
    private var translateY   = 0f
    private lateinit var scaleDetector:   ScaleGestureDetector
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val uriString  = intent.getStringExtra(EXTRA_IMAGE_URI)  ?: run { finish(); return }
        val name       = intent.getStringExtra(EXTRA_IMAGE_NAME) ?: "Unknown"
        val size       = intent.getStringExtra(EXTRA_IMAGE_SIZE) ?: ""
        val spamScore  = intent.getIntExtra(EXTRA_SPAM_SCORE, 0)
        val hasOcr     = intent.getBooleanExtra(EXTRA_HAS_OCR, false)
        val isDupe     = intent.getBooleanExtra(EXTRA_IS_DUPE, false)
        isSelected     = intent.getBooleanExtra(EXTRA_IS_SELECTED, false)

        // Load image
        binding.imgFullscreen.load(Uri.parse(uriString)) { crossfade(true) }

        // Header
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
        binding.txtFullOcrBadge.visibility  = if (hasOcr) View.VISIBLE else View.GONE
        binding.txtFullDupeBadge.visibility = if (isDupe) View.VISIBLE else View.GONE

        // Select toggle
        refreshSelectButton()
        binding.btnToggleSelect.setOnClickListener {
            isSelected = !isSelected
            refreshSelectButton()
            setResult(RESULT_SELECTION_CHANGED,
                Intent().putExtra(EXTRA_NEW_SELECTION_STATE, isSelected))
        }

        // Close
        binding.btnClose.setOnClickListener { finish() }

        setupZoomAndPan()
    }

    private fun refreshSelectButton() {
        if (isSelected) {
            binding.btnToggleSelect.text = "Deselect"
            binding.btnToggleSelect.setBackgroundColor(0xFFFC8181.toInt())
        } else {
            binding.btnToggleSelect.text = "Select"
            binding.btnToggleSelect.setBackgroundColor(0xFF4FD1C5.toInt())
        }
    }

    // ── Pinch-to-zoom + double-tap + pan ──────────────────────────────────────

    private fun setupZoomAndPan() {

        scaleDetector = ScaleGestureDetector(this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(1f, 5f)
                    if (scaleFactor == 1f) { translateX = 0f; translateY = 0f }
                    applyTransform()
                    return true
                }
            })

        gestureDetector = GestureDetector(this,
            object : GestureDetector.SimpleOnGestureListener() {

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    toggleOverlays()
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    val from = scaleFactor
                    val to   = if (scaleFactor > 1.5f) 1f else 2.5f
                    ValueAnimator.ofFloat(from, to).apply {
                        duration     = 280
                        interpolator = DecelerateInterpolator()
                        addUpdateListener { anim ->
                            scaleFactor = anim.animatedValue as Float
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
                    dX: Float,
                    dY: Float
                ): Boolean {
                    if (scaleFactor > 1f) {
                        translateX -= dX
                        translateY -= dY
                        applyTransform()
                    }
                    return true
                }
            })

        binding.imgFullscreen.setOnTouchListener { _, event ->
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun applyTransform() {
        binding.imgFullscreen.apply {
            scaleX       = scaleFactor
            scaleY       = scaleFactor
            translationX = translateX
            translationY = translateY
        }
    }

    private fun toggleOverlays() {
        val hide = binding.topOverlay.alpha > 0.5f
        val to   = if (hide) 0f else 1f
        binding.topOverlay.animate().alpha(to).setDuration(200).start()
        binding.bottomOverlay.animate().alpha(to).setDuration(200).start()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, android.R.anim.fade_out)
    }
}
