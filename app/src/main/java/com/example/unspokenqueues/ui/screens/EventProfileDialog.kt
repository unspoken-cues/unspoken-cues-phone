package com.example.unspokenqueues.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.EventProfile
import com.example.unspokenqueues.model.eventProfileOf
import com.example.unspokenqueues.ui.components.Dot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Edits the user's name, bio and cue for one event. Each blank field, and the "Main profile" cue,
 * falls back to [mainName], [mainBio] and [mainStatus]. [onSave] throws on failure, which keeps
 * the dialog open with the error; "Use main profile" saves an empty override.
 */
@Composable
fun EventProfileDialog(
    current: EventProfile,
    mainName: String,
    mainBio: String,
    mainStatus: CueStatus,
    onSave: suspend (EventProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(current.displayName.orEmpty()) }
    var bio by rememberSaveable { mutableStateOf(current.bio.orEmpty()) }
    var cue by rememberSaveable { mutableStateOf(current.status) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save(profile: EventProfile) {
        if (saving) return
        saving = true
        error = null
        scope.launch {
            try {
                onSave(profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Couldn't save your event profile. Check your connection and try again."
            } finally {
                saving = false
            }
        }
    }

    AlertDialog(
        // Closing mid-save would leave the user unsure which profile others see.
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Your profile at this event") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Only people at this event see these. Leave a field blank to use your main profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    name, { name = it }, label = { Text("Name at this event") }, singleLine = true, enabled = !saving,
                    placeholder = { Text(mainName) },
                    supportingText = { Text(if (mainName.isBlank()) "Blank uses your main name" else "Blank uses $mainName") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    bio, { if (it.length <= BIO_MAX_LENGTH) bio = it }, label = { Text("Bio at this event") }, enabled = !saving,
                    placeholder = { Text(mainBio) },
                    supportingText = { Text("${bio.length}/$BIO_MAX_LENGTH · blank uses your main bio") },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth(),
                )
                Text("Cue at this event", style = MaterialTheme.typography.titleSmall)
                Column(Modifier.selectableGroup()) {
                    (listOf<CueStatus?>(null) + CueStatus.entries).forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(option == cue, enabled = !saving, role = Role.RadioButton) { cue = option },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            RadioButton(option == cue, onClick = null, enabled = !saving)
                            Dot((option ?: mainStatus).color)
                            Text(if (option == null) "Main profile (${mainStatus.label})" else "${option.label} · ${option.meaning}")
                        }
                    }
                }
                TextButton(
                    { save(EventProfile()) },
                    Modifier.heightIn(min = 48.dp),
                    enabled = !saving,
                ) { Text("Use main profile") }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton({ save(eventProfileOf(name, bio, cue)) }, enabled = !saving) {
                if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Save")
            }
        },
        dismissButton = { TextButton(onDismiss, enabled = !saving) { Text("Cancel") } },
    )
}
