package com.desolate.gloom

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.desolate.gloom.adapter.VideoListAdapter
import com.desolate.gloom.databinding.ActivitySingleVideoPlayerBinding
import com.desolate.gloom.model.VideoModel
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SingleVideoPlayerActivity : AppCompatActivity() {

    lateinit var binding: ActivitySingleVideoPlayerBinding
    lateinit var videoId: String
    lateinit var adapter: VideoListAdapter
    private var videosList: List<VideoModel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySingleVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        videoId = intent.getStringExtra("videoId")!!
        setupViewPager()
    }

    fun setupViewPager() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                videosList = SupabaseManager.client
                    .from("videos")
                    .select()
                    .decodeList<VideoModel>()

                val sortedVideos = videosList.sortedByDescending { it.created_at }
                val initialPosition = sortedVideos.indexOfFirst { it.video_id == videoId }

                launch(Dispatchers.Main) {
                    adapter = VideoListAdapter()
                    adapter.submitList(sortedVideos)

                    binding.videoPager.adapter = adapter

                    if (initialPosition != -1) {
                        binding.videoPager.setCurrentItem(initialPosition, false)
                    }

                    binding.videoPager.orientation = androidx.viewpager2.widget.ViewPager2.ORIENTATION_VERTICAL
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}