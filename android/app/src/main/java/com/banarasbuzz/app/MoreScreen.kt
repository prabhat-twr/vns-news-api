package com.banarasbuzz.app

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val SITE_URL = "https://prabhat-twr.github.io/vns-news-api/"
private const val PRIVACY_URL = "https://prabhat-twr.github.io/vns-news-api/privacy.html"
private const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.banarasbuzz.app"

@Composable
fun MoreScreen(vm: FeedViewModel, actions: AppActions) {
    val context = LocalContext.current
    var cityDialog by remember { mutableStateOf(false) }
    var aboutDialog by remember { mutableStateOf(false) }
    val cities = vm.cities()
    val cityName = cities.firstOrNull { it.first == vm.city }?.second ?: "वाराणसी"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("More")
        Column(
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
        ) {
            MoreRow(Icons.Filled.LocationOn, "My City Settings", "आपका शहर: $cityName") { cityDialog = true }
            MoreRow(Icons.Filled.Notifications, "Notifications", "News, Events, Panchang") {
                actions.say("सूचनाएँ जल्द आ रही हैं")
            }
            MoreRow(Icons.Filled.Refresh, "Refresh", if (vm.loading) "अपडेट हो रहा है…" else "ताज़ा खबरें और पंचांग लाएँ") {
                vm.refresh()
                actions.say("अपडेट हो रहा है…")
            }
            MoreRow(Icons.Filled.Info, "About Banaras Buzz", "Our Mission") { aboutDialog = true }
            MoreRow(Icons.Filled.Share, "Share App", "With Friends & Family") {
                val send = Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "Banaras Buzz — अपना बनारस, हर दिन\n$SITE_URL")
                runCatching { context.startActivity(Intent.createChooser(send, null)) }
            }
            MoreRow(Icons.Filled.Star, "Feedback", "Help us improve") { actions.open(PLAY_URL) }
            MoreRow(Icons.Filled.Lock, "Terms & Privacy", "Your Data Matters", divider = false) { actions.open(PRIVACY_URL) }
        }

        Box(
            Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(20.dp))
        ) {
            BanarasScene(Scene.DAWN, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xAAFFF3E0))))
            )
            Text(
                "Together for a\nBetter Banaras ❤️",
                color = BB.Ink,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 18.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
    }

    if (cityDialog) {
        AlertDialog(
            onDismissRequest = { cityDialog = false },
            title = { Text("अपना शहर चुनें") },
            text = {
                Column {
                    if (cities.isEmpty()) Text("शहरों की सूची अभी उपलब्ध नहीं है।")
                    cities.forEach { (id, name) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { vm.city = id; cityDialog = false }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = vm.city == id,
                                onClick = { vm.city = id; cityDialog = false },
                                colors = RadioButtonDefaults.colors(selectedColor = BB.Saffron),
                            )
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { cityDialog = false }) { Text("ठीक है", color = BB.Saffron) } },
            containerColor = BB.Cream,
        )
    }
    if (aboutDialog) {
        AlertDialog(
            onDismissRequest = { aboutDialog = false },
            title = { Text("Banaras Buzz") },
            text = {
                Text(
                    "वाराणसी, मिर्ज़ापुर और जौनपुर की ताज़ा खबरें, रोज़ का पंचांग, व्रत-त्योहार " +
                        "और शहर के कार्यक्रम — एक ही जगह। खबरें मूल स्रोतों से ली जाती हैं और " +
                        "हर खबर पर उसका स्रोत दिखाया जाता है।"
                )
            },
            confirmButton = { TextButton(onClick = { aboutDialog = false }) { Text("ठीक है", color = BB.Saffron) } },
            containerColor = BB.Cream,
        )
    }
}

@Composable
private fun MoreRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    divider: Boolean = true,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(BB.Tile),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = BB.Saffron, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, color = BB.Ink, fontSize = 15.sp)
                Text(subtitle, color = BB.Muted, fontSize = 12.5.sp)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = BB.Muted)
        }
        if (divider) {
            Box(
                Modifier
                    .padding(start = 68.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(BB.Line)
            )
        }
    }
}
