package com.cleanlens.ai

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the gallery scanning feature.
 * Manages the scan lifecycle using MVVM pattern:
 *   View (ScanActivity) ← observes ← ViewModel ← uses ← Repository/Analyzers
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val galleryScanner = GalleryScanner(context)
    private val spamClassifier = SpamClassifier(context)
    private val ocrAnalyzer = OcrAnalyzer(context)
    private val duplicateDetector = DuplicateDetector(context)

    // ── LiveData for UI ──

    private val _scanState = MutableLiveData<ScanState>(ScanState.Idle)
    val scanState: LiveData<ScanState> = _scanState

    private val _progress = MutableLiveData(0)
    val progress: LiveData<Int> = _progress

    private val _totalImages = MutableLiveData(0)
    val totalImages: LiveData<Int> = _totalImages

    private val _currentImageName = MutableLiveData("")
    val currentImageName: LiveData<String> = _currentImageName

    private val _spamImages = MutableLiveData<List<ScannedImage>>(emptyList())
    val spamImages: LiveData<List<ScannedImage>> = _spamImages

    private val _duplicateGroups = MutableLiveData<Map<String, List<ScannedImage>>>(emptyMap())
    val duplicateGroups: LiveData<Map<String, List<ScannedImage>>> = _duplicateGroups

    private val _totalSpamCount = MutableLiveData(0)
    val totalSpamCount: LiveData<Int> = _totalSpamCount

    private val _totalDuplicateCount = MutableLiveData(0)
    val totalDuplicateCount: LiveData<Int> = _totalDuplicateCount

    private val _storageSaveable = MutableLiveData(0L)
    val storageSaveable: LiveData<Long> = _storageSaveable

    /**
     * Start the full gallery scan pipeline:
     *  1. Query all images from MediaStore
     *  2. Run CNN spam detection on each
     *  3. Run OCR text detection on each
     *  4. Detect duplicates (MD5 + pHash)
     *  5. Calculate combined spam scores
     */
    fun startScan() {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning

            withContext(Dispatchers.IO) {
                // Step 1: Get all images
                val allImages = galleryScanner.getAllImages()
                _totalImages.postValue(allImages.size)

                if (allImages.isEmpty()) {
                    _scanState.postValue(ScanState.Complete)
                    return@withContext
                }

                // Step 2 & 3: Classify each image (CNN + OCR)
                for ((index, image) in allImages.withIndex()) {
                    try {
                        _progress.postValue(index + 1)
                        _currentImageName.postValue(image.name)

                        // Load bitmap
                        val bitmap = loadBitmap(image)
                        if (bitmap != null) {
                            // CNN Classification
                            val classResult = spamClassifier.classify(bitmap)
                            image.spamProbability = classResult.confidence / 100f
                            if (classResult.isSpam) {
                                image.spamProbability = classResult.confidence / 100f
                            } else {
                                image.spamProbability = 1f - (classResult.confidence / 100f)
                            }

                            // OCR Analysis (only if CNN thinks it might be spam)
                            if (image.spamProbability > 0.3f) {
                                val ocrResult = ocrAnalyzer.analyzeImage(bitmap)
                                image.ocrText = ocrResult.extractedText
                                image.ocrKeywordFound = ocrResult.hasSpamKeyword
                            }

                            bitmap.recycle()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Step 4: Detect duplicates
                _scanState.postValue(ScanState.DetectingDuplicates)
                val exactDupes = duplicateDetector.findExactDuplicates(allImages)
                exactDupes.values.flatten().forEach { it.isDuplicate = true }

                // Step 5: Calculate combined spam scores
                allImages.forEach { it.calculateSpamScore() }

                // Filter results
                val spamList = allImages.filter { it.isLikelySpam }
                    .sortedByDescending { it.spamScore }

                val totalSaveable = spamList.sumOf { it.size } +
                        exactDupes.values.flatten()
                            .drop(exactDupes.size) // Keep one copy per group
                            .sumOf { it.size }

                // Post results
                _spamImages.postValue(spamList)
                _duplicateGroups.postValue(exactDupes)
                _totalSpamCount.postValue(spamList.size)
                _totalDuplicateCount.postValue(exactDupes.values.sumOf { it.size - 1 })
                _storageSaveable.postValue(totalSaveable)
            }

            _scanState.value = ScanState.Complete
        }
    }

    /**
     * Toggle selection of a scanned image.
     */
    fun toggleSelection(image: ScannedImage) {
        image.isSelected = !image.isSelected
        // Trigger LiveData update
        _spamImages.value = _spamImages.value
    }

    /**
     * Select all spam images.
     */
    fun selectAll() {
        _spamImages.value?.forEach { it.isSelected = true }
        _spamImages.value = _spamImages.value
    }

    /**
     * Deselect all.
     */
    fun deselectAll() {
        _spamImages.value?.forEach { it.isSelected = false }
        _spamImages.value = _spamImages.value
    }

    /**
     * Get list of selected images for deletion.
     */
    fun getSelectedImages(): List<ScannedImage> {
        return _spamImages.value?.filter { it.isSelected } ?: emptyList()
    }

    /**
     * Load a bitmap from a ScannedImage URI.
     */
    @Suppress("DEPRECATION")
    private fun loadBitmap(image: ScannedImage): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, image.uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.setTargetSampleSize(2) // Downsample for speed
                    decoder.isMutableRequired = true
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                MediaStore.Images.Media.getBitmap(context.contentResolver, image.uri)
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        spamClassifier.close()
        ocrAnalyzer.close()
    }

    /**
     * Represents the current state of the scanning process.
     */
    sealed class ScanState {
        data object Idle : ScanState()
        data object Scanning : ScanState()
        data object DetectingDuplicates : ScanState()
        data object Complete : ScanState()
    }
}
