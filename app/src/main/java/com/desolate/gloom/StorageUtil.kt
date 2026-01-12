package com.desolate.gloom

import android.content.Context
import android.net.Uri
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage

object StorageUtil {

    private const val TAG = "StorageUtil"
    suspend fun uploadVideoFile(context: Context, supabase: SupabaseClient, fileUri: Uri, fileName: String): String {
        Log.d(TAG, "=== STORAGE UPLOAD START ===")
        Log.d(TAG, "File URI: $fileUri")
        Log.d(TAG, "File name: $fileName")

        try {
            val bucket = supabase.storage.from("videos")
            Log.d(TAG, "Bucket obtained")

            Log.d(TAG, "Opening input stream...")
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: throw IllegalArgumentException("Unable to open input stream for URI: $fileUri")

            Log.d(TAG, "Reading file bytes...")
            val bytes = inputStream.use { it.readBytes() }

            Log.d(TAG, "File size: ${bytes.size} bytes")

            if (bytes.isEmpty()) {
                throw IllegalArgumentException("File is empty")
            }

            Log.d(TAG, "Uploading with v3.2.4 API...")
            val result = bucket.upload(
                path = fileName,
                data = bytes,
            ) {
                upsert = true
            }

            Log.d(TAG, "Upload result: $result")

            val publicUrl = bucket.publicUrl(fileName)
            Log.d(TAG, "Public URL: $publicUrl")
            Log.d(TAG, "=== STORAGE UPLOAD SUCCESS ===")

            return publicUrl

        } catch (e: Exception) {
            Log.e(TAG, "STORAGE UPLOAD FAILED: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteFile(supabase: SupabaseClient, fileName: String) {
        try {
            supabase.storage.from("videos").delete(fileName)
            Log.d(TAG, "File deleted: $fileName")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file: $fileName", e)
        }
    }
}