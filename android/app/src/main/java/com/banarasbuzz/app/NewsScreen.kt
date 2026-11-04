package com.banarasbuzz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@Composable
fun NewsScreen(vm: FeedViewModel, actions: AppActions) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var cityMenu by remember { mutableStateOf(false) }

    val cities = vm.cities()
    val cityName = cities.firstOrNull { it.first == vm.city }?.second ?: "वाराणसी"

    val q = query.trim().lowercase()
    val list = vm.feed.news.filter { n ->
        val inTab = when (tab) {
            1 -> n.cityId == vm.city
            2 -> n.isBreaking
            else -> true
        }
        inTab && (q.isEmpty() || (n.title + " " + n.summary).lowercase().contains(q))
    }.take(150)

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Top News", onBack = actions.back) {
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, contentDescription = "खोजें", tint = BB.Ink)
            }
            Box {
                Row(
                    Modifier
                        .clip(CircleShape)
                        .background(BB.Tile)
                        .clickable { cityMenu = true }
                        .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(cityName, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = BB.Ink)
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = BB.Ink, modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = cityMenu, onDismissRequest = { cityMenu = false }) {
                    cities.forEach { (id, name) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = { vm.city = id; tab = 1; cityMenu = false },
                        )
                    }
                }
            }
        }
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("खबरें खोजें…") },
                shape = RoundedCornerShape(14.dp),
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
        }
        UnderlineTabs(listOf("Top Stories", "Local", "Breaking"), tab) { tab = it }

        if (list.isEmpty()) {
            EmptyNote("अभी यहाँ कोई खबर नहीं है।")
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val lead = list.firstOrNull { it.imageUrl != null } ?: list.first()
            item(key = "lead-" + lead.id) { LeadStory(lead, actions) }
            items(list.filter { it.id != lead.id }, key = { it.id }) { n -> StoryRow(n, actions) }
        }
    }
}

@Composable
private fun LeadStory(n: NewsItem, actions: AppActions) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = n.sourceUrl != null) { n.sourceUrl?.let(actions.open) }
    ) {
        if (n.imageUrl != null) {
            AsyncImage(
                model = n.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BanarasScene(Scene.NIGHT, Modifier.fillMaxSize())
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            if (n.isBreaking) {
                Text(
                    "ब्रेकिंग",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BB.Saffron)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                n.title,
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 25.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOf("🕑 " + timeAgo(n.timestamp), n.cityName, n.sourceName).filter { it.isNotBlank() && it != "🕑 " }
                    .joinToString(" • "),
                color = Color(0xFFF3E6D6),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun StoryRow(n: NewsItem, actions: AppActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(enabled = n.sourceUrl != null) { n.sourceUrl?.let(actions.open) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 104.dp, height = 78.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            if (n.imageUrl != null) {
                AsyncImage(
                    model = n.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                BanarasScene(Scene.DUSK, Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                n.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = BB.Ink,
                lineHeight = 20.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(timeAgo(n.timestamp), n.sourceName).filter { it.isNotBlank() }.joinToString(" • "),
                fontSize = 12.sp,
                color = BB.Muted,
                maxLines = 1,
            )
        }
    }
}
