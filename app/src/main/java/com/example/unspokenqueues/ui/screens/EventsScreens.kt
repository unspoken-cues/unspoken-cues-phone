package com.example.unspokenqueues.ui.screens

import android.content.ClipData
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.unspokenqueues.R
import com.example.unspokenqueues.data.EventCoverRepository
import com.example.unspokenqueues.data.EventDraft
import com.example.unspokenqueues.data.EventRepository
import com.example.unspokenqueues.data.joinFailureMessage
import com.example.unspokenqueues.model.Attendee
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.EventPhase
import com.example.unspokenqueues.model.JOIN_CODE_MAX_LENGTH
import com.example.unspokenqueues.model.MAX_TAGS_PER_EVENT
import com.example.unspokenqueues.model.Rsvp
import com.example.unspokenqueues.model.dayHeader
import com.example.unspokenqueues.model.filterByTags
import com.example.unspokenqueues.model.formatDay
import com.example.unspokenqueues.model.formatTimeRange
import com.example.unspokenqueues.model.goingText
import com.example.unspokenqueues.model.isValidJoinCode
import com.example.unspokenqueues.model.normalizeTag
import com.example.unspokenqueues.model.normalizeTags
import com.example.unspokenqueues.model.pastEvents
import com.example.unspokenqueues.model.phase
import com.example.unspokenqueues.model.tagLabel
import com.example.unspokenqueues.model.tagsOf
import com.example.unspokenqueues.model.upcomingEvents
import com.example.unspokenqueues.model.visibleAttendees
import com.example.unspokenqueues.ui.components.Avatar
import com.example.unspokenqueues.ui.components.AttendeeListSkeleton
import com.example.unspokenqueues.ui.components.Chip
import com.example.unspokenqueues.ui.components.EventCover
import com.example.unspokenqueues.ui.components.EventFeatureCard
import com.example.unspokenqueues.ui.components.EventListSkeleton
import com.example.unspokenqueues.ui.components.EventRowItem
import com.example.unspokenqueues.ui.components.FullScreenQr
import com.example.unspokenqueues.ui.components.Halo
import com.example.unspokenqueues.ui.components.MetaLine
import com.example.unspokenqueues.ui.components.OutlinedButton
import com.example.unspokenqueues.ui.components.QrScanner
import com.example.unspokenqueues.ui.components.SectionCard
import com.example.unspokenqueues.ui.components.SegmentedTabs
import com.example.unspokenqueues.ui.components.TagFilterRow
import com.example.unspokenqueues.ui.components.TagPill
import com.example.unspokenqueues.ui.components.eventBadge
import com.example.unspokenqueues.ui.theme.EaseOutQuart
import com.example.unspokenqueues.ui.theme.MotionMedium
import com.example.unspokenqueues.ui.theme.MotionShort
import io.github.jan.supabase.postgrest.exception.PostgrestRestException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

// ---------- Events tab ----------

private val EVENT_TABS = listOf("Upcoming", "Discover", "Past")

/**
 * The Events tab, in three views: what is coming up (live events first, grouped by day), a feed of
 * public events to RSVP to, and what is over. Each view can be narrowed by tag. Hosting is the
 * floating button; joining with a host's code (typed or scanned) is the link beside the title.
 * [onOpen] receives an event the user tapped or just joined.
 */
