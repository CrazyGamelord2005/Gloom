package com.desolate.gloom.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.desolate.gloom.ProfileActivity
import com.desolate.gloom.R
import com.desolate.gloom.SupabaseManager
import com.desolate.gloom.databinding.VideoItemRowBinding
import com.desolate.gloom.model.UserModel
import com.desolate.gloom.model.VideoModel
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VideoListAdapter : ListAdapter<VideoModel, VideoListAdapter.VideoViewHolder>(VideoDiffCallback()) {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = VideoItemRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = getItem(position)
        holder.bindVideo(video)
    }

    class VideoViewHolder(private val binding: VideoItemRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bindVideo(videoModel: VideoModel) {

            loadUserData(videoModel.uploader_id)

            binding.captionView.text = videoModel.title

            binding.progressBar.visibility = View.VISIBLE


            binding.videoView.apply {
                setVideoPath(videoModel.url)
                setOnPreparedListener {
                    binding.progressBar.visibility = View.GONE
                    it.start()
                    it.isLooping = true
                }
                setOnClickListener {
                    if (isPlaying) {
                        pause()
                        binding.pauseIcon.visibility = View.VISIBLE
                    } else {
                        start()
                        binding.pauseIcon.visibility = View.GONE
                    }
                }
            }
        }

        private fun loadUserData(uploaderId: String) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val users = SupabaseManager.client
                        .from("users")
                        .select()
                        .decodeList<UserModel>()

                    val userModel = users.findLast { it.id == uploaderId }

                    CoroutineScope(Dispatchers.Main).launch {
                        if (userModel != null) {
                            binding.usernameView.text = userModel.username
                            if (userModel.profilePic.isNotEmpty()) {
                                Glide.with(binding.profileIcon.context)
                                    .load(userModel.profilePic)
                                    .circleCrop()
                                    .apply(
                                        RequestOptions().placeholder(R.drawable.icon_profile)
                                    )
                                    .into(binding.profileIcon)
                            } else {
                                binding.profileIcon.setImageResource(R.drawable.icon_profile)
                            }

                            binding.userDetailLayout.setOnClickListener {
                                val intent = Intent(binding.userDetailLayout.context,
                                    ProfileActivity::class.java)
                                intent.putExtra("profile_user_id", userModel.id)
                                binding.userDetailLayout.context.startActivity(intent)
                            }
                        } else {
                            binding.usernameView.text = "User"
                            binding.profileIcon.setImageResource(R.drawable.icon_profile)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    CoroutineScope(Dispatchers.Main).launch {
                        binding.usernameView.text = "User"
                        binding.profileIcon.setImageResource(R.drawable.icon_profile)
                    }
                }

            }
        }
    }

    class VideoDiffCallback : DiffUtil.ItemCallback<VideoModel>() {
        override fun areItemsTheSame(
            oldItem: VideoModel,
            newItem: VideoModel
        ): Boolean {
            return oldItem.video_id == newItem.video_id
        }

        override fun areContentsTheSame(
            oldItem: VideoModel,
            newItem: VideoModel
        ): Boolean {
            return oldItem == newItem
        }

    }
}

