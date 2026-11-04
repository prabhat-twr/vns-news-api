package com.banarasbuzz.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private val pollOptions = listOf("अस्सी घाट", "दशाश्वमेध घाट", "सारनाथ", "बीएचयू", "रामनगर किला")

@Composable
fun CommunityScreen(vm: FeedViewModel, actions: AppActions) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val withImages = vm.feed.news.filter { it.imageUrl != null }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader("My Banaras", onBack = actions.back)
            UnderlineTabs(listOf("Stories", "Photos", "Polls"), tab) { tab = it }
            when (tab) {
                0 -> StoriesTab(withImages.take(40), vm, actions)
                1 -> PhotosTab(withImages.take(60), actions)
                else -> PollTab(vm)
            }
        }
        ExtendedFloatingActionButton(
            onClick = { actions.say("पोस्ट करने की सुविधा जल्द आ रही है") },
            containerColor = BB.Saffron,
            contentColor = Color.White,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Post", fontWeight = FontWeight.Bold) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }
}

@Composable
private fun StoriesTab(list: List<NewsItem>, vm: FeedViewModel, actions: AppActions) {
    val context = LocalContext.current
    if (list.isEmpty()) {
        EmptyNote("अभी कोई कहानी नहीं है।")
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(list, key = { it.id }) { n ->
            WarmCard(Modifier.fillMaxWidth()) {
                Column {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(BB.Tile)
                                .border(1.dp, BB.Saffron, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(n.sourceName.take(1).ifBlank { "B" }, fontWeight = FontWeight.Bold, color = BB.Saffron)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(n.sourceName.ifBlank { "Banaras Buzz" }, fontWeight = FontWeight.Bold, color = BB.Ink, fontSize = 14.sp)
                            Text(
                                listOf(timeAgo(n.timestamp), n.cityName).filter { it.isNotBlank() }.joinToString(" • "),
                                fontSize = 12.sp,
                                color = BB.Muted,
                            )
                        }
                    }
                    Text(
                        n.title,
                        color = BB.Ink,
                        fontSize = 15.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    AsyncImage(
                        model = n.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clickable(enabled = n.sourceUrl != null) { n.sourceUrl?.let(actions.open) },
                    )
                    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        val liked = vm.isSaved("like-" + n.id)
                        IconButton(onClick = { vm.toggleSaved("like-" + n.id) }) {
                            Icon(
                                if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = "पसंद",
                                tint = if (liked) BB.Heart else BB.InkSoft,
                            )
                        }
                        if (n.sourceUrl != null) {
                            Text(
                                "पूरी खबर →",
                                color = BB.Saffron,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { actions.open(n.sourceUrl) }
                                    .padding(8.dp),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            val text = n.title + (n.sourceUrl?.let { "\n$it" } ?: "") + "\n— via Banaras Buzz"
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                            runCatching { context.startActivity(Intent.createChooser(send, null)) }
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "शेयर", tint = BB.InkSoft)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotosTab(list: List<NewsItem>, actions: AppActions) {
    if (list.isEmpty()) {
        EmptyNote("अभी कोई फ़ोटो नहीं है।")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 88.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(list, key = { it.id }) { n ->
            AsyncImage(
                model = n.imageUrl,
                contentDescription = n.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = n.sourceUrl != null) { n.sourceUrl?.let(actions.open) },
            )
        }
    }
}

@Composable
private fun PollTab(vm: FeedViewModel) {
    val voted = pollOptions.indexOfFirst { vm.isSaved("poll-fav-$it") }
    Column(Modifier.padding(16.dp)) {
        WarmCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("बनारस में आपकी पसंदीदा जगह?", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = BB.Ink)
                pollOptions.forEachIndexed { i, opt ->
                    val chosen = i == voted
                    Text(
                        (if (chosen) "✓  " else "") + opt,
                        fontSize = 15.sp,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium,
                        color = if (chosen) Color.White else BB.Ink,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (chosen) BB.Saffron else BB.Tile)
                            .clickable {
                                pollOptions.forEach { vm.saved["poll-fav-$it"] = false }
                                vm.saved["poll-fav-$opt"] = true
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
                if (voted >= 0) {
                    Text("धन्यवाद! सामुदायिक नतीजे जल्द दिखेंगे।", fontSize = 12.5.sp, color = BB.Muted)
                }
            }
        }
    }
}
