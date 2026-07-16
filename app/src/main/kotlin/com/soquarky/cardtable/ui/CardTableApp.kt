package com.soquarky.cardtable.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class Destination(val label: String, val glyph: String) {
    HOME("Home", "⌂"),
    CANASTA("Canasta", "C"),
    SOLITAIRE("Solitaire", "S"),
    COMPETE("Compete", "★"),
    PROFILE("Profile", "●"),
    BLUEPRINT("Blueprint", "≡"),
}

@Composable
fun CardTableApp() {
    val tablet = LocalConfiguration.current.screenWidthDp >= 760
    var destination by rememberSaveable { mutableStateOf(Destination.HOME) }
    var canastaTableOpen by rememberSaveable { mutableStateOf(false) }

    fun navigate(target: Destination, openTable: Boolean = false) {
        destination = target
        canastaTableOpen = target == Destination.CANASTA && openTable
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (tablet) {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
                    Spacer(Modifier.height(16.dp))
                    Text("C&S", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Destination.entries.forEach { item ->
                        NavigationRailItem(
                            selected = item == destination,
                            onClick = { navigate(item) },
                            icon = { Text(item.glyph, fontWeight = FontWeight.Bold) },
                            label = { Text(item.label) },
                        )
                    }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    AppContent(destination, canastaTableOpen, ::navigate)
                }
            }
        } else {
            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(Modifier.navigationBarsPadding()) {
                        Destination.entries.take(5).forEach { item ->
                            NavigationBarItem(
                                selected = item == destination,
                                onClick = { navigate(item) },
                                icon = { Text(item.glyph, fontWeight = FontWeight.Bold) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    AppContent(destination, canastaTableOpen, ::navigate)
                }
            }
        }
    }
}

@Composable
private fun AppContent(
    destination: Destination,
    canastaTableOpen: Boolean,
    navigate: (Destination, Boolean) -> Unit,
) {
    when (destination) {
        Destination.HOME -> HomeScreen(
            openCanasta = { navigate(Destination.CANASTA, false) },
            openSolitaire = { navigate(Destination.SOLITAIRE, false) },
            openBlueprint = { navigate(Destination.BLUEPRINT, false) },
        )
        Destination.CANASTA -> if (canastaTableOpen) {
            CanastaTableScreen(onExit = { navigate(Destination.CANASTA, false) })
        } else {
            CanastaLobbyScreen(onStart = { navigate(Destination.CANASTA, true) })
        }
        Destination.SOLITAIRE -> SolitaireLibraryScreen()
        Destination.COMPETE -> CompeteScreen()
        Destination.PROFILE -> ProfileScreen()
        Destination.BLUEPRINT -> BlueprintScreen()
    }
}
