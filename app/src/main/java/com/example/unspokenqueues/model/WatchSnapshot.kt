package com.example.unspokenqueues.model

/** Minimal companion data. Never send account sessions, email, or backend credentials. */
data class WatchSnapshot(val active: Boolean, val status: String, val name: String,
                         val token: String, val isPublic: Boolean)

fun watchSnapshot(active: Boolean, status: CueStatus, name: String, token: String?,
                  isPublic: Boolean): WatchSnapshot {
    if (!active) return WatchSnapshot(false, "", "", "", false)
    val shareToken = token?.takeIf { isPublic && Regex("[0-9a-fA-F]{32}").matches(it) }
        ?.lowercase().orEmpty()
    return WatchSnapshot(true, status.name, name.take(80), shareToken, isPublic)
}

/** Preserve a same-account offline cache, but never leave a previous account shareable. */
fun watchSnapshotWhileLoading(currentAccount: String?, publishedAccount: String?): WatchSnapshot? =
    if (currentAccount != null && currentAccount != publishedAccount)
        WatchSnapshot(false, "", "", "", false)
    else null
