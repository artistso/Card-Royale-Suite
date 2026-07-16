package com.soquarky.cardtable.protocol

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProtocolTest {
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
    }

    @Test
    fun `command envelope round trips with explicit type`() {
        val command = CommandEnvelope(
            commandId = "command-1",
            matchId = "match-1",
            expectedRevision = 12,
            payload = PlayerCommand.Discard(cardId = 73),
        )

        val encoded = json.encodeToString(CommandEnvelope.serializer(), command)
        val decoded = json.decodeFromString(CommandEnvelope.serializer(), encoded)

        assertEquals(command, decoded)
        assertTrue(encoded.contains("Discard"))
    }
}
