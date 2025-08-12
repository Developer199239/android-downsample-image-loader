// TopHeadlineAdapter.kt
package com.murtuzarahman.downsampleimage

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.murtuzarahman.downsampleimage.databinding.ListItemArticleBinding
import kotlinx.coroutines.*

class TopHeadlineAdapter(
    private val items: List<Article>,
    private val imageLoader: ImageLoader
) : RecyclerView.Adapter<TopHeadlineAdapter.DataViewHolder>() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DataViewHolder {
        val binding = ListItemArticleBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DataViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DataViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: DataViewHolder) {
        holder.cancel()
        super.onViewRecycled(holder)
    }

    fun clear() {
        scope.cancel()
    }

    inner class DataViewHolder(private val binding: ListItemArticleBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private var job: Job? = null
        private val placeholder = ColorDrawable(Color.LTGRAY)

        fun bind(article: Article) {
            cancel()
            binding.progress.isVisible = true
            binding.imageViewBanner.setImageDrawable(placeholder)

            job = scope.launch {
                val bmp = imageLoader.load(article.imageUrl)
                binding.progress.isVisible = false
                if (bmp != null && bindingAdapterPosition != RecyclerView.NO_POSITION) {
                    binding.imageViewBanner.setImageBitmap(bmp)
                } else {
                    binding.imageViewBanner.setImageDrawable(placeholder)
                }
            }
        }

        fun cancel() {
            job?.cancel()
            job = null
        }
    }
}