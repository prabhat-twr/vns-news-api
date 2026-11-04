package com.banarasbuzz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.net.URLEncoder

fun mapsSearch(query: String) =
    "https://www.google.com/maps/search/?api=1&query=" + URLEncoder.encode(query, "UTF-8")

private class ExploreTile(
    val label: String,
    val scene: Scene?,
    val emoji: String,
    val colors: List<Color>,
    val onClick: (AppActions) -> Unit,
)

private val exploreTiles = listOf(
    ExploreTile("Temples", Scene.DAWN, "🛕", emptyList()) { it.open(mapsSearch("Temples in Varanasi")) },
    ExploreTile("Ghats", Scene.DUSK, "🌅", emptyList()) { it.open(mapsSearch("Ghats in Varanasi")) },
    ExploreTile("Food", null, "🥟", listOf(Color(0xFFFFC56E), Color(0xFFE5732E))) { it.go(Route.BUSINESS) },
    ExploreTile("Culture", null, "🪔", listOf(Color(0xFF8E2D4F), Color(0xFFF2A541))) { it.go(Route.EVENTS) },
    ExploreTile("Shopping", null, "🧣", listOf(Color(0xFFB83B5E), Color(0xFFF08A5D))) { it.go(Route.BUSINESS) },
    ExploreTile("Tourism", Scene.NIGHT, "⛵", emptyList()) { it.open(mapsSearch("Tourist attractions in Varanasi")) },
)

@Composable
fun ExploreScreen(vm: FeedViewModel, actions: AppActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Explore Banaras")
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = BB.Muted) },
            placeholder = { Text("Search news, events, places…") },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BB.Saffron,
                unfocusedBorderColor = BB.Line,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (q.isNotEmpty()) {
            SearchResults(vm, q, actions)
            return@Column
        }

        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            exploreTiles.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    pair.forEach { t -> TileCard(t, Modifier.weight(1f)) { t.onClick(actions) } }
                }
            }
        }

        SectionTitle("Quick Links")
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            QuickLink("🙏", "Live Darshan") { actions.open("https://shrikashivishwanath.org/") }
            QuickLink("📍", "My Banaras") { actions.go(Route.COMMUNITY) }
            QuickLink("🛺", "Bus/Auto") { actions.open(mapsSearch("Bus stand Varanasi")) }
            QuickLink("🗺️", "Maps") { actions.open(mapsSearch("Varanasi")) }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun TileCard(t: ExploreTile, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(118.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        if (t.scene != null) {
            BanarasScene(t.scene, Modifier.fillMaxSize())
        } else {
            EmojiArt(t.emoji, t.colors, Modifier.fillMaxSize(), size = 46.sp)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x88000000))))
        )
        Text(
            t.label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            style = TextStyle(shadow = Shadow(Color(0x99000000), blurRadius = 6f)),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        )
    }
}

@Composable
private fun QuickLink(emoji: String, label: String, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(BB.Tile),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 24.sp) }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, color = BB.InkSoft, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SearchResults(vm: FeedViewModel, q: String, actions: AppActions) {
    val news = vm.feed.news.filter { (it.title + " " + it.summary).lowercase().contains(q) }.take(20)
    val events = vm.feed.events.filter {
        (it.name + " " + it.description + " " + (it.venue ?: "")).lowercase().contains(q)
    }.take(10)
    val places = localPlaces.filter { (it.name + " " + it.tagline + " " + it.area).lowercase().contains(q) }

    if (news.isEmpty() && events.isEmpty() && places.isEmpty()) {
        EmptyNote("कुछ नहीं मिला।")
        return
    }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        places.forEach { p -> ResultRow("🏪", p.name, p.area) { actions.open(mapsSearch(p.mapsQuery)) } }
        events.forEach { e -> ResultRow("📅", e.name, e.venue ?: "") { e.sourceUrl?.let(actions.open) } }
        news.forEach { n -> ResultRow("📰", n.title, timeAgo(n.timestamp)) { n.sourceUrl?.let(actions.open) } }
    }
}

@Composable
private fun ResultRow(emoji: String, title: String, sub: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = BB.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub.isNotBlank()) Text(sub, fontSize = 12.sp, color = BB.Muted, maxLines = 1)
        }
    }
}
