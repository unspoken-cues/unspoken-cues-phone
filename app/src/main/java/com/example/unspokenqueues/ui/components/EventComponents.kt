package com.example.unspokenqueues.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.unspokenqueues.R
import com.example.unspokenqueues.model.CueStatus
import com.example.unspokenqueues.model.Event
import com.example.unspokenqueues.model.EventPhase
import com.example.unspokenqueues.model.Rsvp
import com.example.unspokenqueues.model.formatWhen
import com.example.unspokenqueues.model.goingText
import com.example.unspokenqueues.model.phase
import com.example.unspokenqueues.model.tagLabel
import com.example.unspokenqueues.ui.theme.EaseOutQuart
import com.example.unspokenqueues.ui.theme.Ink
import com.example.unspokenqueues.ui.theme.MotionMedium
import java.time.Instant
import java.time.ZoneId

/**
 * A pill-shaped switch between a few views ("Upcoming", "Discover", "Past"). The chosen one is
 * filled ink with its word in paper, so the choice reads without color.
 */
@Composable
fun SegmentedTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
            .padding(4.dp)
            .selectableGroup(),
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            val fill by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                tween(MotionMedium, easing = EaseOutQuart),
                label = "segmentFill",
            )
            val ink by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                tween(MotionMedium, easing = EaseOutQuart),
                label = "segmentInk",
            )
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = ink)
            }
        }
    }
}

/** A filter pill: filled ink with a check when on, outlined when off. */
@Composable
fun TagPill(label: String, selected: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(CircleShape)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() }),
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) Icon(painterResource(R.drawable.ic_check), contentDescription = null, Modifier.size(14.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A scrolling row of tag filters. Tapping a tag turns it on or off; several can be on at once. */
@Composable
fun TagFilterRow(
    tags: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(tags, key = { it }) { tag -> TagPill(tagLabel(tag), tag in selected, { onToggle(tag) }) }
    }
}

/**
 * An event's cover image. With no image it shows a cue-colored field (the cue is picked from the
 * event's id, so an event keeps its color) with the event's first letter, so a list is never a
 * column of grey boxes.
 */
@Composable
fun EventCover(
    eventId: String,
    name: String,
    coverUrl: String,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
) {
    val cue = remember(eventId) { CueStatus.entries[(eventId.hashCode() and Int.MAX_VALUE) % CueStatus.entries.size] }
    Box(modifier.clip(shape).background(cue.color), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.16f), radius = size.minDimension * 0.6f, center = Offset(size.width * 0.12f, size.height))
            drawCircle(Color.Black.copy(alpha = 0.08f), radius = size.minDimension * 0.42f, center = Offset(size.width * 0.92f, size.height * 0.05f))
        }
        Text(
            name.trim().take(1).uppercase().ifEmpty { "?" },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = cue.onColor,
        )
        if (coverUrl.isNotBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** A small label on top of a picture: white on a dark scrim, so it reads over any cover. */
@Composable
fun ScrimBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .background(Ink.copy(alpha = 0.82f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** One line of detail with a small icon: a time, a place, a headcount. */
@Composable
fun MetaLine(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(icon), contentDescription = null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Who is hosting: a small initial in a circle and their name. Nothing when the name is unknown. */
@Composable
fun HostLine(hostName: String, modifier: Modifier = Modifier) {
    if (hostName.isBlank()) return
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(22.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                hostName.trim().take(1).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Text(
            hostName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What to say about the viewer's relationship to an event, or null when they have none. */
fun eventBadge(event: Event, hosting: Boolean, rsvp: Rsvp?, now: Instant): String? = when {
    event.phase(now) == EventPhase.LIVE -> "Live now"
    !event.active -> "Ended"
    hosting -> "Hosting"
    rsvp != null -> rsvp.label
    else -> null
}

/**
 * An event as a row in a list: host, name, when and where on the left, the cover on the right
 * with the viewer's status over it. Rows sit on the page with a hairline between them.
 */
@Composable
fun EventRowItem(
    event: Event,
    now: Instant,
    zone: ZoneId,
    badge: String?,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.72f else 1f)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HostLine(event.hostName)
            Text(
                event.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MetaLine(R.drawable.ic_schedule, formatWhen(event, now, zone))
            if (event.location.isNotBlank()) MetaLine(R.drawable.ic_place, event.location)
        }
        Box(Modifier.size(96.dp)) {
            EventCover(event.id, event.name, event.coverUrl, Modifier.fillMaxSize(), RoundedCornerShape(20.dp))
            if (badge != null) {
                ScrimBadge(badge, Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp).widthIn(max = 88.dp))
            }
        }
    }
}

/**
 * A public event as a big card for the Discover feed: cover with tags, name, host, when and
 * where, how many are going, and a one-tap RSVP. [onRsvp] is null once the viewer has answered;
 * their answer then shows in its place.
 */
@Composable
fun EventFeatureCard(
    event: Event,
    now: Instant,
    zone: ZoneId,
    rsvp: Rsvp?,
    hosting: Boolean,
    onRsvp: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).clickable(onClick = onClick),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column {
            Box {
                EventCover(
                    event.id, event.name, event.coverUrl,
                    Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    RoundedCornerShape(0.dp),
                )
                Row(
                    Modifier.align(Alignment.BottomStart).padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (event.phase(now) == EventPhase.LIVE) ScrimBadge("Live now")
                    event.tags.take(2).forEach { ScrimBadge(tagLabel(it)) }
                }
            }
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    event.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                HostLine(event.hostName)
                MetaLine(R.drawable.ic_schedule, formatWhen(event, now, zone))
                if (event.location.isNotBlank()) MetaLine(R.drawable.ic_place, event.location)
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val going = goingText(event.goingCount)
                    if (going.isNotEmpty()) MetaLine(R.drawable.ic_people, going)
                    Spacer(Modifier.weight(1f))
                    when {
                        hosting -> Chip("Hosting")
                        onRsvp != null -> Button(onRsvp, Modifier.heightIn(min = 44.dp)) { Text("RSVP") }
                        rsvp != null -> Chip(rsvp.label)
                    }
                }
            }
        }
    }
}
