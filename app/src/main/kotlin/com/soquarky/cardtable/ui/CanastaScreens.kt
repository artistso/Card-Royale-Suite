package com.soquarky.cardtable.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.soquarky.cardtable.canasta.CanastaEngine
import com.soquarky.cardtable.canasta.CanastaIntent
import com.soquarky.cardtable.canasta.EngineResult
import com.soquarky.cardtable.canasta.MatchState
import com.soquarky.cardtable.canasta.PlayerId
import com.soquarky.cardtable.canasta.PlayerSeat
import com.soquarky.cardtable.canasta.TeamId
import com.soquarky.cardtable.canasta.TurnPhase

@Composable
internal fun CanastaLobbyScreen(onStart: () -> Unit) {
    var players by rememberSaveable { mutableStateOf(2) }
    var ranked by rememberSaveable { mutableStateOf(false) }
    var privateRoom by rememberSaveable { mutableStateOf(false) }

    ScreenFrame(
        title = "Canasta Lobby",
        subtitle = "Choose a human-only room. Match rules are shown before every player readies up.",
    ) {
        SectionCard("Seats") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = players == 2,
                    onClick = { players = 2 },
                    label = { Text("2-player duel") },
                )
                FilterChip(
                    selected = players == 4,
                    onClick = { players = 4 },
                    label = { Text("4-player partnership") },
                )
            }
        }

        SectionCard("Room type") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = !privateRoom,
                    onClick = { privateRoom = false },
                    label = { Text("Public matchmaking") },
                )
                FilterChip(
                    selected = privateRoom,
                    onClick = { privateRoom = true },
                    label = { Text("Private invite") },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilterChip(
                    selected = !ranked,
                    onClick = { ranked = false },
                    label = { Text("Casual") },
                )
                FilterChip(
                    selected = ranked,
                    onClick = { ranked = true },
                    enabled = !privateRoom,
                    label = { Text("Ranked") },
                )
            }
        }

        SectionCard("Classic rules preview") {
            LabeledValue("Players", players.toString())
            LabeledValue("Starting hand", if (players == 2) "15 cards" else "11 cards")
            LabeledValue("Stock draw", if (players == 2) "2 cards" else "1 card")
            LabeledValue("Target score", "5,000")
            Text("Hand and Foot and custom-room rules remain future rulesets, not hidden variants of Classic Canasta.")
        }

        InfoBanner(
            "No bots",
            "The production room waits for authenticated human players. The local sandbox below pauses when the remote seat becomes active.",
        )

        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Open local table sandbox")
        }
    }
}

@Composable
internal fun CanastaTableScreen(onExit: () -> Unit) {
    val localPlayer = remember { PlayerId("lovely") }
    var state by remember { mutableStateOf(newSandbox()) }
    var selectedIds by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var status by remember { mutableStateOf("Draw from the stock to begin your turn.") }

    fun submit(intent: CanastaIntent) {
        when (val result = CanastaEngine.apply(state, intent)) {
            is EngineResult.Accepted -> {
                state = result.state
                selectedIds = emptySet()
                status = result.events.joinToString { event -> event::class.simpleName ?: "Event" }
            }
            is EngineResult.Rejected -> status = result.message
        }
    }

    val localTurn = state.activePlayer.seat.playerId == localPlayer
    val localHand = state.players.first { it.seat.playerId == localPlayer }.hand
    val selectedCards = localHand.filter { it.id in selectedIds }

    Column(
        Modifier.fillMaxSize().padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Classic Canasta Sandbox", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Revision ${state.revision} • ${state.phase.name.replace('_', ' ')}")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    state = newSandbox()
                    selectedIds = emptySet()
                    status = "Sandbox reset."
                }) { Text("Reset") }
                OutlinedButton(onClick = onExit) { Text("Lobby") }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Opponent", Modifier.weight(1f)) {
                Text("Guest • ${state.players[1].hand.size} cards")
                Text(if (localTurn) "Waiting" else "Remote human turn", fontWeight = FontWeight.Bold)
            }
            SectionCard("Table", Modifier.weight(2f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CardBack(state.stock.size)
                        Text("Stock")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayingCard(state.discardPile.last())
                        Text("Discard")
                    }
                    Column {
                        LabeledValue("Active seat", state.activePlayer.seat.displayName)
                        LabeledValue("Your melds", state.teams.first().melds.size.toString())
                    }
                }
            }
        }

        InfoBanner(
            if (localTurn) "Your turn" else "Waiting for a human player",
            if (localTurn) status else "No AI move is generated. A production WebSocket event will resume this table.",
        )

        Text("Your hand • ${localHand.size} cards", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(localHand, key = { it.id }) { card ->
                PlayingCard(
                    card = card,
                    selected = card.id in selectedIds,
                    onClick = {
                        if (localTurn && state.phase == TurnPhase.MELD_OR_DISCARD) {
                            selectedIds = if (card.id in selectedIds) selectedIds - card.id else selectedIds + card.id
                        }
                    },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                enabled = localTurn && state.phase == TurnPhase.DRAW,
                onClick = { submit(CanastaIntent.DrawStock(localPlayer, state.revision)) },
            ) { Text("Draw stock") }

            Button(
                enabled = localTurn && state.phase == TurnPhase.MELD_OR_DISCARD && selectedIds.size >= 3,
                onClick = { submit(CanastaIntent.MeldCards(localPlayer, state.revision, selectedIds)) },
            ) { Text("Meld selected") }

            Button(
                enabled = localTurn && state.phase == TurnPhase.MELD_OR_DISCARD && selectedCards.size == 1,
                onClick = { submit(CanastaIntent.Discard(localPlayer, state.revision, selectedCards.single().id)) },
            ) { Text("Discard selected") }
        }
    }
}

private fun newSandbox(): MatchState = CanastaEngine.newGame(
    seats = listOf(
        PlayerSeat(0, PlayerId("lovely"), "Lovely", TeamId("lovely")),
        PlayerSeat(1, PlayerId("guest"), "Guest", TeamId("guest")),
    ),
    seed = 83L,
)
