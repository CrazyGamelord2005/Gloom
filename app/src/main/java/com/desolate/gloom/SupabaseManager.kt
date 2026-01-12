package com.desolate.gloom

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

object SupabaseManager {
    val client : SupabaseClient = createSupabaseClient(
        supabaseUrl = "Enter your url",
        supabaseKey = "Enter your key"
    ) {
        install(Auth) {
            alwaysAutoRefresh = true
            autoSaveToStorage = true
        }
        install(Postgrest)
        install(Storage)
    }
}