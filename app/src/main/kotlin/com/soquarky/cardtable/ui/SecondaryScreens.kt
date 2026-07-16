package com.soquarky.cardtable.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun CompeteScreen() {
    ScreenFrame("Compete", "Leaderboards and achievements accept server-verified results only.") {
        SectionCard("Canasta rating") {
            Text("Provisional", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("Complete ten ranked human-versus-human matches to establish a rating.")
        }
        SectionCard("Planned leaderboards") {
            listOf(
                "Canasta rating",
                "Ranked victories",
                "Partnership victories",
                "Longest winning streak",
                "Daily Solitaire challenge",
                "Variants mastered",
            ).forEach { Text("• $it") }
        }
        SectionCard("Achievement plan") {
            listOf("First Canasta", "Natural Talent", "Partnership", "Century Club", "Master of Patience").forEach {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(it)
                    Text("Locked", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        InfoBanner(
            "Integrity",
            "Ranked scores and achievements are submitted after backend verification—not trusted directly from the device.",
        )
    }
}

@Composable
internal fun ProfileScreen() {
    var largeCards by rememberSaveable { mutableStateOf(false) }
    var leftHanded by rememberSaveable { mutableStateOf(false) }
    var suitSymbols by rememberSaveable { mutableStateOf(true) }

    ScreenFrame(
        "Profile",
        "Guest Solitaire is available offline. Online Canasta requires an authenticated player identity.",
    ) {
        SectionCard("Account") {
            Text("Guest", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Google Play Games is represented by an integration boundary, not fake credentials.")
            Button(onClick = {}, enabled = false) { Text("Connect after Play Console setup") }
        }
        SectionCard("Accessibility") {
            SettingToggle("Large cards", largeCards) { largeCards = it }
            SettingToggle("Left-handed controls", leftHanded) { leftHanded = it }
            SettingToggle("Always show suit symbols", suitSymbols) { suitSymbols = it }
        }
        SectionCard("Privacy and safety") {
            Text("• No unrestricted chat at launch")
            Text("• Preset table messages")
            Text("• Match reports and blocking")
            Text("• Account deletion and data export")
        }
    }
}

@Composable
private fun SettingToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Text(
            if (checked) "ON" else "OFF",
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun BlueprintScreen() {
    ScreenFrame(
        "Technical Blueprint",
        "Native Kotlin client, pure rules modules, and an authoritative multiplayer backend.",
    ) {
        SectionCard("Module boundary") {
            Text("app → cards / canasta / solitaire / protocol")
            Text("Pure rule modules have no Android dependency and run under ordinary JVM tests.")
        }
        SectionCard("Server authority") {
            Text("The backend owns shuffle, hidden hands, legality, turn order, score, timers, reconnect state, and final results.")
            Text("Clients submit commands tagged with the expected match revision and an idempotency key.")
        }
        SectionCard("Roadmap") {
            listOf(
                "1. Freeze Classic Canasta rules and tests",
                "2. Complete offline table interactions",
                "3. Private two-player authoritative rooms",
                "4. Four-player partnerships and reconnect",
                "5. Ranked queues, moderation, and seasons",
                "6. Expand the playable Solitaire catalog",
            ).forEach { Text(it) }
        }
        InfoBanner(
            "Current truth",
            "This foundation exposes deliberate rule gaps. It does not present incomplete Canasta behavior as production-ready.",
        )
    }
}
