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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Which sections are live. News and Panchang are; the rest show a "coming soon" screen until
 * their data is ready. Flip a flag to true to bring that section back as it was.
 */
object Features {
    const val EVENTS = false
    const val BUSINESS = false
    const val COMMUNITY = false
    const val EXPLORE = false

    fun isLive(route: Route): Boolean = when (route) {
        Route.EVENTS -> EVENTS
        Route.BUSINESS -> BUSINESS
        Route.COMMUNITY -> COMMUNITY
        Route.EXPLORE -> EXPLORE
        else -> true
    }
}

/** What each coming-soon section will have. */
private class Soon(val title: String, val emoji: String, val tagline: String, val points: List<String>)

private fun soonFor(route: Route) = when (route) {
    Route.EVENTS -> Soon(
        "आज बनारस में", "📅", "शहर के कार्यक्रम, एक जगह",
        listOf("घाटों और मंदिरों के आयोजन", "संगीत, नाटक और प्रदर्शनियाँ", "देव दीपावली, नाग नथैया जैसे बड़े पर्व"),
    )
    Route.BUSINESS -> Soon(
        "लोकल बिजनेस", "🏪", "बनारस की दुकानें और ऑफ़र",
        listOf("खाने-पीने की मशहूर जगहें", "बनारसी साड़ी और हस्तशिल्प", "अपना बिज़नेस जोड़ने की सुविधा"),
    )
    Route.COMMUNITY -> Soon(
        "My Banaras", "📍", "बनारस वालों की कहानियाँ",
        listOf("फ़ोटो और कहानियाँ साझा करें", "शहर से जुड़े पोल", "मोहल्ले की ख़बरें"),
    )
    else -> Soon(
        "Explore Banaras", "🧭", "मंदिर, घाट और शहर की जानकारी",
        listOf("मंदिर और घाट गाइड", "खाना, ख़रीदारी और संस्कृति", "लाइव दर्शन और यात्रा जानकारी"),
    )
}

/** Small "जल्द" badge for tiles and rows that lead to a coming-soon section. */
@Composable
fun SoonBadge(modifier: Modifier = Modifier) {
    Text(
        "जल्द आ रहा है",
        color = Color.White,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(BB.Muted)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun ComingSoonScreen(route: Route, actions: AppActions) {
    val s = soonFor(route)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(s.title, onBack = if (route == Route.EXPLORE) null else actions.back)
        Box(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(22.dp))
        ) {
            BanarasScene(Scene.DUSK, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x88000000))))
            )
            Text(s.emoji, fontSize = 52.sp, modifier = Modifier.align(Alignment.Center))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("जल्द आ रहा है", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = BB.Saffron)
            Text(s.tagline, fontSize = 16.sp, color = BB.InkSoft, textAlign = TextAlign.Center)
        }
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(BB.Tile)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("इसमें क्या होगा", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BB.Ink)
            s.points.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(end = 10.dp)
                            .width(8.dp)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(BB.Saffron)
                    )
                    Text(p, fontSize = 14.5.sp, color = BB.InkSoft)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "तब तक",
            fontSize = 13.sp,
            color = BB.Muted,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(
            Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LiveLink("📰", "ताज़ा खबरें", Modifier.weight(1f)) { actions.go(Route.NEWS) }
            LiveLink("🌞", "आज का पंचांग", Modifier.weight(1f)) { actions.go(Route.PANCHANG) }
        }
    }
}

@Composable
private fun LiveLink(emoji: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 26.sp)
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BB.Ink)
    }
}
