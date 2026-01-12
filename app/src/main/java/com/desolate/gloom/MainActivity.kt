package com.desolate.gloom

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.desolate.gloom.adapter.VideoListAdapter
import com.desolate.gloom.databinding.ActivityMainBinding
import com.desolate.gloom.model.VideoModel
import com.desolate.gloom.util.UiUtil
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    lateinit var adapter: VideoListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.bottomNavBar.setOnItemSelectedListener { menuItem ->
            when(menuItem.itemId) {
                R.id.bottom_menu_home -> {
                    UiUtil.showToast(this, "Home")
                }
                R.id.bottom_menu_add_video -> {
                    startActivity(Intent(this, VideoUploadActivity::class.java))
                }
                R.id.bottom_menu_profile -> {
                    val intent = Intent(this, ProfileActivity::class.java)
                    val currentUser = SupabaseManager.client.auth.currentUserOrNull()
                    val userId = currentUser?.id

                    intent.putExtra("profile_user_id", userId)
                    startActivity(intent)

                }
            }
            false
        }
        setupViewPager()
    }

    private fun setupViewPager() {
        adapter = VideoListAdapter()
        binding.viewPager.adapter = adapter
    }

    override fun onStart() {
        super.onStart()
        loadVideosFromSupabase()
    }

    override fun onResume() {
        super.onResume()
        loadVideosFromSupabase()
    }

    private fun loadVideosFromSupabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val videos = SupabaseManager.client
                    .from("videos")
                    .select()
                    .decodeList<VideoModel>()

                launch(Dispatchers.Main) {
                    adapter.submitList(videos)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(Dispatchers.Main) {
                    UiUtil.showToast(this@MainActivity, "Error loading video")
                }
            }
        }
    }
}
