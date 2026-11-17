package com.example.unspokenqueues.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.ui.theme.UnspokenQueuesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditProfileScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun whenSavingFails_itSaysSo_andSaveCanBeTriedAgain() {
        val profile = Profile("Ana", "", listOf("Direct feedback"), emptyList(), isPublic = true)
        val saved = mutableListOf<Profile>()
        var failSave = true
        compose.setContent {
            UnspokenQueuesTheme {
                EditProfileScreen(
                    initial = profile,
                    email = null,
                    status = CueStatus.GREEN,
                    onUploadAvatar = { "https://x.test/uploaded.jpg" },
                    onUploadCardImage = { "https://x.test/card.jpg" },
                    onSave = {
                        if (failSave) error("offline")
                        saved += it
                    },
                    onCancel = {},
                )
            }
        }

        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("Couldn't save your profile. Check your connection and try again.").assertIsDisplayed()
        assertEquals(emptyList<Profile>(), saved)

        failSave = false
        compose.onNodeWithText("Save").assertIsEnabled().performClick()
        compose.waitForIdle()

        assertEquals(listOf(profile), saved)
        compose.onNodeWithText("Couldn't save your profile. Check your connection and try again.").assertDoesNotExist()
    }
}
