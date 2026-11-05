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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

@Composable
fun HomeScreen(vm: FeedViewModel, actions: AppActions) {
    val feed = vm.feed
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Masthead
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🛕", fontSize = 34.sp)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "BANARAS BUZZ",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BB.Saffron,
                    letterSpacing = 0.5.sp,
                    lineHeight = 26.sp,
                )
                Text("अपना बनारस, हर दिन", fontSize = 13.sp, color = BB.SaffronDeep, fontWeight = FontWeight.Medium)
            }
            IconButton(onClick = { actions.go(Route.NEWS) }) {
                Icon(Icons.Filled.Notifications, contentDescription = "सूचनाएँ", tint = BB.Saffron)
            }
        }

        // Hero banner
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            BanarasScene(greetingScene(), Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x99000000)), startY = 250f))
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp)
            ) {
                val shadow = TextStyle(shadow = Shadow(Color(0x88000000), blurRadius = 8f))
                Text(
                    greeting() + " ☀️",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = shadow,
                )
                Text("Your daily Banaras briefing", color = Color.White, fontSize = 14.sp, style = shadow)
            }
        }

        // Quick tiles 2x2
        Column(
            Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile("📰", "आज की\n5 बड़ी खबरें", "Local News", Modifier.weight(1f)) { actions.go(Route.NEWS) }
                QuickTile("🌞", "आज का पंचांग", "Tithi, Nakshatra", Modifier.weight(1f)) { actions.go(Route.PANCHANG) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickTile("📅", "आज बनारस में", "Events & More", Modifier.weight(1f)) { actions.go(Route.EVENTS) }
                QuickTile("🏪", "लोकल बिजनेस", "Shops & Markets", Modifier.weight(1f)) { actions.go(Route.BUSINESS) }
            }
        }

        // Local pulse strip
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BB.Tile)
                .clickable { actions.go(Route.COMMUNITY) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("📍", fontSize = 22.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("My Banaras", fontWeight = FontWeight.Bold, color = BB.Ink, fontSize = 15.sp)
                Text("Stories • Photos • City pulse", color = BB.Muted, fontSize = 12.5.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = BB.Saffron)
        }

        // Today's top five headlines
        val top = feed.news.take(5)
        if (top.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("आज की 5 बड़ी खबरें", Modifier.weight(1f))
                Text(
                    "सभी देखें",
                    color = BB.Saffron,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { actions.go(Route.NEWS) }
                        .padding(8.dp),
                )
            }
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                top.forEachIndexed { i, n ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .clickable(enabled = n.sourceUrl != null) { n.sourceUrl?.let(actions.open) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(BB.Saffron),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${i + 1}", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                n.title,
                                fontWeight = FontWeight.SemiBold,
                                color = BB.Ink,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                listOf(n.cityName, timeAgo(n.timestamp)).filter { it.isNotBlank() }.joinToString(" • "),
                                color = BB.Muted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }

        if (feed.offline) {
            Text(
                "ऑफ़लाइन — पिछली सहेजी हुई जानकारी दिख रही है",
                color = BB.Muted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun QuickTile(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(BB.Tile)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(emoji, fontSize = 30.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = BB.Ink,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
        Text(subtitle, fontSize = 12.sp, color = BB.SaffronDeep, textAlign = TextAlign.Center)
    }
}
