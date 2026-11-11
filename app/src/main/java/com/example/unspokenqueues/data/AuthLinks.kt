package com.example.unspokenqueues.data

import java.net.URI

/**
 * Where Supabase sends someone after they open the confirmation email: a small page in this repo's
 * `docs/` folder, served by GitHub Pages, that says the email is confirmed and links back into the
 * app. It must be listed under Authentication → URL Configuration → Redirect URLs.
 */
const val EMAIL_CONFIRMED_REDIRECT = "https://unspoken-cues.github.io/unspoken-cues-phone/auth/confirmed/"

/** The link that page opens: it brings the app up on the sign-in form. It carries no token. */
const val SIGN_IN_LINK = "unspokencues://sign-in"

/** Whether [link] is exactly [SIGN_IN_LINK] (a trailing slash allowed), so card links keep their routing. */
fun isSignInLink(link: String?): Boolean = try {
    val uri = URI(link ?: "")
    uri.scheme == "unspokencues" && uri.host == "sign-in" &&
        uri.userInfo == null && uri.port == -1 && uri.path.orEmpty() in listOf("", "/")
} catch (_: Exception) {
    false
}
