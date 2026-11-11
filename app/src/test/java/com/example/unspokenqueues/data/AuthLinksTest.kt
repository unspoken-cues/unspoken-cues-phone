package com.example.unspokenqueues.data

import com.example.unspokenqueues.model.cardLink
import com.example.unspokenqueues.model.cardTokenFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class AuthLinksTest {

    @Test
    fun signInLink_isRecognised_withOrWithoutATrailingSlash() {
        assertTrue(isSignInLink(SIGN_IN_LINK))
        assertTrue(isSignInLink("unspokencues://sign-in/"))
    }

    @Test
    fun otherLinks_areNotTheSignInLink() {
        assertFalse(isSignInLink(null))
        assertFalse(isSignInLink(""))
        assertFalse(isSignInLink(PASSWORD_RESET_REDIRECT))
        assertFalse(isSignInLink(cardLink("0123456789abcdef0123456789abcdef")))
        assertFalse(isSignInLink("https://sign-in"))
        assertFalse(isSignInLink("unspokencues://sign-in/extra"))
        assertFalse(isSignInLink("unspokencues://user@sign-in"))
        assertFalse(isSignInLink("unspokencues://sign-in:80"))
        assertFalse(isSignInLink("not a link at all"))
    }

    @Test
    fun signInLink_isNotMistakenForACardOrAResetLink() {
        assertNull(cardTokenFrom(SIGN_IN_LINK))
        assertFalse(isPasswordRecoveryLink(SIGN_IN_LINK))
    }

    @Test
    fun confirmedRedirect_isAnHttpsPageOnGitHubPages() {
        val uri = URI(EMAIL_CONFIRMED_REDIRECT)
        assertEquals("https", uri.scheme)
        assertEquals("unspoken-cues.github.io", uri.host)
        assertTrue(uri.path.endsWith("/auth/confirmed/"))
    }
}
