package com.example.unspokenqueues.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import com.example.unspokenqueues.model.ReportReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// The reports table's limit on a note.
const val REPORT_NOTE_MAX_LENGTH = 1000

/**
 * Reports [name] to the team. [onSend] files the report and throws if that failed, which keeps
 * the dialog open with the error. Once it has gone, [onSent] receives whether "Also block" was
 * ticked; blocking is the caller's job, so a failed block is reported the same way as anywhere else.
 */
@Composable
fun ReportDialog(
    name: String,
    onSend: suspend (reason: ReportReason, note: String) -> Unit,
    onSent: (alsoBlock: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var reason by rememberSaveable { mutableStateOf<ReportReason?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var alsoBlock by rememberSaveable { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun send() {
        val chosen = reason ?: return
        if (sending) return
        sending = true
        error = null
        scope.launch {
            try {
                onSend(chosen, note)
                onSent(alsoBlock)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Couldn't send your report. Check your connection and try again."
            } finally {
                sending = false
            }
        }
    }

    AlertDialog(
        // Closing mid-send would leave the user unsure whether the report went.
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Report $name") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("What happened? Only the Unspoken Cues team sees reports.")
                Column(Modifier.selectableGroup()) {
                    ReportReason.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .selectable(option == reason, enabled = !sending, role = Role.RadioButton) { reason = option },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(option == reason, onClick = null, enabled = !sending)
                            Text(option.label, Modifier.padding(start = 12.dp))
                        }
                    }
                }
                OutlinedTextField(
                    note, { note = it.take(REPORT_NOTE_MAX_LENGTH) },
                    label = { Text("Anything else? (optional)") },
                    supportingText = { Text("${note.length}/$REPORT_NOTE_MAX_LENGTH") },
                    enabled = !sending,
                    minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(alsoBlock, enabled = !sending, role = Role.Checkbox) { alsoBlock = it },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(alsoBlock, onCheckedChange = null, enabled = !sending)
                    Text("Also block $name", Modifier.padding(start = 12.dp))
                }
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(::send, enabled = reason != null && !sending) {
                if (sending) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Report")
            }
        },
        dismissButton = { TextButton(onDismiss, enabled = !sending) { Text("Cancel") } },
    )
}
