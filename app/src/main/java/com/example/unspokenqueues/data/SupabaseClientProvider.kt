package com.example.unspokenqueues.data

import com.example.unspokenqueues.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Single, lazily-created [SupabaseClient] for the whole app.
 *
 * The URL and anon key come from [BuildConfig], which is populated at build time from
 * `local.properties` (untracked). The anon key is a public client key protected by Row Level
 * Security — it is safe to ship in the APK, but we still inject it per-environment rather than
 * hardcoding it in source.
 */
object SupabaseClientProvider {

    val client: SupabaseClient by lazy {
        val url = BuildConfig.SUPABASE_URL.trim()
        val key = BuildConfig.SUPABASE_ANON_KEY.trim()
        require(url.isNotBlank()) {
            "SUPABASE_URL is empty. Add it to local.properties (see the Supabase section)."
        }
        require(key.isNotBlank()) {
            "SUPABASE_ANON_KEY is empty. Add it to local.properties (see the Supabase section)."
        }
        // The client speaks HTTP to the REST API. A Postgres connection string
        // (postgresql://...:5432/...) is NOT a valid value and will hang until timeout.
        require(url.startsWith("https://") || url.startsWith("http://")) {
            "SUPABASE_URL must be the project REST URL like https://<ref>.supabase.co, " +
                "not a database connection string. Got: $url"
        }
        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key,
        ) {
            install(Auth)
            install(Postgrest)
            install(Storage)
        }
    }
}
