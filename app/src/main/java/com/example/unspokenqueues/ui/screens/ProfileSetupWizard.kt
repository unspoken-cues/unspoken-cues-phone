package com.example.unspokenqueues.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.unspokenqueues.model.Profile
import com.example.unspokenqueues.model.canFinishProfileSetup
import com.example.unspokenqueues.model.hasDisplayName
import com.example.unspokenqueues.model.hasPreferenceOrBoundary
import com.example.unspokenqueues.ui.components.SectionCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// One question per step, in the order they are asked.
private enum class SetupStep(val title: String, val prompt: String) {
    NAME("What should people call you?", "This is the name on your card."),
    BIO("Add a short bio", "Optional. A line or two about you."),
    PREFERENCES("How do you like to connect?", "Preferences tell people what works well for you."),
    BOUNDARIES("What are your boundaries?", "Boundaries tell people what to avoid."),
    PHOTO("Add a photo", "Optional. It helps people recognise you."),
    PRIVACY("Who can see your profile?", "You can change this later from your profile."),
}

/**
 * First-run profile setup: a Back/Next wizard that asks for one thing per step, starting from
 * [initial]. The name step can't be passed without a display name, and Finish stays off until
 * [canFinishProfileSetup] is satisfied. A chosen photo is uploaded with [onUploadAvatar] when the
 * user finishes; [onFinish] then receives the completed profile to save. If either throws, the
 * wizard says so and stays put so the user can try again.
 */
@Composable
fun ProfileSetupWizard(
    initial: Profile,
    onUploadAvatar: suspend (Uri) -> String,
    onFinish: suspend (Profile) -> Unit,
) {
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    val step = SetupStep.entries[stepIndex]
    val isLastStep = stepIndex == SetupStep.entries.lastIndex
    // The answers are saveable because the photo step can get the activity recreated (the camera
    // app is in front), and that must not wipe what was entered on the earlier steps.
    var name by rememberSaveable { mutableStateOf(initial.displayName) }
    var bio by rememberSaveable { mutableStateOf(initial.bio) }
    var preferences by rememberSaveable { mutableStateOf(initial.preferences) }
    var boundaries by rememberSaveable { mutableStateOf(initial.boundaries) }
    var isPublic by rememberSaveable { mutableStateOf(initial.isPublic) }
    var avatarUrl by rememberSaveable { mutableStateOf(initial.avatarUrl) }
    // A photo chosen or taken here. It is only uploaded when the user taps Finish.
    var pickedPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    // Text typed into the current list step's field but not added to the list yet.
    var entry by rememberSaveable { mutableStateOf("") }
    var finishing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val draft = Profile(name, bio, preferences, boundaries, isPublic, avatarUrl)

    fun goTo(index: Int) {
        // Leaving a list step keeps what was typed there, so tapping Next instead of Add doesn't lose it.
        when (step) {
            SetupStep.PREFERENCES -> preferences = preferences.withEntry(entry)
            SetupStep.BOUNDARIES -> boundaries = boundaries.withEntry(entry)
            else -> Unit
        }
        entry = ""
        stepIndex = index
    }

    fun finish() {
        if (finishing) return
        finishing = true
        error = null
        scope.launch {
            var failure = "Couldn't upload your photo. Check your connection and try again, or go back and remove the photo."
            try {
                val url = pickedPhoto?.let { onUploadAvatar(it) } ?: avatarUrl
                // Keep the uploaded URL, so trying again after a failed save doesn't upload twice.
                avatarUrl = url
                pickedPhoto = null
                failure = "Couldn't save your profile. Check your connection and try again."
                onFinish(Profile(name.trim(), bio.trim(), preferences, boundaries, isPublic, url))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = failure
            } finally {
                finishing = false
            }
        }
    }

    BackHandler(enabled = stepIndex > 0 && !finishing) { goTo(stepIndex - 1) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Step ${stepIndex + 1} of ${SetupStep.entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (stepIndex + 1f) / SetupStep.entries.size },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Only the step scrolls; the progress bar and the Back/Next buttons stay put.
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(step.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    step.prompt,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when (step) {
                SetupStep.NAME -> OutlinedTextField(
                    name, { name = it }, label = { Text("Display name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(onNext = { if (hasDisplayName(draft)) goTo(stepIndex + 1) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                SetupStep.BIO -> OutlinedTextField(
                    bio, { if (it.length <= BIO_MAX_LENGTH) bio = it }, label = { Text("Bio") },
                    supportingText = { Text("${bio.length}/$BIO_MAX_LENGTH") },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                )
                SetupStep.PREFERENCES -> {
                    SectionCard { EditableList("e.g. Text over calls", preferences, { preferences = it }, entry) { entry = it } }
                    if (!hasPreferenceOrBoundary(draft)) {
                        Guidance("You need at least one preference or boundary to finish. Boundaries are the next step.")
                    }
                }
                SetupStep.BOUNDARIES -> {
                    SectionCard { EditableList("e.g. No photos", boundaries, { boundaries = it }, entry) { entry = it } }
                    if (!hasPreferenceOrBoundary(draft)) {
                        Guidance("You need at least one preference or boundary to finish. Add a boundary here, or go back and add a preference.")
                    }
                }
                SetupStep.PHOTO -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    AvatarPicker(
                        name = name,
                        image = pickedPhoto ?: avatarUrl,
                        enabled = !finishing,
                        onPicked = { pickedPhoto = it },
                        onRemove = {
                            pickedPhoto = null
                            avatarUrl = ""
                        },
                        onError = { error = it },
                    )
                }
                SetupStep.PRIVACY -> SectionCard {
                    ToggleRow("Public profile", "Anyone who scans your QR can view it", isPublic) { isPublic = it }
                }
            }

            if (isLastStep && !canFinishProfileSetup(draft)) {
                Guidance(
                    if (!hasDisplayName(draft)) {
                        "Go back and add your display name to finish."
                    } else {
                        "Go back and add at least one preference or boundary to finish."
                    },
                    isError = true,
                )
            }
            error?.let { Guidance(it, isError = true) }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (stepIndex > 0) {
                OutlinedButton({ goTo(stepIndex - 1) }, Modifier.weight(1f).height(52.dp), enabled = !finishing) {
                    Text("Back")
                }
            }
            if (isLastStep) {
                Button(::finish, Modifier.weight(1f).height(52.dp), enabled = canFinishProfileSetup(draft) && !finishing) {
                    Text(if (finishing) "Finishing…" else "Finish")
                }
            } else {
                Button(
                    onClick = { goTo(stepIndex + 1) },
                    modifier = Modifier.weight(1f).height(52.dp),
                    // The display name is the one answer every later step depends on.
                    enabled = step != SetupStep.NAME || hasDisplayName(draft),
                ) { Text("Next") }
            }
        }
    }
}

@Composable
private fun Guidance(text: String, isError: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
