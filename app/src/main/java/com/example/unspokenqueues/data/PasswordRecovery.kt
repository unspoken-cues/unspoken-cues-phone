package com.example.unspokenqueues.data

import java.net.URI

const val PASSWORD_RESET_REDIRECT = "unspokencues://password-reset"

/** Only this dedicated callback may enter recovery; card links keep their existing routing. */
fun isPasswordRecoveryLink(link: String?): Boolean = try {
    val uri = URI(link ?: "")
    uri.scheme == "unspokencues" && uri.host == "password-reset" &&
        uri.userInfo == null && uri.port == -1 && uri.path.orEmpty() in listOf("", "/")
} catch (_: Exception) {
    false
}
