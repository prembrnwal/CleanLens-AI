package com.cleanlens.ai

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load

/**
 * RecyclerView Adapter for displaying scanned spam images in a grid.
 * Shows image thumbnail, spam score badge, and selection checkbox.
 */
class ScanResultAdapter(
    private var images: List<ScannedImage> = emptyList(),
    private val onItemClick: (ScannedImage) -> Unit,
    private val onSelectionChanged: (ScannedImage) -> Unit,
) : RecyclerView.Adapter<ScanResultAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val thumbnail: ImageView = itemView.findViewById(R.id.imgThumbnail)
        val spamBadge: TextView = itemView.findViewById(R.id.txtSpamBadge)
        val checkbox: CheckBox = itemView.findViewById(R.id.checkboxSelect)
        val fileName: TextView = itemView.findViewById(R.id.txtFileName)
        val fileSize: TextView = itemView.findViewById(R.id.txtFileSize)
        val ocrBadge: TextView = itemView.findViewById(R.id.txtOcrBadge)
        val dupeBadge: TextView = itemView.findViewById(R.id.txtDupeBadge)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scan_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val image = images[position]

        // Load thumbnail using Coil
        holder.thumbnail.load(image.uri) {
            crossfade(true)
            placeholder(android.R.color.darker_gray)
        }

        // Spam score badge
        val scorePercent = (image.spamScore * 100).toInt()
        holder.spamBadge.text = "${scorePercent}%"
        holder.spamBadge.setBackgroundResource(
            if (scorePercent >= 80) R.drawable.badge_high_spam
            else if (scorePercent >= 50) R.drawable.badge_medium_spam
            else R.drawable.badge_low_spam
        )

        // File info
        holder.fileName.text = image.name
        holder.fileSize.text = image.sizeFormatted

        // OCR badge
        if (image.ocrKeywordFound) {
            holder.ocrBadge.visibility = View.VISIBLE
            holder.ocrBadge.text = "📝 Text"
        } else {
            holder.ocrBadge.visibility = View.GONE
        }

        // Duplicate badge
        if (image.isDuplicate) {
            holder.dupeBadge.visibility = View.VISIBLE
            holder.dupeBadge.text = "📋 Dupe"
        } else {
            holder.dupeBadge.visibility = View.GONE
        }

        // Checkbox
        holder.checkbox.isChecked = image.isSelected
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            image.isSelected = isChecked
            onSelectionChanged(image)
        }

        // Click listener
        holder.itemView.setOnClickListener {
            onItemClick(image)
        }
    }

    override fun getItemCount(): Int = images.size

    fun updateImages(newImages: List<ScannedImage>) {
        images = newImages
        notifyDataSetChanged()
    }

    fun getSelectedCount(): Int = images.count { it.isSelected }

    fun getSelectedSize(): Long = images.filter { it.isSelected }.sumOf { it.size }
}
