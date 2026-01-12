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
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.desolate.gloom.adapter.ProfileVideoAdapter
import com.desolate.gloom.databinding.ActivityProfileBinding
import com.desolate.gloom.model.UserModel
import com.desolate.gloom.model.VideoModel
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private lateinit var profileUserId: String
    private lateinit var currentUserId: String
    private lateinit var photoLauncher: ActivityResultLauncher<Intent>
    private lateinit var profileUserModel: UserModel
    private lateinit var videoAdapter: ProfileVideoAdapter

    private companion object {
        const val TAG = "ProfileActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate started")

        profileUserId = intent.getStringExtra("profile_user_id")!!
        currentUserId = SupabaseManager.client.auth.currentUserOrNull()?.id ?: ""
        Log.d(TAG, "profileUserId: $profileUserId, currentUserId: $currentUserId")

        photoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "Photo picker result: ${result.resultCode}")
            if (result.resultCode == RESULT_OK) {
                uploadToSupabase(result.data?.data!!)
            }
        }

        if (profileUserId == currentUserId) {
            binding.profileBtn.text = "Logout"
            binding.profileBtn.setOnClickListener {
                Log.d(TAG, "Logout clicked")
                logout()
            }
            binding.profilePic.setOnClickListener {
                Log.d(TAG, "Profile pic clicked")
                checkPermissionAndPickPhoto()
            }
        } else {
            binding.profileBtn.text = "Follow"
            binding.profileBtn.setOnClickListener {
                Log.d(TAG, "Follow button clicked")
                followUnfollowUser()
            }
        }
        setupVideoRecyclerView()
        getProfileDataFromSupabase()
    }

    private fun setupVideoRecyclerView() {
        videoAdapter = ProfileVideoAdapter()
        binding.recyclerView.adapter = videoAdapter
        binding.recyclerView.layoutManager = GridLayoutManager(this, 3)
    }
    fun followUnfollowUser() {
        Log.d(TAG, "followUnfollowUser started")
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Getting users from database...")
                val users = SupabaseManager.client
                    .from("users")
                    .select()
                    .decodeList<UserModel>()
                Log.d(TAG, "Received ${users.size} users")

                val currentUser = users.find { it.id == currentUserId }
                val profileUser = users.find { it.id == profileUserId }

                Log.d(TAG, "Current user found: ${currentUser != null}")
                Log.d(TAG, "Profile user found: ${profileUser != null}")

                if (currentUser != null && profileUser != null) {
                    val isFollowing = profileUser.followerList.contains(currentUserId)
                    Log.d(TAG, "Is following according to DB: $isFollowing")

                    if (isFollowing) {
                        // UNFOLLOW
                        Log.d(TAG, "Processing UNFOLLOW")
                        val updatedProfileUser = profileUser.copy(
                            followerList = profileUser.followerList.toMutableList().apply {
                                remove(currentUserId)
                            }
                        )
                        val updatedCurrentUser = currentUser.copy(
                            followingList = currentUser.followingList.toMutableList().apply {
                                remove(profileUserId)
                            }
                        )

                        Log.d(TAG, "Saving updated users...")
                        SupabaseManager.client.from("users").upsert(updatedProfileUser)
                        SupabaseManager.client.from("users").upsert(updatedCurrentUser)
                        Log.d(TAG, "Users saved successfully")

                        profileUserModel = updatedProfileUser

                        launch(Dispatchers.Main) {
                            binding.profileBtn.text = "Follow"
                            binding.progressBar.visibility = View.GONE
                            // Сразу обновляем счетчики
                            binding.followerCount.text = profileUserModel.followerList.size.toString()
                            binding.followingCount.text = profileUserModel.followingList.size.toString()
                            Log.d(TAG, "UI updated to Follow state")
                        }
                    } else {
                        Log.d(TAG, "Processing FOLLOW")
                        val updatedProfileUser = profileUser.copy(
                            followerList = profileUser.followerList.toMutableList().apply {
                                add(currentUserId)
                            }
                        )
                        val updatedCurrentUser = currentUser.copy(
                            followingList = currentUser.followingList.toMutableList().apply {
                                add(profileUserId)
                            }
                        )

                        Log.d(TAG, "Saving updated users...")
                        SupabaseManager.client.from("users").upsert(updatedProfileUser)
                        SupabaseManager.client.from("users").upsert(updatedCurrentUser)
                        Log.d(TAG, "Users saved successfully")

                        profileUserModel = updatedProfileUser

                        launch(Dispatchers.Main) {
                            binding.profileBtn.text = "Unfollow"
                            binding.progressBar.visibility = View.GONE
                            // Сразу обновляем счетчики
                            binding.followerCount.text = profileUserModel.followerList.size.toString()
                            binding.followingCount.text = profileUserModel.followingList.size.toString()
                            Log.d(TAG, "UI updated to Unfollow state")
                        }
                    }
                } else {
                    Log.e(TAG, "Users not found!")
                    launch(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in followUnfollowUser: ${e.message}", e)
                launch(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }
    fun uploadToSupabase(photoUri: Uri) {
        Log.d(TAG, "uploadToSupabase started with URI: $photoUri")
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Reading file bytes...")
                val bytes = contentResolver.openInputStream(photoUri)?.use { it.readBytes() }
                if (bytes == null) {
                    Log.e(TAG, "Failed to read bytes from URI")
                    return@launch
                }
                Log.d(TAG, "Read ${bytes.size} bytes")

                Log.d(TAG, "Uploading to storage...")
                SupabaseManager.client.storage
                    .from("profile-pictures")
                    .upload("$currentUserId/profile.jpg", bytes) {
                        upsert = true
                    }
                Log.d(TAG, "Upload successful")

                val downloadUrl = SupabaseManager.client.storage
                    .from("profile-pictures")
                    .publicUrl("$currentUserId/profile.jpg")
                Log.d(TAG, "Got download URL: $downloadUrl")

                postToSupabase(downloadUrl)

            } catch (e: Exception) {
                Log.e(TAG, "Error in uploadToSupabase: ${e.message}", e)
                launch(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    fun postToSupabase(url: String) {
        Log.d(TAG, "postToSupabase started with URL: $url")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Getting current user data...")
                val users = SupabaseManager.client
                    .from("users")
                    .select()
                    .decodeList<UserModel>()

                val user = users.find { it.id == currentUserId }
                if (user != null) {
                    Log.d(TAG, "Updating user profile picture...")
                    val updatedUser = user.copy(profilePic = url)
                    SupabaseManager.client.from("users").upsert(updatedUser)
                    Log.d(TAG, "Profile picture updated successfully")

                    launch(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                        getProfileDataFromSupabase()
                    }
                } else {
                    Log.e(TAG, "Current user not found!")
                    launch(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in postToSupabase: ${e.message}", e)
                launch(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    fun checkPermissionAndPickPhoto() {
        Log.d(TAG, "checkPermissionAndPickPhoto started")
        val readExternalPhoto = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, readExternalPhoto) == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission granted, opening picker")
            openPhotoPicker()
        } else {
            Log.d(TAG, "Requesting permission...")
            ActivityCompat.requestPermissions(this, arrayOf(readExternalPhoto), 100)
        }
    }

    private fun openPhotoPicker() {
        Log.d(TAG, "openPhotoPicker started")
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        photoLauncher.launch(intent)
    }

    fun logout() {
        Log.d(TAG, "logout started")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Signing out...")
                SupabaseManager.client.auth.signOut()
                Log.d(TAG, "Sign out successful")

                launch(Dispatchers.Main) {
                    val intent = Intent(this@ProfileActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in logout: ${e.message}", e)
            }
        }
    }

    fun getProfileDataFromSupabase() {
        Log.d(TAG, "getProfileDataFromSupabase started")
        binding.progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Fetching users...")
                val users = SupabaseManager.client
                    .from("users")
                    .select()
                    .decodeList<UserModel>()
                Log.d(TAG, "Received ${users.size} users")

                val user = users.find { it.id == profileUserId }
                if (user != null) {
                    Log.d(TAG, "Profile user found: ${user.username}")
                    profileUserModel = user
                    launch(Dispatchers.Main) {
                        setUI()
                    }
                } else {
                    Log.e(TAG, "Profile user not found!")
                    launch(Dispatchers.Main) {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in getProfileDataFromSupabase: ${e.message}", e)
                launch(Dispatchers.Main) {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    fun setUI() {
        Log.d(TAG, "setUI started")
        profileUserModel.apply {
            Log.d(TAG, "Setting UI for user: $username")
            Log.d(TAG, "Profile pic URL: $profilePic")
            Log.d(TAG, "Follower count: ${followerList.size}, Following count: ${followingList.size}")

            Glide.with(binding.profilePic).load(profilePic)
                .apply(RequestOptions().placeholder(R.drawable.icon_account_circle))
                .circleCrop()
                .into(binding.profilePic)

            binding.profileUsername.text = "@$username"

            if (profileUserId != currentUserId) {
                val isFollowing = followerList.contains(currentUserId)
                Log.d(TAG, "Is following: $isFollowing")
                binding.profileBtn.text = if (isFollowing) "Unfollow" else "Follow"
            }

            binding.progressBar.visibility = View.GONE
            binding.followingCount.text = followingList.size.toString()
            binding.followerCount.text = followerList.size.toString()

            Log.d(TAG, "Getting post count...")
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val videos = SupabaseManager.client
                        .from("videos")
                        .select()
                        .decodeList<VideoModel>()


                    val userVideos = videos.filter { it.uploader_id == profileUserId }
                    val sortedVideos = userVideos.sortedByDescending { it.created_at }
                    Log.d(TAG, "Found ${userVideos.size} videos for user")

                    launch(Dispatchers.Main) {
                        binding.postCount.text = userVideos.size.toString()
                        videoAdapter.submitList(sortedVideos)
                        Log.d(TAG, "Loaded ${userVideos.size} videos, sorted by date")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting videos: ${e.message}", e)
                    launch(Dispatchers.Main) {
                        binding.postCount.text = "0"
                    }
                }
            }
        }
        Log.d(TAG, "setUI completed")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: $requestCode, granted: ${grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED}")

        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openPhotoPicker()
        }
    }
}