@Composable
fun EventsScreen(
    repo: EventRepository,
    userId: String,
    onHost: () -> Unit,
    onOpen: (Event) -> Unit,
) {
    // Null until the first load answers.
    var mine by remember { mutableStateOf<List<Event>?>(null) }
    var discover by remember { mutableStateOf<List<Event>?>(null) }
    var rsvps by remember { mutableStateOf<Map<String, Rsvp>>(emptyMap()) }
    var mineFailed by remember { mutableStateOf(false) }
    var discoverFailed by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedTags by remember { mutableStateOf(emptySet<String>()) }
    var joinOpen by rememberSaveable { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    var code by rememberSaveable { mutableStateOf("") }
    var joining by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }
    var rsvpError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }
    val now = remember(mine, discover) { Instant.now() }

    LaunchedEffect(userId) {
        try {
            mine = repo.myEvents(userId)
            rsvps = repo.myRsvps(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mineFailed = true
        }
    }
    LaunchedEffect(userId) {
        try {
            discover = repo.discoverEvents()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            discoverFailed = true
        }
    }

    fun join(text: String) {
        if (joining) return
        joinError = null
        joining = true
        scope.launch {
            try {
                onOpen(repo.joinByCode(text))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                joinError = joinFailureMessage((e as? PostgrestRestException)?.code)
            } finally {
                joining = false
            }
        }
    }

    // One-tap RSVP from the Discover feed.
    fun quickRsvp(event: Event) {
        rsvpError = null
        scope.launch {
            try {
                repo.rsvp(event.id, userId, Rsvp.GOING)
                rsvps = rsvps + (event.id to Rsvp.GOING)
                discover = discover?.map { if (it.id == event.id) it.copy(goingCount = it.goingCount + 1) else it }
                // The event now belongs on the user's own list too.
                mine = (mine.orEmpty() + event).distinctBy { it.id }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                rsvpError = "Couldn't RSVP. Check your connection and try again."
            }
        }
    }

    val upcoming = mine?.let { upcomingEvents(it, now) }
    val past = mine?.let { pastEvents(it, now) }
    val feed = discover?.let { list -> upcomingEvents(list, now) }
    val currentList = when (tab) {
        0 -> upcoming
        1 -> feed
        else -> past
    }
    val tags = currentList?.let { tagsOf(it) }.orEmpty()
    // A tag that no longer exists in the shown list can't stay selected.
    val activeTags = selectedTags.intersect(tags.toSet())

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Events",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                TextButton({ joinOpen = true }) { Text("Join with code") }
            }
            SegmentedTabs(EVENT_TABS, tab, onSelect = {
                tab = it
                selectedTags = emptySet()
            })
            TagFilterRow(tags, activeTags, onToggle = { tag ->
                selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
            })
            rsvpError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            // Placeholders while loading, then the list fades in over them.
            val failed = if (tab == 1) discoverFailed else mineFailed
            AnimatedContent(
                targetState = Triple(tab, currentList == null, failed),
                transitionSpec = { fadeIn(tween(MotionMedium, easing = EaseOutQuart)) togetherWith fadeOut(tween(MotionShort)) },
                label = "eventsTab",
            ) { (shownTab, loading, loadFailed) ->
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    when {
                        loading && loadFailed -> Text(
                            "Couldn't load events. Check your connection, then open this tab again.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        loading -> EventListSkeleton()
                        shownTab == 0 -> UpcomingList(
                            events = filterByTags(upcoming.orEmpty(), activeTags),
                            hasAny = upcoming.orEmpty().isNotEmpty(),
                            now = now, zone = zone, userId = userId, rsvps = rsvps,
                            onBrowse = { tab = 1 }, onOpen = onOpen,
                        )
                        shownTab == 1 -> DiscoverList(
                            events = filterByTags(feed.orEmpty(), activeTags),
                            now = now, zone = zone, userId = userId, rsvps = rsvps,
                            onRsvp = ::quickRsvp, onOpen = onOpen,
                        )
                        else -> PastList(
                            events = filterByTags(past.orEmpty(), activeTags),
                            now = now, zone = zone, userId = userId, rsvps = rsvps, onOpen = onOpen,
                        )
                    }
                }
            }
        }

        ExtendedFloatingActionButton(
            onClick = onHost,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(painterResource(R.drawable.ic_add), contentDescription = null) },
            text = { Text("Host event", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) },
        )
    }

    // Hidden while the scanner is up so the two dialogs don't stack.
    if (joinOpen && !scanning) {
        JoinEventDialog(
            code = code,
            onCodeChange = {
                code = it.trim().uppercase().take(JOIN_CODE_MAX_LENGTH)
                joinError = null
            },
            joining = joining,
            error = joinError,
            onJoin = { join(code) },
            onScan = { scanning = true },
            onDismiss = {
                joinOpen = false
                code = ""
                joinError = null
            },
        )
    }
    if (scanning) {
        QrScanner(
            onCode = { scanned ->
                scanning = false
                // The camera reads any QR code, e.g. someone's card instead of the event's.
                if (isValidJoinCode(scanned)) {
                    code = scanned.trim().uppercase()
                    join(scanned)
                } else {
                    joinError = "That QR code isn't an event code."
                }
            },
            onDismiss = { scanning = false },
            hint = "Point the camera at the event's QR code",
        )
    }
}

