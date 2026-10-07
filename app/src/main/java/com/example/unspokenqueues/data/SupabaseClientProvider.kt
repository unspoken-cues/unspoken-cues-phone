package com.example.unspokenqueues.data

import com.example.unspokenqueues.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

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
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL is empty. Add it to local.properties (see the Supabase section)."
        }
        require(BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "SUPABASE_ANON_KEY is empty. Add it to local.properties (see the Supabase section)."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}
