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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PanchangTile = Color(0xFFFFF1DC)
private val PanchangTileGold = Color(0xFFFCE3B5)

@Composable
fun PanchangScreen(vm: FeedViewModel, actions: AppActions) {
    val context = LocalContext.current
    var alertDialog by remember { mutableStateOf(false) }
    if (alertDialog) AlertSettingsDialog(onDismiss = { alertDialog = false }, say = actions.say)
    val p = vm.feed.panchang
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFFFEBCF), BB.Cream)))
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("☀️", fontSize = 28.sp)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("आज का पंचांग", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = BB.Ink)
                val date = p?.date?.let(::parseIsoDate)
                if (date != null) {
                    Text(hindiLongDate(date), fontSize = 15.sp, color = BB.InkSoft, fontWeight = FontWeight.Medium)
                }
            }
            Text("🔆", fontSize = 28.sp)
        }

        if (p == null) {
            EmptyNote("अभी पंचांग उपलब्ध नहीं है। थोड़ी देर बाद फिर देखें।")
            return@Column
        }
        val pDate = parseIsoDate(p.date)
        if (pDate != null && pDate != todayIst()) {
            Text(
                if (vm.loading) "नया पंचांग लाया जा रहा है…"
                else "यह पंचांग ${hindiShortDate(pDate)} का है — ताज़ा करने के लिए यहाँ टैप करें",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = BB.SaffronDeep,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(BB.TileStrong)
                    .clickable(enabled = !vm.loading) { vm.refresh() }
                    .padding(8.dp),
            )
        }

        val tithi = p.tithi.firstOrNull()
        val nak = p.nakshatra.firstOrNull()
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoTile("तिथि", Modifier.weight(1f)) {
                    if (tithi != null) {
                        Text(pakshaInHindi(tithi.paksha), fontSize = 15.sp, color = BB.InkSoft)
                        BigValue(tithiInHindi(tithi.name))
                        EndsAt(tithi.endsAt)
                    } else BigValue("—")
                }
                InfoTile("नक्षत्र", Modifier.weight(1f)) {
                    if (nak != null) {
                        BigValue(nakshatraInHindi(nak.name))
                        EndsAt(nak.endsAt)
                    } else BigValue("—")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoTile("राहुकाल", Modifier.weight(1f)) {
                    val r = p.rahuKaal
                    Text(
                        if (r != null) "${clockShort(r.start)} – ${clockShort(r.end)}" else "—",
                        fontSize = 14.sp,
                        maxLines = 1,
                        fontWeight = FontWeight.Bold,
                        color = BB.SaffronDeep,
                        textAlign = TextAlign.Center,
                    )
                    Text("(सावधान रहें)", fontSize = 12.sp, color = BB.Muted)
                }
                InfoTile("", Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("☀️", fontSize = 22.sp)
                        Spacer(Modifier.width(6.dp))
                        Column {
                            Text("सूर्योदय", fontSize = 14.sp, color = BB.InkSoft, fontWeight = FontWeight.SemiBold)
                            Text(clock(p.sunrise), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = BB.SaffronDeep)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("सूर्यास्त ${clock(p.sunset)}", fontSize = 13.sp, color = BB.SaffronDeep)
                }
            }

            // Entry point for the Rahu Kaal / Shubh Muhurat notifications.
            val alertsOn = remember(alertDialog) { AlertPrefs.anyEnabled(context) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable { alertDialog = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔔", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("राहुकाल और शुभ मुहूर्त अलर्ट", fontWeight = FontWeight.SemiBold, color = BB.Ink, fontSize = 14.5.sp)
                    Text(if (alertsOn) "चालू — बदलने के लिए टैप करें" else "नोटिफिकेशन बार व लॉक स्क्रीन पर पिन करें", fontSize = 12.5.sp, color = BB.Muted)
                }
                Text(
                    if (alertsOn) "चालू" else "चालू करें",
                    color = if (alertsOn) BB.Saffron else Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (alertsOn) BB.Tile else BB.Saffron)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            GoldBand {
                Text("शुभ मुहूर्त", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = BB.Ink)
                Text("अभिजित मुहूर्त", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = BB.InkSoft)
                val a = p.abhijit
                Text(
                    if (a != null) "${clockShort(a.start)} – ${clockShort(a.end)}" else "आज उपलब्ध नहीं",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BB.Ink,
                )
            }

            val todays = (p.festivals + vm.feed.observances.filter { it.date == p.date }.map { it.nameHi ?: it.name }).distinct()
            GoldBand {
                Text("आज के व्रत और त्योहार", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = BB.Ink)
                Text(
                    if (todays.isEmpty()) "आज कोई विशेष व्रत या त्योहार नहीं" else todays.joinToString(" • "),
                    fontSize = 15.sp,
                    color = BB.InkSoft,
                    textAlign = TextAlign.Center,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFact("योग", p.yoga.firstOrNull()?.let { it.nameHi ?: it.name } ?: "—", Modifier.weight(1f))
                SmallFact("करण", p.karana.firstOrNull()?.let { it.nameHi ?: it.name } ?: "—", Modifier.weight(1f))
                SmallFact("चंद्रोदय", clock(p.moonrise).ifBlank { "—" }, Modifier.weight(1f))
            }
        }

        BanarasScene(
            Scene.DAWN,
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(170.dp),
        )

        // Upcoming vrat & tyohar
        val upcoming = vm.feed.observances.filter { o ->
            val d = parseIsoDate(o.date)
            d != null && !d.isBefore(todayIst())
        }
        SectionTitle("आने वाले व्रत और त्योहार")
        if (upcoming.isEmpty()) {
            EmptyNote("आने वाले दिनों की सूची जल्द अपडेट होगी।")
        }
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            upcoming.take(12).forEach { o ->
                val d = parseIsoDate(o.date)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        Modifier
                            .width(58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BB.Tile)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("${d?.dayOfMonth ?: ""}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = BB.Saffron)
                        Text(d?.let { hindiShortDate(it).substringAfter(' ') } ?: "", fontSize = 11.sp, color = BB.InkSoft)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.nameHi ?: o.name, fontWeight = FontWeight.Bold, color = BB.Ink, fontSize = 15.sp)
                        Text(
                            listOf(
                                when (o.type) { "vrat" -> "व्रत"; "both" -> "व्रत व त्योहार"; else -> "त्योहार" },
                                listOf(pakshaInHindi(o.paksha), tithiInHindi(o.tithi)).filter { it.isNotBlank() }.joinToString(" "),
                            ).filter { it.isNotBlank() }.joinToString(" • "),
                            fontSize = 12.5.sp,
                            color = BB.SaffronDeep,
                        )
                        if (o.description.isNotBlank()) {
                            Text(o.description, fontSize = 13.sp, color = BB.Muted, maxLines = 2)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun InfoTile(label: String, modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .heightIn(min = 120.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(PanchangTile)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (label.isNotBlank()) {
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = BB.Ink)
            Spacer(Modifier.height(2.dp))
        }
        content()
    }
}

@Composable
private fun BigValue(text: String) {
    Text(text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = BB.Ink, textAlign = TextAlign.Center)
}

@Composable
private fun EndsAt(millis: Long) {
    if (millis != 0L) Text("${clockShort(millis)} तक", fontSize = 12.sp, color = BB.Muted)
}

@Composable
private fun GoldBand(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(PanchangTileGold)
            .padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun SmallFact(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(PanchangTile)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, fontSize = 13.sp, color = BB.InkSoft, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 13.5.sp, color = BB.Ink, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}
