package com.banarasbuzz.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Title row used by inner screens: optional back arrow, title, trailing slot. */
@Composable
fun ScreenHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = if (onBack == null) 20.dp else 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "वापस", tint = BB.Ink)
            }
        }
        Text(
            title,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = BB.Ink,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

/** Underlined text tabs ("Top Stories | Local | ..."). */
@Composable
fun UnderlineTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            labels.forEachIndexed { i, label ->
                val active = i == selected
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(i) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        fontSize = 14.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) BB.Saffron else BB.InkSoft,
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .height(3.dp)
                            .width(if (active) 36.dp else 0.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(BB.Saffron)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BB.Line))
    }
}

/** Pill chips ("All | Food | Shopping"). */
@Composable
fun PillChips(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            Text(
                label,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (active) Color.White else BB.Ink,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) BB.Saffron else BB.Tile)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Outlined saffron "View Details →" button. */
@Composable
fun OutlineAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        "$label  →",
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = BB.Saffron,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.2.dp, BB.Saffron, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** White rounded card with a soft warm shadow. */
@Composable
fun WarmCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .shadow(6.dp, shape, ambientColor = BB.Saffron.copy(alpha = 0.25f), spotColor = BB.Saffron.copy(alpha = 0.25f))
            .clip(shape)
            .background(BB.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        color = BB.Ink,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        color = BB.Muted,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
    )
}

@Composable
fun MetaLine(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 12.sp, modifier = Modifier.size(18.dp))
        Text(text, fontSize = 13.sp, color = BB.InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
