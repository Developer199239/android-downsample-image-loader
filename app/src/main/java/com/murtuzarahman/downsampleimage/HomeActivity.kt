package com.murtuzarahman.downsampleimage

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.murtuzarahman.downsampleimage.databinding.ActivityHomeBinding

class HomeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityHomeBinding
    private lateinit var adapter: TopHeadlineAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageLoader = ImageLoader(
            applicationContext,
            targetMemBytes = 512 * 1024,  // aim ~0.5 MB in-memory per bitmap
            useRgb565 = true               // halves memory vs ARGB_8888; no alpha channel
        )

        val articles = demoArticles()

        adapter = TopHeadlineAdapter(articles, imageLoader)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun demoArticles(): List<Article> = listOf(
        Article(1, "A", "https://picsum.photos/id/1018/4000/3000"),
        Article(2, "B", "https://picsum.photos/id/1015/6000/4000"),
        Article(3, "C", "https://picsum.photos/id/1021/2048/1206"),
        Article(4, "D", "https://picsum.photos/id/1003/1181/1772"),
        Article(5, "E", "https://picsum.photos/id/1025/4951/3301")
    )
}