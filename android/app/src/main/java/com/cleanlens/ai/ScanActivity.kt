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

    private lateinit var binding: ActivityScanBinding
    private lateinit var viewModel: ScanViewModel
    private lateinit var adapter: ScanResultAdapter

    // For Android 11+ scoped storage delete request
    private val deleteRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "✅ Images deleted successfully!", Toast.LENGTH_SHORT).show()
            // Re-scan after deletion
            viewModel.startScan()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ScanViewModel::class.java]

        setupRecyclerView()
        setupButtons()
        observeViewModel()

        // Auto-start scan
        viewModel.startScan()
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

        // Delete button
        binding.btnDelete.setOnClickListener {
            val selected = viewModel.getSelectedImages()
            if (selected.isEmpty()) {
                Toast.makeText(this, "No images selected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Confirmation dialog
            AlertDialog.Builder(this, com.google.android.material.R.style.MaterialAlertDialog_Material3)
                .setTitle("🗑 Delete ${selected.size} images?")
                .setMessage("This will permanently delete the selected spam images. This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    deleteSelectedImages(selected)
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

    private fun deleteSelectedImages(images: List<ScannedImage>) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ — use MediaStore delete request
                val uris = images.map { it.uri }
                val pendingIntent = MediaStore.createDeleteRequest(contentResolver, uris)
                val request = IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                deleteRequestLauncher.launch(request)
            } else {
                // Android 10 and below — delete directly
                var deletedCount = 0
                for (image in images) {
                    val rows = contentResolver.delete(image.uri, null, null)
                    if (rows > 0) deletedCount++
                }
                Toast.makeText(this, "✅ Deleted $deletedCount images", Toast.LENGTH_SHORT).show()
                viewModel.startScan() // Re-scan
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "❌ Error deleting images", Toast.LENGTH_SHORT).show()
        }
    }
}