@Composable
private fun UpcomingList(
    events: List<Event>,
    hasAny: Boolean,
    now: Instant,
    zone: ZoneId,
    userId: String,
    rsvps: Map<String, Rsvp>,
    onBrowse: () -> Unit,
    onOpen: (Event) -> Unit,
) {
    if (events.isEmpty()) {
        EmptyState(
            if (hasAny) "Nothing matches those tags." else "Nothing coming up. Browse Discover for events to RSVP to, or host your own.",
            action = if (hasAny) null else "Browse Discover" to onBrowse,
        )
        return
    }
    // Live events sit first under their own heading, then the rest by day.
    val groups = events.groupBy { if (it.phase(now) == EventPhase.LIVE) "Happening now" else dayHeader(it, now, zone) }
    groups.forEach { (heading, inGroup) ->
        Column {
            Text(
                heading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            inGroup.forEachIndexed { i, event ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                val hosting = event.hostId == userId
                EventRowItem(
                    event, now, zone,
                    badge = eventBadge(event, hosting, rsvps[event.id], now),
                    onClick = { onOpen(event) },
                )
            }
        }
    }
}

@Composable
private fun DiscoverList(
    events: List<Event>,
    now: Instant,
    zone: ZoneId,
    userId: String,
    rsvps: Map<String, Rsvp>,
    onRsvp: (Event) -> Unit,
    onOpen: (Event) -> Unit,
) {
    if (events.isEmpty()) {
        EmptyState("No public events yet. Host one and switch on Show in Discover so people can find it.")
        return
    }
    events.forEach { event ->
        val hosting = event.hostId == userId
        val answer = rsvps[event.id]
        EventFeatureCard(
            event, now, zone, answer, hosting,
            onRsvp = if (hosting || answer != null) null else { { onRsvp(event) } },
            onClick = { onOpen(event) },
        )
    }
}

@Composable
private fun PastList(
    events: List<Event>,
    now: Instant,
    zone: ZoneId,
    userId: String,
    rsvps: Map<String, Rsvp>,
    onOpen: (Event) -> Unit,
) {
    if (events.isEmpty()) {
        EmptyState("No past events yet. Events you hosted or went to land here once they finish.")
        return
    }
    Column {
        events.forEachIndexed { i, event ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            EventRowItem(
                event, now, zone,
                badge = if (event.hostId == userId) "Hosted" else rsvps[event.id]?.let { "Went" } ?: "Ended",
                dimmed = true,
                onClick = { onOpen(event) },
            )
        }
    }
}

@Composable
private fun EmptyState(text: String, action: Pair<String, () -> Unit>? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) OutlinedButton(action.second, Modifier.height(48.dp)) { Text(action.first) }
    }
}

