package com.soquarky.cardtable.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soquarky.cardtable.solitaire.Difficulty
import com.soquarky.cardtable.solitaire.KlondikeDealer
import com.soquarky.cardtable.solitaire.SolitaireCatalog
import com.soquarky.cardtable.solitaire.SolitaireVariant

@Composable
internal fun SolitaireLibraryScreen() {
    var difficulty by rememberSaveable { mutableStateOf<Difficulty?>(null) }
    var selected by remember { mutableStateOf<SolitaireVariant?>(null) }
    val filtered = SolitaireCatalog.variants.filter { difficulty == null || it.difficulty == difficulty }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Solitaire Library", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
        Text("Offline, deterministic, and free of AI opponents.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(difficulty == null, onClick = { difficulty = null }, label = { Text("All") })
            Difficulty.entries.forEach { level ->
                FilterChip(
                    selected = difficulty == level,
                    onClick = { difficulty = level },
                    label = { Text(level.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(filtered, key = SolitaireVariant::id) { variant ->
                Card(Modifier.clickable { selected = variant }) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(variant.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${variant.difficulty.name} • ${variant.deckCount} deck${if (variant.deckCount > 1) "s" else ""}")
                        Text(variant.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${variant.estimatedMinutes.first}–${variant.estimatedMinutes.last} min",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(if (variant.dailyChallengeEligible) "Daily challenge eligible" else "Classic play only")
                    }
                }
            }
        }
    }

    selected?.let { variant ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(variant.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(variant.summary)
                    LabeledValue("Difficulty", variant.difficulty.name.lowercase())
                    LabeledValue("Decks", variant.deckCount.toString())
                    if (variant.id == "klondike") {
                        KlondikePreview()
                    } else {
                        Text("The catalog entry is live. Its full rules engine follows the Classic Canasta rules freeze.")
                    }
                }
            },
            confirmButton = { Button(onClick = { selected = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun KlondikePreview() {
    val deal = remember { KlondikeDealer.deal(83L) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Deterministic deal preview", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            deal.tableau.forEach { column ->
                Surface(
                    modifier = Modifier.size(width = 32.dp, height = (38 + column.size * 8).dp),
                    shape = RoundedCornerShape(5.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.BottomCenter) {
                        Text(column.last().card.displayName, fontSize = 10.sp, modifier = Modifier.padding(3.dp))
                    }
                }
            }
        }
        Text("Tableau: 28 cards • Stock: ${deal.stock.size} cards")
    }
}
