package com.cleanlens.ai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.cleanlens.ai.databinding.ActivityScanBinding

/**
 * Full gallery scanning screen.
 * Pipeline: MediaStore → CNN → OCR → Duplicates → Smart Score → Suggestions
 */
class ScanActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOLDER_PATH = "extra_folder_path"
        const val EXTRA_FOLDER_NAME = "extra_folder_name"
    }

    private lateinit var binding: ActivityScanBinding
    private lateinit var viewModel: ScanViewModel
    private lateinit var adapter: ScanResultAdapter
    private var folderPath: String = ""

    // For Android 11+ system trash / delete request
    private val trashRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "✅ Moved to Recently Deleted!", Toast.LENGTH_SHORT).show()
            // Re-scan after trashing
            viewModel.startScan(folderPath)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get folder from intent
        folderPath = intent.getStringExtra(EXTRA_FOLDER_PATH) ?: ""
        val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME) ?: "Gallery"

        viewModel = ViewModelProvider(this)[ScanViewModel::class.java]

        setupRecyclerView()
        setupButtons()
        observeViewModel()

        // Auto-start scan on the selected folder
        if (folderPath.isNotEmpty()) {
            viewModel.startScan(folderPath)
        }
    }

    private fun setupRecyclerView() {
        adapter = ScanResultAdapter(
            onItemClick = { image ->
                // Toggle selection on tap
                viewModel.toggleSelection(image)
                updateBottomBar()
            },
            onSelectionChanged = { _ ->
                updateBottomBar()
            }
        )

        binding.recyclerResults.layoutManager = GridLayoutManager(this, 2)
        binding.recyclerResults.adapter = adapter
    }

    private fun setupButtons() {
        // Select All
        binding.btnSelectAll.setOnClickListener {
            val allSelected = adapter.getSelectedCount() == (viewModel.spamImages.value?.size ?: 0)
            if (allSelected) {
                viewModel.deselectAll()
                binding.btnSelectAll.text = "Select All"
            } else {
                viewModel.selectAll()
                binding.btnSelectAll.text = "Deselect"
            }
            adapter.notifyDataSetChanged()
            updateBottomBar()
        }

        // Move to Trash button
        binding.btnDelete.setOnClickListener {
            val selected = viewModel.getSelectedImages()
            if (selected.isEmpty()) {
                Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Confirmation dialog
            AlertDialog.Builder(this, com.google.android.material.R.style.MaterialAlertDialog_Material3)
                .setTitle("🗑 Move ${selected.size} images to trash?")
                .setMessage("Images will be moved to Recently Deleted and automatically removed after 30 days. You can restore them anytime before that.")
                .setPositiveButton("Move to Trash") { _, _ ->
                    moveSelectedToTrash(selected)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun observeViewModel() {
        // Scan state
        viewModel.scanState.observe(this) { state ->
            when (state) {
                is ScanViewModel.ScanState.Idle -> {
                    binding.progressCard.visibility = View.GONE
                }
                is ScanViewModel.ScanState.Scanning -> {
                    binding.progressCard.visibility = View.VISIBLE
                    binding.summaryCard.visibility = View.GONE
                    binding.bottomBar.visibility = View.GONE
                    binding.emptyState.visibility = View.GONE
                }
                is ScanViewModel.ScanState.DetectingDuplicates -> {
                    binding.txtScanStatus.text = "Detecting duplicates..."
                    binding.txtCurrentImage.text = "Comparing image hashes..."
                }
                is ScanViewModel.ScanState.Complete -> {
                    binding.progressCard.visibility = View.GONE
                    binding.summaryCard.visibility = View.VISIBLE

                    val spamCount = viewModel.totalSpamCount.value ?: 0
                    val dupeCount = viewModel.totalDuplicateCount.value ?: 0

                    if (spamCount == 0 && dupeCount == 0) {
                        binding.emptyState.visibility = View.VISIBLE
                        binding.recyclerResults.visibility = View.GONE
                        binding.bottomBar.visibility = View.GONE
                    } else {
                        binding.emptyState.visibility = View.GONE
                        binding.recyclerResults.visibility = View.VISIBLE
                        binding.bottomBar.visibility = View.VISIBLE
                    }
                }
            }
        }

        // Progress updates
        viewModel.progress.observe(this) { current ->
            val total = viewModel.totalImages.value ?: 0
            if (total > 0) {
                val percent = (current * 100) / total
                binding.scanProgressCircle.progress = percent
                binding.txtScanStatus.text = "Scanning $current / $total images..."
            }
        }

        viewModel.currentImageName.observe(this) { name ->
            binding.txtCurrentImage.text = name
        }

        // Results
        viewModel.spamImages.observe(this) { images ->
            adapter.updateImages(images)
        }

        viewModel.totalSpamCount.observe(this) { count ->
            binding.txtSpamCount.text = count.toString()
        }

        viewModel.totalDuplicateCount.observe(this) { count ->
            binding.txtDupeCount.text = count.toString()
        }

        viewModel.storageSaveable.observe(this) { bytes ->
            val mb = bytes / (1024.0 * 1024.0)
            binding.txtStorageSave.text = if (mb >= 1024) {
                String.format("%.1f GB", mb / 1024)
            } else {
                String.format("%.0f MB", mb)
            }
        }
    }

    private fun updateBottomBar() {
        val selectedCount = adapter.getSelectedCount()
        val selectedSize = adapter.getSelectedSize()
        val mb = selectedSize / (1024.0 * 1024.0)

        if (selectedCount > 0) {
            binding.bottomBar.visibility = View.VISIBLE
            binding.txtSelectedInfo.text = "$selectedCount selected (${String.format("%.1f MB", mb)})"
        } else {
            binding.txtSelectedInfo.text = "No images selected"
        }
    }

    /**
     * Move selected images to the system's Recently Deleted folder.
     * Uses MediaStore.createTrashRequest on Android 11+ which sends
     * images to the Gallery app's built-in "Recently Deleted" section.
     * On older Android, falls back to direct deletion.
     */
    @Suppress("DEPRECATION")
    private fun moveSelectedToTrash(images: List<ScannedImage>) {
        try {
            val uris = images.map { it.uri }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ — use system trash (goes to Gallery's Recently Deleted)
                val pendingIntent = MediaStore.createTrashRequest(
                    contentResolver,
                    uris,
                    true  // isTrashed = true → move to trash
                )
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                trashRequestLauncher.launch(request)
            } else {
                // Android 10 and below — no system trash, delete directly
                var deletedCount = 0
                for (image in images) {
                    val rows = contentResolver.delete(image.uri, null, null)
                    if (rows > 0) deletedCount++
                }
                Toast.makeText(this, "✅ Deleted $deletedCount images", Toast.LENGTH_SHORT).show()
                viewModel.startScan(folderPath)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "❌ Error moving images to trash", Toast.LENGTH_SHORT).show()
        }
    }
}
