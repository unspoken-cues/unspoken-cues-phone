package com.example.unspokenqueues.ui.screens

import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.unspokenqueues.data.authFailureMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun ForgotPasswordScreen(onSendLink: suspend (String) -> Unit, onBack: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun send() {
        if (loading || sent) return
        error = null
        if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            error = "Enter a valid email address."
            return
        }
        loading = true
        scope.launch {
            try {
                onSendLink(email.trim())
                sent = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = authFailureMessage(e)
            } finally {
                loading = false
            }
        }
    }

    RecoveryColumn {
        TextButton(onBack, enabled = !loading) { Text("Back to sign in") }
        RecoveryHeading(if (sent) "Check your email" else "Reset your password")
        Text(
            if (sent) "If an account exists for ${email.trim()}, you'll receive a password reset link. Open it on this device to choose a new password."
            else "Enter the email for your account. We'll send you a link to choose a new password.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        if (!sent) {
            OutlinedTextField(
                email, { email = it }, label = { Text("Email") }, singleLine = true,
                enabled = !loading, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { send() }),
            )
            RecoveryError(error)
            Spacer(Modifier.height(24.dp))
            RecoveryButton("Send reset link", loading, ::send)
        }
    }
}

/** Session validation finishes before the password fields become available. */
@Composable
fun NewPasswordScreen(
    ready: Boolean,
    processing: Boolean,
    linkError: String?,
    onUpdatePassword: suspend (String) -> Unit,
    onCancel: () -> Unit,
    onContinue: () -> Unit,
) {
    // Passwords must never be saved into Android's saved-instance state.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var completed by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun save() {
        if (!ready || loading || completed) return
        error = when {
            password.isBlank() -> "Enter a new password."
            password != confirm -> "Those passwords don't match."
            else -> null
        }
        if (error != null) return
        loading = true
        scope.launch {
            try {
                onUpdatePassword(password)
                password = ""
                confirm = ""
                completed = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = authFailureMessage(e)
            } finally {
                loading = false
            }
        }
    }

    BackHandler {
        if (!loading && !processing) {
            if (completed) onContinue() else onCancel()
        }
    }

    RecoveryColumn {
        if (!completed) TextButton(onCancel, enabled = !loading && !processing) { Text("Back to sign in") }
        RecoveryHeading(if (completed) "Password updated" else "Choose a new password")
        when {
            completed -> {
                Text("Your new password is saved. You can use it the next time you sign in.")
                Spacer(Modifier.height(24.dp))
                RecoveryButton("Continue", false, onContinue)
            }
            processing -> {
                Text("Checking your reset link…")
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator()
            }
            !ready -> RecoveryError(linkError ?: "This reset link is no longer available. Go back to sign in and request a new link.")
            else -> {
                Text("Enter and confirm your new password.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                OutlinedTextField(
                    password, { password = it }, label = { Text("New password") }, singleLine = true,
                    enabled = !loading, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    confirm, { confirm = it }, label = { Text("Confirm password") }, singleLine = true,
                    enabled = !loading, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { save() }),
                )
                RecoveryError(error)
                Spacer(Modifier.height(24.dp))
                RecoveryButton("Save new password", loading, ::save)
            }
        }
    }
}

@Composable
private fun RecoveryColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), content = content)
}

@Composable
private fun RecoveryHeading(title: String) {
    Spacer(Modifier.height(24.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun RecoveryError(error: String?) {
    error?.let {
        Spacer(Modifier.height(12.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RecoveryButton(label: String, loading: Boolean, onClick: () -> Unit) {
    Button(onClick, enabled = !loading, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
        else Text(label)
    }
}
