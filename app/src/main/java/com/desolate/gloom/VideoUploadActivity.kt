package com.desolate.gloom

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.desolate.gloom.databinding.ActivityVideoUploadBinding
import com.desolate.gloom.model.VideoModel
import com.desolate.gloom.util.UiUtil
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.util.UUID

class VideoUploadActivity : AppCompatActivity() {
    lateinit var binding: ActivityVideoUploadBinding
    private var selectedVideoUri : Uri? = null
    lateinit var videoLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val TAG = "VideoUploadActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }

        setContentView(R.layout.activity_video_upload)

        Log.d(TAG, "Activity created!")
        binding = ActivityVideoUploadBinding.inflate(layoutInflater)
        setContentView(binding.root)

        lifecycleScope.launch {
            try {
                SupabaseManager.client.auth.loadFromStorage()
                val user = SupabaseManager.client.auth.currentUserOrNull()
                Log.d(TAG, "User on activity start: $user")
                if (user == null) {
                    UiUtil.showToast(this@VideoUploadActivity, "Please login first")
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking auth on start", e)
            }
        }

        videoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "Video picker result: ${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                selectedVideoUri = result.data?.data
                Log.d(TAG, "Video selected: $selectedVideoUri")
                showPostView()
            } else {
                Log.d(TAG, "Video selection cancelled or failed")
            }
        }
        binding.uploadView.setOnClickListener {
            Log.d(TAG, "Upload view clicked")
            checkPermissionAndOpenVideoPicker()
        }

        binding.submitPostBtn.setOnClickListener {
            Log.d(TAG, "Submit button clicked")
            postVideo()
        }

        binding.cancelPostBtn.setOnClickListener {
            Log.d(TAG, "Cancel button clicked")
            finish()
        }
    }

    private fun postVideo() {
        Log.d(TAG, "postVideo() started")

        val caption = binding.postCaptionInput.text.toString()
        Log.d(TAG, "Caption: $caption")

        if (caption.isEmpty()) {
            Log.d(TAG, "Caption is empty, showing error")
            binding.postCaptionInput.setError("Write something")
            return
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Starting video upload process")
                setInProgress(true)

                val supabase = SupabaseManager.client
                Log.d(TAG, "Supabase client obtained")

                Log.d(TAG, "Loading session from storage...")
                try {
                    supabase.auth.loadFromStorage()
                    Log.d(TAG, "Session loaded from storage")
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading session from storage: ${e.message}")
                }

                Log.d(TAG, "Checking current user...")
                val currentUser = supabase.auth.currentUserOrNull()
                val currentSession = supabase.auth.currentSessionOrNull()

                Log.d(TAG, "Current user: $currentUser")
                Log.d(TAG, "Current session: $currentSession")

                if (currentUser == null) {
                    Log.e(TAG, "CRITICAL: User is NULL - not authenticated!")
                    UiUtil.showToast(applicationContext, "Please sign in first")

                    val intent = Intent(this@VideoUploadActivity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                    finish()
                    setInProgress(false)
                    return@launch
                }

                val userId = currentUser.id
                val userEmail = currentUser.email ?: "no email"

                Log.d(TAG, "User authenticated:")
                Log.d(TAG, "ID: $userId")
                Log.d(TAG, "Email: $userEmail")
                Log.d(TAG, "Session: ${supabase.auth.currentSessionOrNull()}")

                if (selectedVideoUri == null) {
                    Log.e(TAG, "No video selected")
                    UiUtil.showToast(applicationContext, "Please select a video first")
                    setInProgress(false)
                    return@launch
                }

                Log.d(TAG, "Selected video URI: $selectedVideoUri")

                val fileName = "${System.currentTimeMillis()}_${selectedVideoUri!!.lastPathSegment ?: "video"}.mp4"
                Log.d(TAG, "Uploading video with filename: $fileName")

                val videoUrl = try {
                    StorageUtil.uploadVideoFile(
                        context = applicationContext,
                        supabase = supabase,
                        fileUri = selectedVideoUri!!,
                        fileName = fileName
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Video upload failed: ${e.message}", e)
                    UiUtil.showToast(applicationContext, "Video upload failed: ${e.message}")
                    setInProgress(false)
                    return@launch
                }

                Log.d(TAG, "Video uploaded successfully: $videoUrl")

                Log.d(TAG, "Inserting video data into database...")
                try {
                    postToSupabase(supabase, videoUrl, caption, userId)
                    Log.d(TAG, "Database insert successful")

                    setInProgress(false)
                    UiUtil.showToast(applicationContext, "Video uploaded successfully!")
                    finish()
                } catch (e: Exception) {
                    Log.e(TAG, "Database insert failed: ${e.message}", e)

                    try {
                        StorageUtil.deleteFile(supabase, fileName)
                        Log.d(TAG,"Cleaned up uploaded video file")
                    } catch (cleanupError: Exception) {
                        Log.e(TAG, "Failed to cleanup video file: ${cleanupError.message}")
                    }
                    throw e
                }

            } catch (e: Exception) {
                setInProgress(false)
                UiUtil.showToast(applicationContext,"Video failed: ${e.message}")
            }
        }

    }

    suspend fun postToSupabase(supabase : SupabaseClient, url : String, title : String, userId : String) {
        Log.d(TAG, "postToSupabase() called with:")
        Log.d(TAG, "URL: $url")
        Log.d(TAG, "Title: $title")
        Log.d(TAG, "UserId: $userId")

        if (url.isBlank()) {
            throw IllegalArgumentException("URL is blank")
        }
        if (title.isBlank()) {
            throw IllegalArgumentException("Title is blank")
        }
        if (userId.isBlank()) {
            throw IllegalArgumentException("UserID is blank")
        }

        try {
            UUID.fromString(userId)
            Log.d(TAG, "UserId is valid UUID")
        } catch (e: Exception) {
            Log.e(TAG, "UserId is NOT valid UUID: $userId")
            throw IllegalArgumentException("Invalid user ID format: $userId")
        }

        val videoData = VideoModel(
            title = title,
            url = url,
            uploader_id = userId
        )

        Log.d(TAG, "Video data to insert: $videoData")

        try {
            Log.d(TAG, "Executing Supabase insert...")
            val result = supabase.from("videos").insert(videoData)
            Log.d(TAG, "Insert executed successfully")
            Log.d(TAG, "Insert result: $result")
        } catch (e: Exception) {
            Log.e(TAG, "Supabase insert failed", e)
            Log.e(TAG, "Error type: ${e::class.java.name}")
            Log.e(TAG, "Error message: ${e.message}")
            throw e
        }

    }

    private fun setInProgress(inProgress : Boolean){
        Log.d(TAG, "setInProgress: $inProgress")
        if (inProgress) {
            binding.progressBar.visibility = View.VISIBLE
            binding.submitPostBtn.visibility = View.GONE
            binding.uploadView.isEnabled = false
        } else {
            binding.progressBar.visibility = View.GONE
            binding.submitPostBtn.visibility = View.VISIBLE
            binding.uploadView.isEnabled = true
        }
    }

    private fun showPostView() {
        selectedVideoUri?.let {
            Log.d(TAG, "Showing post view")
            binding.postView.visibility = View.VISIBLE
            binding.uploadView.visibility = View.GONE
            Glide.with(binding.postThumbnailView).load(it).into(binding.postThumbnailView)
        }
    }

    private fun checkPermissionAndOpenVideoPicker() {
        Log.d(TAG, "Checking permissions...")

        val readExternalVideo = if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, readExternalVideo) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission granted, opening video picker")
            openVideoPicker()
        } else {
            Log.d(TAG, "Permission not granted, requesting...")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(readExternalVideo),
                100
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "Permission granted after request")
                openVideoPicker()
            } else {
                Log.d(TAG, "Permission denied by user")
                UiUtil.showToast(this, "Permission denied - cannot access videos")
            }
        }
    }

    private fun openVideoPicker() {
        Log.d(TAG, "Opening video picker")
        var intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI).apply {
            type = "video/*"
        }
        videoLauncher.launch(intent)
    }

}