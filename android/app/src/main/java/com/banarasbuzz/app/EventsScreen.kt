package com.banarasbuzz.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate

private val categoryHi = mapOf(
    "religious" to "धार्मिक", "cultural" to "सांस्कृतिक", "tourism" to "पर्यटन", "general" to "सामान्य",
)

private fun EventItem.day(): LocalDate? =
    if (startMillis == 0L) null else Instant.ofEpochMilli(startMillis).atZone(IST).toLocalDate()

@Composable
fun EventsScreen(vm: FeedViewModel, actions: AppActions) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val today = todayIst()
    val end = when (tab) {
        0 -> today
        1 -> today.plusDays(6)
        else -> today.plusDays(30)
    }
    val events = vm.feed.events.filter { e ->
        val d = e.day() ?: return@filter false
        !d.isBefore(today) && !d.isAfter(end)
    }
    val laterEvents = if (events.isEmpty()) {
        vm.feed.events.filter { e -> e.day()?.isAfter(end) == true }.take(5)
    } else emptyList()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Events in Banaras", onBack = actions.back)
        UnderlineTabs(listOf("Today", "This Week", "This Month"), tab) { tab = it }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The evening Ganga Aarti happens every day, so it always leads the list.
            item(key = "aarti") {
                EventCard(
                    title = "Ganga Aarti at Dashashwamedh Ghat",
                    whenText = "रोज़ • सूर्यास्त के बाद" +
                        (vm.feed.panchang?.sunset?.takeIf { it != 0L && vm.feed.panchang?.date == today.toString() }
                            ?.let { " (सूर्यास्त ${clockShort(it)})" } ?: ""),
                    venue = "Dashashwamedh Ghat",
                    imageUrl = null,
                    scene = Scene.NIGHT,
                    onDetails = { actions.open("https://www.google.com/maps/search/?api=1&query=Dashashwamedh+Ghat+Varanasi") },
                    actionLabel = "Directions",
                )
            }
            items(events, key = { it.id }) { e -> LiveEventCard(e, actions) }
            if (events.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (laterEvents.isEmpty()) "इस अवधि में कोई और कार्यक्रम दर्ज नहीं है।"
                        else "इस अवधि में कोई और कार्यक्रम नहीं — आगे के कार्यक्रम:",
                        color = BB.Muted,
                        fontSize = 14.sp,
                    )
                }
                items(laterEvents, key = { "later-" + it.id }) { e -> LiveEventCard(e, actions) }
            }
        }
    }
}

@Composable
private fun LiveEventCard(e: EventItem, actions: AppActions) {
    val d = e.day()
    val dateText = when {
        d == null -> ""
        d == todayIst() -> "आज"
        d == todayIst().plusDays(1) -> "कल"
        else -> hindiShortDate(d)
    }
    val whenText = listOfNotNull(
        dateText.takeIf { it.isNotBlank() },
        if (!e.allDay && e.startMillis != 0L) clockShort(e.startMillis) else null,
        categoryHi[e.category],
        if (e.approximateDate) "तिथि अनुमानित" else null,
    ).joinToString(" • ")
    EventCard(
        title = e.name,
        whenText = whenText,
        venue = e.venue,
        imageUrl = e.imageUrl,
        scene = if (e.category == "religious") Scene.DUSK else Scene.DAWN,
        description = e.description,
        onDetails = e.sourceUrl?.let { url -> { actions.open(url) } },
    )
}

@Composable
private fun EventCard(
    title: String,
    whenText: String,
    venue: String?,
    imageUrl: String?,
    scene: Scene,
    description: String = "",
    actionLabel: String = "View Details",
    onDetails: (() -> Unit)?,
) {
    WarmCard(Modifier.fillMaxWidth(), onClick = onDetails) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    BanarasScene(scene, Modifier.fillMaxSize())
                }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    title,
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = BB.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (whenText.isNotBlank()) MetaLine("📅", whenText)
                if (!venue.isNullOrBlank()) MetaLine("📍", venue)
                if (description.isNotBlank()) {
                    Text(
                        description,
                        fontSize = 13.sp,
                        color = BB.Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (onDetails != null) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlineAction(actionLabel, onClick = onDetails)
                    }
                } else {
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}
