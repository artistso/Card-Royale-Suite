package com.soquarky.cardtable.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun HomeScreen(
    openCanasta: () -> Unit,
    openSolitaire: () -> Unit,
    openBlueprint: () -> Unit,
) {
    ScreenFrame(
        title = "Canasta & Solitaire",
        subtitle = "Human competition at the table. Quiet patience offline.",
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    "CANASTA",
                    letterSpacing = 2.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text("Start a real table", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Two-player duels and four-player partnerships. Every occupied seat belongs to a human player.")
                Button(onClick = openCanasta, modifier = Modifier.height(56.dp)) {
                    Text("Open Canasta lobby")
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MetricCard("14", "Solitaire variants", Modifier.weight(1f))
            MetricCard("0", "AI opponents", Modifier.weight(1f))
            MetricCard("108", "Canasta cards", Modifier.weight(1f))
        }

        Card(Modifier.fillMaxWidth().clickable(onClick = openSolitaire)) {
            Row(
                Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Solitaire Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Klondike, Spider, FreeCell, Pyramid, TriPeaks, and more.")
                }
                Text("→", style = MaterialTheme.typography.headlineMedium)
            }
        }

        Card(Modifier.fillMaxWidth().clickable(onClick = openBlueprint)) {
            Column(Modifier.padding(20.dp)) {
                Text("Technical Blueprint", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Review the native Kotlin modules, authoritative multiplayer boundary, and staged roadmap.")
            }
        }
    }
}