// The way into someone else's event: type the host's code, or scan the QR code they are showing.
@Composable
private fun JoinEventDialog(
    code: String,
    onCodeChange: (String) -> Unit,
    joining: Boolean,
    error: String?,
    onJoin: () -> Unit,
    onScan: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canJoin = isValidJoinCode(code) && !joining
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join an event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter the code from the host, or scan the QR code they're showing.")
                OutlinedTextField(
                    code, onCodeChange, label = { Text("Event code") }, singleLine = true,
                    enabled = !joining,
                    isError = error != null,
                    supportingText = if (error != null) { { Text(error) } } else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { if (canJoin) onJoin() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onScan, Modifier.fillMaxWidth(), enabled = !joining) { Text("Scan QR code") }
            }
        },
        confirmButton = { TextButton(onJoin, enabled = canJoin) { Text(if (joining) "Joining…" else "Join") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

// ---------- Host an event ----------

private const val EVENT_NAME_MAX_LENGTH = 60
private const val EVENT_DETAILS_MAX_LENGTH = 400
private const val EVENT_LOCATION_MAX_LENGTH = 80
private val SUGGESTED_TAGS = listOf("social", "networking", "games", "music", "food", "sports", "study", "outdoors", "arts", "tech")

private enum class Picking { NONE, DATE, START, END }

private fun LocalDateTime.toMillis(zone: ZoneId): Long = atZone(zone).toInstant().toEpochMilli()
private fun Long.toLocal(zone: ZoneId): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()

/** The next whole hour after [now], then one more: a sensible default start for a new event. */
private fun defaultStart(now: LocalDateTime): LocalDateTime =
    now.withMinute(0).withSecond(0).withNano(0).plusHours(2)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateEventScreen(
    repo: EventRepository,
    coverRepo: EventCoverRepository,
    userId: String,
    hostName: String,
    onCreated: (Event) -> Unit,
    onCancel: () -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var details by rememberSaveable { mutableStateOf("") }
    var location by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf(listOf<String>()) }
    var tagInput by rememberSaveable { mutableStateOf("") }
    var isPublic by rememberSaveable { mutableStateOf(false) }
    var startMillis by rememberSaveable { mutableStateOf(defaultStart(LocalDateTime.now(zone)).toMillis(zone)) }
    var endMillis by rememberSaveable { mutableStateOf(defaultStart(LocalDateTime.now(zone)).plusHours(2).toMillis(zone)) }
    var coverUrl by rememberSaveable { mutableStateOf("") }
    var coverPreview by rememberSaveable { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(Picking.NONE) }
    var creating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val start = startMillis.toLocal(zone)
    val end = endMillis.toLocal(zone)
    val endsAfterStart = end.isAfter(start)
    val canCreate = name.isNotBlank() && endsAfterStart && !creating && !uploading

    // Changing the start moves the end with it, so the length of the event stays what the host chose.
    fun moveStart(to: LocalDateTime) {
        val length = java.time.Duration.between(start, end)
        startMillis = to.toMillis(zone)
        endMillis = to.plus(length).toMillis(zone)
    }

    val pickCover = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        coverPreview = uri.toString()
        uploading = true
        error = null
        scope.launch {
            try {
                coverUrl = coverRepo.upload(context.contentResolver, userId, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                coverPreview = null
                error = "Couldn't upload that image. Try another, or create the event without one."
            } finally {
                uploading = false
            }
        }
    }

    fun create() {
        if (!canCreate) return
        creating = true
        error = null
        scope.launch {
            try {
                onCreated(
                    repo.createEvent(
                        userId,
                        EventDraft(
                            name = name,
                            details = details,
                            startsAt = start.atZone(zone).toInstant(),
                            endsAt = end.atZone(zone).toInstant(),
                            location = location,
                            coverUrl = coverUrl,
                            tags = normalizeTags(tags),
                            isPublic = isPublic,
                            hostName = hostName,
                        ),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = "Couldn't create the event. Check your connection and try again."
            } finally {
                creating = false
            }
        }
    }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onCancel) { Text("Cancel") }
            Spacer(Modifier.weight(1f))
            Text("Host an event", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = ::create, enabled = canCreate) { Text(if (creating) "Creating…" else "Create") }
        }

        // Cover
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clickable(enabled = !creating) {
                    pickCover.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            contentAlignment = Alignment.Center,
        ) {
            if (coverPreview != null) {
                AsyncImage(
                    model = coverPreview,
                    contentDescription = "Cover image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        painterResource(R.drawable.ic_image), contentDescription = null,
                        Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        "Add a cover image",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            if (uploading) {
                Text(
                    "Uploading…",
                    Modifier.align(Alignment.BottomStart).padding(12.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }

        SectionCard(title = "Event") {
            OutlinedTextField(
                name, { if (it.length <= EVENT_NAME_MAX_LENGTH) name = it }, label = { Text("Event name") },
                singleLine = true, enabled = !creating, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                details, { if (it.length <= EVENT_DETAILS_MAX_LENGTH) details = it }, label = { Text("About") },
                placeholder = { Text("What should people know?") },
                supportingText = { Text("${details.length}/$EVENT_DETAILS_MAX_LENGTH") },
                minLines = 3, maxLines = 6, enabled = !creating, modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = "When and where") {
            PickRow("Date", start.toLocalDate().let { formatPickedDate(it) }) { picking = Picking.DATE }
            PickRow("Starts", formatPickedTime(start.toLocalTime())) { picking = Picking.START }
            PickRow("Ends", formatPickedTime(end.toLocalTime())) { picking = Picking.END }
            if (!endsAfterStart) {
                Text("The end has to be after the start.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedTextField(
                location, { if (it.length <= EVENT_LOCATION_MAX_LENGTH) location = it }, label = { Text("Location") },
                placeholder = { Text("e.g. CASE 138") },
                singleLine = true, enabled = !creating, modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = "Tags") {
            Text(
                "Tags help people find and filter events. Up to $MAX_TAGS_PER_EVENT.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                (SUGGESTED_TAGS + tags.filter { it !in SUGGESTED_TAGS }).forEach { tag ->
                    TagPill(tagLabel(tag), tag in tags, onToggle = {
                        tags = if (tag in tags) tags - tag else if (tags.size < MAX_TAGS_PER_EVENT) tags + tag else tags
                    })
                }
            }
            OutlinedTextField(
                tagInput, { if (it.length <= 20) tagInput = it }, label = { Text("Add your own tag") },
                singleLine = true, enabled = !creating,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val tag = normalizeTag(tagInput)
                    if (tag.isNotEmpty() && tag !in tags && tags.size < MAX_TAGS_PER_EVENT) tags = tags + tag
                    tagInput = ""
                }),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionCard(title = "Who can find it") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Show in Discover", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (isPublic) "Anyone signed in can find this event and RSVP."
                        else "Only people with the code or QR can join.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(isPublic, { isPublic = it }, enabled = !creating)
            }
        }

        error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
        Text(
            "You'll get a code and a QR code either way. As the host you see every attendee's status.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    when (picking) {
        Picking.NONE -> Unit
        Picking.DATE -> {
            val state = rememberDatePickerState(
                initialSelectedDateMillis = start.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { picking = Picking.NONE },
                confirmButton = {
                    TextButton({
                        state.selectedDateMillis?.let { utc ->
                            val date = Instant.ofEpochMilli(utc).atZone(ZoneOffset.UTC).toLocalDate()
                            moveStart(LocalDateTime.of(date, start.toLocalTime()))
                        }
                        picking = Picking.NONE
                    }) { Text("OK") }
                },
                dismissButton = { TextButton({ picking = Picking.NONE }) { Text("Cancel") } },
            ) { DatePicker(state) }
        }
        Picking.START, Picking.END -> {
            val isStart = picking == Picking.START
            val initial = if (isStart) start.toLocalTime() else end.toLocalTime()
            val state = rememberTimePickerState(initial.hour, initial.minute, is24Hour = false)
            AlertDialog(
                onDismissRequest = { picking = Picking.NONE },
                title = { Text(if (isStart) "Start time" else "End time") },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton({
                        val picked = LocalTime.of(state.hour, state.minute)
                        if (isStart) {
                            moveStart(LocalDateTime.of(start.toLocalDate(), picked))
                        } else {
                            // An end time earlier than the start means the next morning.
                            var newEnd = LocalDateTime.of(start.toLocalDate(), picked)
                            if (!newEnd.isAfter(start)) newEnd = newEnd.plusDays(1)
                            endMillis = newEnd.toMillis(zone)
                        }
                        picking = Picking.NONE
                    }) { Text("OK") }
                },
                dismissButton = { TextButton({ picking = Picking.NONE }) { Text("Cancel") } },
            )
        }
    }
}

private fun formatPickedDate(date: LocalDate): String =
    java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", java.util.Locale.US).format(date)

private fun formatPickedTime(time: LocalTime): String =
    java.time.format.DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.US).format(time)

// A tappable line showing a label and its current value.
@Composable
private fun PickRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- Event page ----------

/**
 * One event: cover, name, host, when and where, what it is about, the RSVP, and who is going. For
 * the host also the join QR and the way to end it. Anyone can take the event off their list: an
 * attendee leaves it, the host deletes it for everyone. [status] is the viewer's own cue, which
 * colours the join QR's frame. [onEventChange] receives the event once it has changed (ended, or
 * a fresh going count); [onRemoved] is called once the viewer has left or deleted it.
 */
@Composable
fun EventDetailScreen(
    repo: EventRepository,
    userId: String,
    status: CueStatus,
    event: Event,
    onEventChange: (Event) -> Unit,
    onRemoved: () -> Unit,
    onBack: () -> Unit,
) {
    val isHost = event.hostId == userId
    val zone = remember { ZoneId.systemDefault() }
    val now = remember(event) { Instant.now() }
    val phase = event.phase(now)
    // Null until the first load answers.
    var attendees by remember { mutableStateOf<List<Attendee>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    // Bumped to load the attendee list again.
    var reload by remember { mutableIntStateOf(0) }
    var rsvp by remember { mutableStateOf<Rsvp?>(null) }
    var showingQr by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    var confirmingEnd by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }
    // True while ending, leaving, deleting or answering is in flight; only one runs at a time.
    var working by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // Compose's clipboard rather than the system service, so a test can swap in a fake one.
    val clipboard = LocalClipboard.current

    LaunchedEffect(event.id, reload) {
        try {
            attendees = repo.eventAttendees(event.id)
            loadFailed = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loadFailed = true
        }
    }
    // The viewer's own answer, and a fresh going count (the one on the list may be old).
    LaunchedEffect(event.id) {
        try {
            rsvp = repo.myRsvps(userId)[event.id]
            repo.event(event.id)?.let { if (it.goingCount != event.goingCount) onEventChange(event.copy(goingCount = it.goingCount)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The page works without either; the RSVP buttons just start unselected.
        }
    }

    fun perform(failure: String, action: suspend () -> Unit) {
        if (working) return
        working = true
        actionError = null
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                actionError = failure
            } finally {
                working = false
            }
        }
    }

    fun answer(choice: Rsvp) = perform("Couldn't save your RSVP. Check your connection and try again.") {
        repo.rsvp(event.id, userId, choice)
        val wasGoing = rsvp == Rsvp.GOING
        rsvp = choice
        val delta = (if (choice == Rsvp.GOING) 1 else 0) - (if (wasGoing) 1 else 0)
        if (delta != 0) onEventChange(event.copy(goingCount = (event.goingCount + delta).coerceAtLeast(0)))
        reload++
    }

    fun end() = perform("Couldn't end the event. Check your connection and try again.") {
        repo.endEvent(event.id)
        onEventChange(event.copy(active = false))
    }

    fun remove() = perform(
        if (isHost) "Couldn't delete the event. Check your connection and try again."
        else "Couldn't leave the event. Check your connection and try again.",
    ) {
        if (isHost) repo.deleteEvent(event.id) else repo.leaveEvent(event.id, userId)
        onRemoved()
    }

    ScreenColumn {
        Box {
            EventCover(
                event.id, event.name, event.coverUrl,
                Modifier.fillMaxWidth().aspectRatio(16f / 10f),
                RoundedCornerShape(28.dp),
            )
            IconButton(
                onBack,
                Modifier.align(Alignment.TopStart).padding(8.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape),
            ) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back")
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (event.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    event.tags.forEach { Chip(tagLabel(it)) }
                }
            }
            Text(event.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (event.hostName.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Hosted by", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(event.hostName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
            if (phase == EventPhase.LIVE) Chip("Happening now")
            if (!event.active) Chip("Ended")
        }

        SectionCard {
            val day = formatDay(event, zone)
            if (day.isEmpty()) {
                MetaLine(R.drawable.ic_schedule, "Time to be announced")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(day, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        formatTimeRange(event, zone),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (event.location.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                MetaLine(R.drawable.ic_place, event.location)
            }
            goingText(event.goingCount).takeIf { it.isNotEmpty() }?.let {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                MetaLine(R.drawable.ic_people, it)
            }
        }

        if (event.details.isNotBlank()) {
            SectionCard(title = "About") {
                Text(event.details, style = MaterialTheme.typography.bodyLarge)
            }
        }

        if (!event.active) {
            Text(
                "This event has ended. Nobody else can join.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (!isHost && phase != EventPhase.PAST) {
            SectionCard(title = "Your RSVP") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Rsvp.entries.forEach { choice ->
                        val chosen = rsvp == choice
                        if (chosen) {
                            Button({ answer(choice) }, Modifier.weight(1f).height(52.dp), enabled = !working) {
                                Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(choice.label)
                            }
                        } else {
                            OutlinedButton({ answer(choice) }, Modifier.weight(1f).height(52.dp), enabled = !working) {
                                Text(choice.label)
                            }
                        }
                    }
                }
                if (rsvp != null) {
                    Text(
                        if (rsvp == Rsvp.GOING) "You're going. The host can see you on the list."
                        else "You said maybe. Switch to Going when you're sure.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (event.active && isHost) {
            SectionCard(title = "Join code") {
                Text(event.joinCode, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Event join code", event.joinCode)))
                                copied = true
                            }
                        },
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) { CopyLabel(copied, "Copy code") }
                    Button({ showingQr = true }, Modifier.weight(1f).height(52.dp)) { Text("Show join QR") }
                }
            }
        }

        // The rule lives in visibleAttendees: the host sees everyone, others only green and yellow.
        val visible = attendees?.let { visibleAttendees(userId, event.hostId, it) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (visible == null) "Attendees" else "Attendees · ${visible.size}",
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
            )
            TextButton({ reload++ }) { Text("Refresh") }
        }
        if (loadFailed) {
            Text(
                "Couldn't load attendees. Check your connection, then refresh.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        AnimatedContent(
            targetState = visible,
            transitionSpec = { fadeIn(tween(MotionMedium, easing = EaseOutQuart)) togetherWith fadeOut(tween(MotionShort)) },
            // Only loading → loaded fades; a refresh updates the list in place.
            contentKey = { it == null },
            label = "attendees",
        ) { shown ->
            when {
                shown == null -> if (!loadFailed) AttendeeListSkeleton()
                shown.isEmpty() -> Text(
                    if (isHost && event.active) "Nobody has joined yet. Show the join QR or share the code." else "Nobody to show yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> SectionCard {
                    shown.forEachIndexed { i, person ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        AttendeeRow(person)
                    }
                }
            }
        }
        if (!isHost) {
            Text(
                "You see attendees who are green or yellow. The host sees everyone's status.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        actionError?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (isHost && event.active) {
            OutlinedButton({ confirmingEnd = true }, Modifier.fillMaxWidth().height(52.dp), enabled = !working) {
                Text("End event")
            }
        }
        TextButton({ confirmingRemove = true }, Modifier.fillMaxWidth(), enabled = !working) {
            Text(
                when {
                    isHost -> "Delete event"
                    rsvp != null -> "Cancel RSVP and leave"
                    else -> "Leave event"
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showingQr) {
        FullScreenQr(event.joinCode, event.name, status) {
            showingQr = false
            // Whoever just scanned the code should be on the list when the host looks back.
            reload++
        }
    }
    if (confirmingEnd) {
        AlertDialog(
            onDismissRequest = { confirmingEnd = false },
            title = { Text("End ${event.name}?") },
            text = { Text("Nobody else will be able to join, and it moves to Past. People who already joined can still open it.") },
            confirmButton = {
                TextButton({
                    confirmingEnd = false
                    end()
                }) { Text("End event") }
            },
            dismissButton = { TextButton({ confirmingEnd = false }) { Text("Cancel") } },
        )
    }
    if (confirmingRemove) {
        AlertDialog(
            onDismissRequest = { confirmingRemove = false },
            title = { Text(if (isHost) "Delete ${event.name}?" else "Leave ${event.name}?") },
            text = {
                Text(
                    if (isHost) {
                        "The event is removed for you and for everyone who joined. This can't be undone."
                    } else {
                        "The event leaves your list and you come off its attendee list. " +
                            "You can join again with the code while the event is still running."
                    },
                )
            },
            confirmButton = {
                TextButton({
                    confirmingRemove = false
                    remove()
                }) { Text(if (isHost) "Delete event" else "Leave event", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ confirmingRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AttendeeRow(person: Attendee) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Halo(person.status) { Avatar(person.name, 40, person.profile.avatarUrl) }
        Column(Modifier.weight(1f)) {
            Text(person.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            StatusLabel(person.status)
        }
    }
}
