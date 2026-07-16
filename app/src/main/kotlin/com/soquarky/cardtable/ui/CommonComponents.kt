package com.soquarky.cardtable.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soquarky.cardtable.cards.Card
import com.soquarky.cardtable.cards.Suit

@Composable
internal fun ScreenFrame(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Column(verticalArrangement = Arrangement.spacedBy(16.dp), content = content) }
    }
}

@Composable
internal fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
internal fun MetricCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun InfoBanner(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.Black)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PlayingCard(
    card: Card,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val red = card.suit == Suit.HEARTS || card.suit == Suit.DIAMONDS
    val clickableModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Surface(
        modifier = clickableModifier.size(width = 70.dp, height = 98.dp),
        shape = RoundedCornerShape(9.dp),
        color = Color(0xFFF9F7F0),
        border = BorderStroke(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.secondary else Color(0xFF8B918D),
        ),
        shadowElevation = if (selected) 8.dp else 2.dp,
    ) {
        Box(Modifier.padding(7.dp), contentAlignment = Alignment.TopStart) {
            Text(
                card.displayName,
                color = if (red) Color(0xFFB4232D) else Color(0xFF18201D),
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
            )
        }
    }
}

@Composable
internal fun CardBack(count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(width = 70.dp, height = 98.dp),
        shape = RoundedCornerShape(9.dp),
        color = MaterialTheme.colorScheme.primary,
        border = BorderStroke(3.dp, MaterialTheme.colorScheme.onPrimary),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(count.toString(), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
internal fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold)
    }
}
