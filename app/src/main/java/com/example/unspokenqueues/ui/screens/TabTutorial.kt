package com.example.unspokenqueues.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.unspokenqueues.Tab
import com.example.unspokenqueues.ui.components.Dot
import kotlinx.coroutines.launch

// What the tour says about each tab. A `when` rather than a field on Tab, so a tab can't be
// added without writing its card.
private val Tab.tourText: String
    get() = when (this) {
        Tab.CUE -> "Set your cue: green, yellow, red or purple. It tells people how to approach you, and it syncs to your watch."
        Tab.EVENTS -> "Host an event or join one with its code. Attendees see who is green or yellow; the host sees everyone."
        Tab.QR -> "Your card and its QR code. Have someone scan it, or scan theirs, and you swap cards."
        Tab.BINDER -> "The cards you've collected through S.W.A.P., each with that person's preferences and boundaries."
        Tab.PROFILE -> "Your name, bio, preferences and boundaries. Edit them here, and open Settings from the cog."
    }

/**
 * A full-screen tour of the app's tabs: one card per [Tab], swiped through or stepped with
 * Back/Next. "Skip" and, on the last card, "Done" both call [onFinish]. It keeps no record of
 * having been seen, so the caller can show it again whenever it likes.
 */
@Composable
fun TabTutorialScreen(onFinish: () -> Unit) {
    val tabs = Tab.entries
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val page = pager.currentPage
    val isLastPage = page == tabs.lastIndex

    // On the first card the system back button is left to whoever is showing the tour.
    BackHandler(enabled = page > 0) { scope.launch { pager.animateScrollToPage(page - 1) } }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // A fixed height so the title doesn't jump when Skip goes away on the last card.
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Quick tour",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (!isLastPage) TextButton(onFinish) { Text("Skip") }
        }

        HorizontalPager(pager, Modifier.weight(1f)) { index ->
            val tab = tabs[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(112.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(tab.icon),
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(tab.label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(
                    tab.tourText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().semantics { contentDescription = "Page ${page + 1} of ${tabs.size}" },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            tabs.indices.forEach { index ->
                Dot(
                    if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    8,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (page > 0) {
                OutlinedButton(
                    onClick = { scope.launch { pager.animateScrollToPage(page - 1) } },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Back") }
            }
            if (isLastPage) {
                Button(onFinish, Modifier.weight(1f).height(52.dp)) { Text("Done") }
            } else {
                Button(
                    onClick = { scope.launch { pager.animateScrollToPage(page + 1) } },
                    modifier = Modifier.weight(1f).height(52.dp),
                ) { Text("Next") }
            }
        }
    }
}
