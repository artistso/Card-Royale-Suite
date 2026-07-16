package com.soquarky.cardtable.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommandEnvelope(
    val commandId: String,
    val matchId: String,
    val expectedRevision: Long,
    val payload: PlayerCommand,
)

@Serializable
sealed interface PlayerCommand {
    @Serializable
    @SerialName("DrawStock")
    data object DrawStock : PlayerCommand

    @Serializable
    @SerialName("CreateMeld")
    data class CreateMeld(val cardIds: List<Int>) : PlayerCommand

    @Serializable
    @SerialName("Discard")
    data class Discard(val cardId: Int) : PlayerCommand
}

@Serializable
data class EventEnvelope(
    val matchId: String,
    val revision: Long,
    val eventId: String,
    val payload: PublicMatchEvent,
)

@Serializable
sealed interface PublicMatchEvent {
    @Serializable
    @SerialName("TurnStarted")
    data class TurnStarted(val playerId: String, val deadlineEpochMs: Long?) : PublicMatchEvent

    @Serializable
    @SerialName("CardDiscarded")
    data class CardDiscarded(val playerId: String, val cardId: Int, val label: String) : PublicMatchEvent

    @Serializable
    @SerialName("HandCountChanged")
    data class HandCountChanged(val playerId: String, val count: Int) : PublicMatchEvent

    @Serializable
    @SerialName("MatchFinished")
    data class MatchFinished(val winningTeamId: String, val finalScores: Map<String, Int>) : PublicMatchEvent
}
