package com.banarasbuzz.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.DayOfWeek

/** Switches for the Rahu Kaal and Shubh Muhurat alerts, with today's times. */
@Composable
fun AlertSettingsDialog(onDismiss: () -> Unit, say: (String) -> Unit) {
    val context = LocalContext.current
    // Bumped when we come back from a system settings screen, so permission state is re-read.
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var rahu by remember { mutableStateOf(AlertPrefs.enabled(context, AlertKind.RAHU)) }
    var shubh by remember { mutableStateOf(AlertPrefs.enabled(context, AlertKind.SHUBH)) }
    var early by remember { mutableStateOf(AlertPrefs.early(context)) }
    var sticky by remember { mutableStateOf(AlertPrefs.sticky(context)) }
    // What the user was switching on when we had to ask for notification permission:
    // an AlertKind id, or "sticky".
    var pending by remember { mutableStateOf<String?>(null) }

    fun applySticky(on: Boolean) {
        AlertPrefs.setSticky(context, on)
        sticky = on
        StickyPanchang.update(context)
        if (on) say("आज का पंचांग नोटिफिकेशन बार में पिन हुआ")
    }

    fun apply(kind: AlertKind, on: Boolean) {
        AlertPrefs.setEnabled(context, kind, on)
        if (kind == AlertKind.RAHU) rahu = on else shubh = on
        PanchangAlerts.schedule(context, postRunning = on)
        if (on) say(if (kind == AlertKind.RAHU) "राहुकाल अलर्ट चालू" else "शुभ मुहूर्त अलर्ट चालू")
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val key = pending
        pending = null
        if (key == null) return@rememberLauncherForActivityResult
        if (!granted) {
            say("सूचनाओं की अनुमति नहीं मिली — फ़ोन की सेटिंग में चालू करें")
        } else if (key == "sticky") {
            applySticky(true)
        } else {
            AlertKind.entries.firstOrNull { it.id == key }?.let { apply(it, true) }
        }
    }

    fun needsPermission(on: Boolean) = on && Build.VERSION.SDK_INT >= 33 && !PanchangAlerts.canNotify(context)

    fun toggle(kind: AlertKind, on: Boolean) {
        if (needsPermission(on)) {
            pending = kind.id
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            apply(kind, on)
        }
    }

    fun toggleSticky(on: Boolean) {
        if (needsPermission(on)) {
            pending = "sticky"
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            applySticky(on)
        }
    }

    val today = todayIst()
    val w = remember(refresh) { PanchangAlerts.windows(context, today) }
    val exactOk = remember(refresh) { PanchangAlerts.canScheduleExact(context) }
    val notifyOk = remember(refresh) { PanchangAlerts.canNotify(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = BB.Cream,
        title = { Text("पंचांग अलर्ट", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AlertRow(
                    "📌", "नोटिफिकेशन बार व लॉक स्क्रीन पर पिन करें",
                    "हमेशा दिखेगा — स्वाइप करने पर वापस आ जाएगा। हटाने के लिए सूचना में \"बंद करें\" दबाएँ।",
                    sticky,
                ) { toggleSticky(it) }
                Hint("🏠 होम स्क्रीन पर विजेट जोड़ें — यह कभी नहीं हटेगा") {
                    if (!PanchangWidget.requestPin(context)) {
                        say("होम स्क्रीन पर खाली जगह को दबाकर रखें → विजेट → Banaras Buzz → आज का पंचांग")
                    }
                }
                Text("शुरू होने पर अलग से सूचना (आवाज़ के साथ)", fontSize = 12.5.sp, color = BB.Muted, fontWeight = FontWeight.SemiBold)
                AlertRow(
                    "🌑", "राहुकाल अलर्ट",
                    "आज ${clockShort(w.rahu.start)} – ${clockShort(w.rahu.end)}",
                    rahu,
                ) { toggle(AlertKind.RAHU, it) }
                AlertRow(
                    "✨", "शुभ मुहूर्त (अभिजित) अलर्ट",
                    if (today.dayOfWeek == DayOfWeek.WEDNESDAY) "बुधवार को अभिजित मुहूर्त नहीं माना जाता"
                    else "आज ${clockShort(w.abhijit.start)} – ${clockShort(w.abhijit.end)}",
                    shubh,
                ) { toggle(AlertKind.SHUBH, it) }
                AlertRow("⏰", "10 मिनट पहले भी याद दिलाएँ", "शुरू होने से पहले एक सूचना", early) {
                    early = it
                    AlertPrefs.setEarly(context, it)
                    PanchangAlerts.schedule(context)
                }
                Text(
                    "सूचना में समय की उलटी गिनती चलती है और अवधि खत्म होते ही वह हट जाती है।",
                    fontSize = 12.5.sp,
                    color = BB.Muted,
                )
                if ((sticky || rahu || shubh) && !notifyOk) {
                    Hint("सूचनाएँ बंद हैं। यहाँ टैप करके चालू करें।") {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    }
                }
                if ((sticky || rahu || shubh) && !exactOk && Build.VERSION.SDK_INT >= 31) {
                    Hint("ठीक समय पर अलर्ट के लिए \"अलार्म और रिमाइंडर\" की अनुमति दें। इसके बिना सूचना कुछ मिनट देर से आ सकती है।") {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("ठीक है", color = BB.Saffron) } },
    )
}

@Composable
private fun AlertRow(emoji: String, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BB.Tile)
            .clickable { onChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = BB.Ink, fontSize = 14.5.sp)
            Text(sub, color = BB.Muted, fontSize = 12.5.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = BB.Saffron),
        )
    }
}

@Composable
private fun Hint(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.5.sp,
        color = BB.SaffronDeep,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BB.TileStrong)
            .clickable(onClick = onClick)
            .padding(10.dp),
    )
}
