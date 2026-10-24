package com.banarasbuzz.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Accent = Color(0xFFC2410C)
private val AccentDark = Color(0xFF7C2D12)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Accent,
                    onPrimary = Color.White,
                    secondaryContainer = Color(0xFFF3E8D8),
                    onSecondaryContainer = AccentDark,
                    background = Color(0xFFFBF7F0),
                    surface = Color.White,
                )
            ) {
                BuzzApp()
            }
        }
    }
}

class FeedViewModel(app: Application) : AndroidViewModel(app) {
    var feed by mutableStateOf(Feed())
        private set
    var loading by mutableStateOf(false)
        private set
    var loaded by mutableStateOf(false)
        private set

    init {
        refresh()
    }

    fun refresh() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            feed = Repository.load(getApplication<Application>())
            loaded = true
            loading = false
        }
    }
}

private val categoryLabels = mapOf(
    "religious" to "धार्मिक",
    "cultural" to "सांस्कृतिक",
    "tourism" to "पर्यटन",
    "general" to "सामान्य",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuzzApp(vm: FeedViewModel = viewModel()) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Banaras Buzz", fontWeight = FontWeight.Bold, color = AccentDark)
                        Text(
                            "वाराणसी · मिर्ज़ापुर · जौनपुर",
                            fontSize = 12.sp,
                            color = Color(0xFF6B6459),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0; filter = "all" },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                    label = { Text("खबरें") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1; filter = "all" },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    label = { Text("कार्यक्रम") },
                )
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("खोजें / Search…") },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )

            val chips: List<Pair<String, String>> = if (tab == 0) {
                val cities = vm.feed.news
                    .filter { it.cityId.isNotBlank() }
                    .map { it.cityId to it.cityName }
                    .distinct()
                listOf("all" to "सभी", "breaking" to "ब्रेकिंग") + cities
            } else {
                val cats = vm.feed.events.map { it.category }.distinct()
                listOf("all" to "सभी", "upcoming" to "आगामी") +
                    cats.map { it to (categoryLabels[it] ?: it) }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(chips) { (key, label) ->
                    FilterChip(
                        selected = filter == key,
                        onClick = { filter = key },
                        label = { Text(label) },
                    )
                }
            }

            if (vm.feed.offline && vm.loaded) {
                Text(
                    "ऑफ़लाइन — पुरानी सहेजी हुई खबरें दिख रही हैं",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = Color(0xFF6B6459),
                )
            }

            Box(Modifier.fillMaxSize()) {
                if (!vm.loaded && vm.loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else if (tab == 0) {
                    NewsList(vm.feed.news, filter, query)
                } else {
                    EventList(vm.feed.events, filter, query)
                }
            }
        }
    }
}

@Composable
private fun NewsList(all: List<NewsItem>, filter: String, query: String) {
    val q = query.trim().lowercase()
    val list = all.filter { n ->
        val inFilter = when (filter) {
            "all" -> true
            "breaking" -> n.isBreaking
            else -> n.cityId == filter
        }
        val matches = q.isEmpty() || (n.title + " " + n.summary).lowercase().contains(q)
        inFilter && matches
    }.take(150)

    if (list.isEmpty()) {
        EmptyState()
        return
    }
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(list, key = { it.id }) { item ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = item.sourceUrl != null) {
                        item.sourceUrl?.let { runCatching { uriHandler.openUri(it) } }
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column {
                    if (item.imageUrl != null) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        )
                    }
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (item.isBreaking) {
                                Text(
                                    "ब्रेकिंग",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(Accent)
                                        .padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                            Text(
                                listOf(item.cityName, item.dateText, item.sourceName)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" · "),
                                fontSize = 12.sp,
                                color = Color(0xFF6B6459),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(item.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                        if (item.summary.isNotBlank()) {
                            Text(
                                item.summary,
                                fontSize = 14.sp,
                                color = Color(0xFF6B6459),
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (item.sourceUrl != null) {
                            Text("पूरी खबर →", color = Accent, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventList(all: List<EventItem>, filter: String, query: String) {
    val q = query.trim().lowercase()
    val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    val list = all.filter { e ->
        val inFilter = when (filter) {
            "all" -> true
            "upcoming" -> e.startMillis >= cutoff
            else -> e.category == filter
        }
        val matches = q.isEmpty() ||
            (e.name + " " + e.description + " " + (e.venue ?: "")).lowercase().contains(q)
        inFilter && matches
    }

    if (list.isEmpty()) {
        EmptyState()
        return
    }
    val uriHandler = LocalUriHandler.current
    val zone = ZoneId.of("Asia/Kolkata")
    val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    val timeFmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(list, key = { it.id }) { ev ->
            val whenText = if (ev.startMillis == 0L) "" else {
                val z = Instant.ofEpochMilli(ev.startMillis).atZone(zone)
                (if (ev.allDay) dayFmt else timeFmt).format(z) +
                    (if (ev.approximateDate) " (तिथि अनुमानित)" else "")
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = ev.sourceUrl != null) {
                        ev.sourceUrl?.let { runCatching { uriHandler.openUri(it) } }
                    },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        (categoryLabels[ev.category] ?: ev.category) +
                            (if (whenText.isNotBlank()) " · $whenText" else ""),
                        fontSize = 12.sp,
                        color = Accent,
                    )
                    Text(ev.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    if (!ev.venue.isNullOrBlank()) {
                        Text("📍 ${ev.venue}", fontSize = 13.sp, color = Color(0xFF6B6459))
                    }
                    if (ev.description.isNotBlank()) {
                        Text(
                            ev.description,
                            fontSize = 14.sp,
                            color = Color(0xFF6B6459),
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (ev.sourceUrl != null) {
                        Text("विवरण →", color = Accent, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("कुछ नहीं मिला।", color = Color(0xFF6B6459))
    }
}
