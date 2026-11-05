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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val FILTERS = listOf("सभी", "त्योहार", "एकादशी", "अन्य व्रत")
private val MONTHS_HI = listOf("जनवरी", "फ़रवरी", "मार्च", "अप्रैल", "मई", "जून", "जुलाई", "अगस्त", "सितंबर", "अक्टूबर", "नवंबर", "दिसंबर")

/** Every vrat and tyohar of the next 12 months, by month. Tapping one opens that day's panchang. */
@Composable
fun YearScreen(vm: FeedViewModel, actions: AppActions) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { vm.ensureYear() }
    val year = vm.year
    val today = todayIst().toString()

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("साल भर के व्रत-त्योहार", onBack = actions.back)
        PillChips(FILTERS, filter) { filter = it }
        when {
            year == null && (vm.yearLoading || !vm.yearFailed) -> EmptyNote("साल भर का पंचांग लाया जा रहा है…")
            year == null -> Text(
                if (vm.yearProblem == Repository.YearProblem.NOT_PUBLISHED)
                    "साल भर की सूची अभी सर्वर पर प्रकाशित नहीं हुई है। दोबारा जाँचने के लिए टैप करें।"
                else "इंटरनेट नहीं मिला, इसलिए सूची नहीं लाई जा सकी। दोबारा कोशिश के लिए टैप करें।",
                color = BB.SaffronDeep,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BB.TileStrong)
                    .clickable { vm.ensureYear(force = true) }
                    .padding(12.dp),
            )
            else -> {
                val list = year.observances.filter { o ->
                    o.date >= today && when (filter) {
                        1 -> o.type != "vrat"
                        2 -> o.name.contains("Ekadashi")
                        3 -> o.type == "vrat" && !o.name.contains("Ekadashi")
                        else -> true
                    }
                }
                val byMonth = list.groupBy { it.date.take(7) }
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "count") {
                        Text(
                            "${list.size} व्रत-त्योहार · ${hindiShortDate(parseIsoDate(year.start) ?: todayIst())} से " +
                                hindiShortDate(parseIsoDate(year.end) ?: todayIst()) + " ${year.end.take(4)} तक",
                            color = BB.Muted,
                            fontSize = 12.5.sp,
                        )
                    }
                    byMonth.forEach { (ym, items) ->
                        item(key = "m-$ym") {
                            Text(
                                "${MONTHS_HI[ym.substring(5, 7).toInt() - 1]} ${ym.take(4)}",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = BB.Ink,
                                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                            )
                        }
                        items(items, key = { it.id }) { o -> YearRow(o) { vm.selectedDate = parseIsoDate(o.date); actions.go(Route.PANCHANG) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearRow(o: Observance, onClick: () -> Unit) {
    val d = parseIsoDate(o.date)
    val festival = o.type != "vrat"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (festival) BB.Tile else Color.White)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${d?.dayOfMonth ?: ""}", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = BB.Saffron)
            Text(d?.let { listOf("सोम", "मंगल", "बुध", "गुरु", "शुक्र", "शनि", "रवि")[it.dayOfWeek.value - 1] } ?: "", fontSize = 11.sp, color = BB.InkSoft)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(o.nameHi ?: o.name, fontWeight = if (festival) FontWeight.Bold else FontWeight.SemiBold, color = BB.Ink, fontSize = 15.sp)
            Text(
                listOf(pakshaInHindi(o.paksha), tithiInHindi(o.tithi)).filter { it.isNotBlank() }.joinToString(" "),
                fontSize = 12.sp,
                color = BB.SaffronDeep,
            )
        }
        Text("›", fontSize = 20.sp, color = BB.Muted)
    }
}
