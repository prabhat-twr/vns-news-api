package com.banarasbuzz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A well-known Banaras market, food lane or service point. */
class LocalPlace(
    val id: String,
    val name: String,
    val tagline: String,
    val area: String,
    val category: String,
    val emoji: String,
    val colors: List<Color>,
    val mapsQuery: String,
    val tag: String? = null,
)

private val warm = listOf(Color(0xFFFFC56E), Color(0xFFE5732E))
private val silk = listOf(Color(0xFFB83B5E), Color(0xFFF08A5D))
private val green = listOf(Color(0xFF9BD17B), Color(0xFF3F8F4E))
private val river = listOf(Color(0xFF7FB3D5), Color(0xFF2E5E8C))

val localPlaces = listOf(
    LocalPlace("kachori-gali", "Kachori Gali", "Kachori-sabzi & jalebi breakfast lane", "Chowk", "Food", "🥟", warm, "Kachori Gali Varanasi", "सुबह का नाश्ता"),
    LocalPlace("godowlia-chaat", "Godowlia Chaat", "Tamatar chaat, palak patta chaat", "Godowlia", "Food", "🍲", warm, "Chaat Godowlia Varanasi"),
    LocalPlace("malaiyo", "Malaiyo", "Winter-only saffron milk foam", "Chowk & Pakka Mahal", "Food", "🥛", listOf(Color(0xFFFFE9A8), Color(0xFFF2B33D)), "Malaiyo Varanasi", "सर्दियों में"),
    LocalPlace("banarasi-silk", "Banarasi Silk Weavers", "Handloom sarees & brocade", "Peeli Kothi", "Shopping", "🧣", silk, "Banarasi saree weavers Peeli Kothi Varanasi"),
    LocalPlace("thatheri-bazaar", "Thatheri Bazaar", "Brass & copper utensils", "Chowk", "Shopping", "🏺", listOf(Color(0xFFE8B04B), Color(0xFF9C6A1E)), "Thatheri Bazaar Varanasi"),
    LocalPlace("vishwanath-gali", "Vishwanath Gali", "Puja items, bangles, wooden toys", "Near Kashi Vishwanath", "Shopping", "🪔", silk, "Vishwanath Gali Varanasi"),
    LocalPlace("godowlia-market", "Godowlia Market", "Clothes, footwear, everyday shopping", "Godowlia", "Shopping", "🛍️", listOf(Color(0xFFF7A072), Color(0xFFD1495B)), "Godowlia Market Varanasi"),
    LocalPlace("boat-ride", "Boat Rides", "Sunrise & aarti boat rides", "Dashashwamedh Ghat", "Services", "⛵", river, "Boat ride Dashashwamedh Ghat Varanasi"),
    LocalPlace("prepaid-auto", "Prepaid Auto Booth", "Fixed-fare autos from the station", "Varanasi Junction", "Services", "🛺", green, "Prepaid auto booth Varanasi Junction"),
    LocalPlace("tourist-info", "Tourist Information", "Maps, guides and help desks", "Varanasi Cantt", "Services", "ℹ️", river, "Tourist information centre Varanasi"),
)

private val categories = listOf("All", "Food", "Shopping", "Services")

@Composable
fun BusinessScreen(vm: FeedViewModel, actions: AppActions) {
    var cat by rememberSaveable { mutableStateOf(0) }
    val list = localPlaces.filter { cat == 0 || it.category == categories[cat] }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Local Business", onBack = actions.back)
        PillChips(categories, cat) { cat = it }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(list, key = { it.id }) { p ->
                PlaceCard(
                    p,
                    saved = vm.isSaved(p.id),
                    onSave = {
                        vm.toggleSaved(p.id)
                        actions.say(if (vm.isSaved(p.id)) "सहेजा गया" else "हटाया गया")
                    },
                    onOpen = { actions.open(mapsSearch(p.mapsQuery)) },
                )
            }
            item(key = "add") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(BB.Tile)
                        .clickable { actions.say("बिज़नेस लिस्टिंग जल्द आ रही है") }
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🏪  अपना बिज़नेस जोड़ें", fontWeight = FontWeight.Bold, color = BB.Ink, fontSize = 16.sp)
                    Text("Offers & deals from local shops — coming soon", fontSize = 12.5.sp, color = BB.Muted)
                }
            }
        }
    }
}

@Composable
private fun PlaceCard(p: LocalPlace, saved: Boolean, onSave: () -> Unit, onOpen: () -> Unit) {
    WarmCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column {
                EmojiArt(
                    p.emoji,
                    p.colors,
                    Modifier
                        .size(width = 104.dp, height = 88.dp)
                        .clip(RoundedCornerShape(14.dp)),
                    size = 38.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = BB.Ink)
                Text(p.tagline, fontSize = 13.sp, color = BB.InkSoft, maxLines = 2)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍 ${p.area}", fontSize = 12.sp, color = BB.Muted, modifier = Modifier.weight(1f))
                    if (p.tag != null) {
                        Text(
                            p.tag,
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BB.Saffron)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onSave) {
                Icon(
                    if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "सहेजें",
                    tint = if (saved) BB.Saffron else BB.Muted,
                )
            }
        }
    }
}
