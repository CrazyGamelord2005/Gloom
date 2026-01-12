package com.desolate.gloom.adapter

import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.desolate.gloom.SingleVideoPlayerActivity
import com.desolate.gloom.databinding.ProfileVideoItemRowBinding
import com.desolate.gloom.model.VideoModel

class ProfileVideoAdapter : ListAdapter<VideoModel, ProfileVideoAdapter.VideoViewHolder>(DiffCallback) {

    inner class VideoViewHolder(private val binding: ProfileVideoItemRowBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(video: VideoModel) {
            Glide.with(binding.thumbnailImageView)
                .load(video.url)
                .into(binding.thumbnailImageView)
            binding.thumbnailImageView.setOnClickListener {
                val intent = Intent(binding.thumbnailImageView.context, SingleVideoPlayerActivity::class.java)
                intent.putExtra("videoId", video.video_id)
                binding.thumbnailImageView.context.startActivity(intent)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoViewHolder {
        val binding = ProfileVideoItemRowBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VideoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VideoViewHolder, position: Int) {
        val video = getItem(position)
        holder.bind(video)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<VideoModel>() {
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