package com.soquarky.cardtable.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object ProtocolVersion {
    const val CURRENT: Int = 1
}

@Serializable
data class CommandEnvelope(
    val commandId: String,
    val matchId: String,
    val playerId: String,
    val connectionId: String,
    val expectedRevision: Long,
    val payload: PlayerCommand,
    val protocolVersion: Int = ProtocolVersion.CURRENT,
) {
    init {
        require(commandId.isNotBlank()) { "commandId must not be blank" }
        require(matchId.isNotBlank()) { "matchId must not be blank" }
        require(playerId.isNotBlank()) { "playerId must not be blank" }
        require(connectionId.isNotBlank()) { "connectionId must not be blank" }
        require(expectedRevision >= 0) { "expectedRevision must not be negative" }
        require(protocolVersion == ProtocolVersion.CURRENT) { "Unsupported protocol version $protocolVersion" }
    }
}

@Serializable
sealed interface PlayerCommand {
    @Serializable
    @SerialName("DrawStock")
    data object DrawStock : PlayerCommand

    @Serializable
    @SerialName("TakeDiscardPile")
    data class TakeDiscardPile(
        val matchingCardIds: List<Int>,
        val openingMeldGroups: List<List<Int>> = emptyList(),
    ) : PlayerCommand

    @Serializable
    @SerialName("CreateMeld")
    data class CreateMeld(val cardIds: List<Int>) : PlayerCommand

    @Serializable
    @SerialName("OpenMelds")
    data class OpenMelds(val groups: List<List<Int>>) : PlayerCommand

    @Serializable
    @SerialName("Discard")
    data class Discard(val cardId: Int) : PlayerCommand

    @Serializable
    @SerialName("GoOut")
    data class GoOut(
        val meldGroups: List<List<Int>>,
        val discardCardId: Int? = null,
        val partnerPermissionRequestId: String? = null,
    ) : PlayerCommand

    @Serializable
    @SerialName("EndRoundForExhaustedStock")
    data object EndRoundForExhaustedStock : PlayerCommand

    @Serializable
    @SerialName("RequestPartnerPermission")
    data class RequestPartnerPermission(
        val requestId: String,
        val partnerPlayerId: String,
        val expiresAtEpochMs: Long,
    ) : PlayerCommand

    @Serializable
    @SerialName("AnswerPartnerPermission")
    data class AnswerPartnerPermission(
        val requestId: String,
        val granted: Boolean,
    ) : PlayerCommand
}

@Serializable
sealed interface CommandResponse {
    val commandId: String

    @Serializable
    @SerialName("Accepted")
    data class Accepted(
        override val commandId: String,
        val acceptedRevision: Long,
        val eventIds: List<String>,
    ) : CommandResponse

    @Serializable
    @SerialName("Rejected")
    data class Rejected(
        override val commandId: String,
        val code: String,
        val message: String,
        val currentRevision: Long,
        val resyncRequired: Boolean,
    ) : CommandResponse
}

@Serializable
data class CardView(
    val id: Int,
    val rank: String? = null,
    val suit: String? = null,
    val joker: Boolean = false,
    val displayName: String,
)

@Serializable
data class TeamScoreView(
    val teamId: String,
    val roundScore: Int,
    val totalScore: Int,
)

@Serializable
sealed interface ClientMatchEvent

@Serializable
sealed interface PublicMatchEvent : ClientMatchEvent {
    @Serializable
    @SerialName("TurnStarted")
    data class TurnStarted(
        val playerId: String,
        val deadlineEpochMs: Long?,
    ) : PublicMatchEvent

    @Serializable
    @SerialName("CardDiscarded")
    data class CardDiscarded(
        val playerId: String,
        val card: CardView,
    ) : PublicMatchEvent

    @Serializable
    @SerialName("HandCountChanged")
    data class HandCountChanged(
        val playerId: String,
        val count: Int,
    ) : PublicMatchEvent

    @Serializable
    @SerialName("StockCountChanged")
    data class StockCountChanged(val count: Int) : PublicMatchEvent

    @Serializable
    @SerialName("DiscardPileChanged")
    data class DiscardPileChanged(
        val cards: List<CardView>,
        val frozen: Boolean,
    ) : PublicMatchEvent

    @Serializable
    @SerialName("MeldChanged")
    data class MeldChanged(
        val teamId: String,
        val rank: String,
        val cards: List<CardView>,
        val naturalCanasta: Boolean,
        val mixedCanasta: Boolean,
    ) : PublicMatchEvent

    @Serializable
    @SerialName("RedThreesChanged")
    data class RedThreesChanged(
        val teamId: String,
        val cards: List<CardView>,
    ) : PublicMatchEvent

    @Serializable
    @SerialName("RoundFinished")
    data class RoundFinished(
        val reason: String,
        val winnerPlayerId: String?,
        val scores: List<TeamScoreView>,
    ) : PublicMatchEvent

    @Serializable
    @SerialName("PartnerPermissionResolved")
    data class PartnerPermissionResolved(
        val requestId: String,
        val requestingPlayerId: String,
        val partnerPlayerId: String,
        val status: String,
    ) : PublicMatchEvent

    @Serializable
    @SerialName("MatchFinished")
    data class MatchFinished(
        val winningTeamId: String,
        val finalScores: Map<String, Int>,
    ) : PublicMatchEvent
}

@Serializable
sealed interface PrivateMatchEvent : ClientMatchEvent {
    @Serializable
    @SerialName("HandReplaced")
    data class HandReplaced(
        val playerId: String,
        val cards: List<CardView>,
    ) : PrivateMatchEvent

    @Serializable
    @SerialName("PartnerPermissionPrompt")
    data class PartnerPermissionPrompt(
        val requestId: String,
        val requestingPlayerId: String,
        val expiresAtEpochMs: Long,
    ) : PrivateMatchEvent
}

@Serializable
data class EventEnvelope(
    val matchId: String,
    val revision: Long,
    val sequence: Long,
    val eventId: String,
    val commandId: String,
    val payload: ClientMatchEvent,
)

@Serializable
data class ReconnectRequest(
    val matchId: String,
    val playerId: String,
    val connectionId: String,
    val lastSeenSequence: Long?,
    val lastKnownRevision: Long?,
    val protocolVersion: Int = ProtocolVersion.CURRENT,
) {
    init {
        require(matchId.isNotBlank())
        require(playerId.isNotBlank())
        require(connectionId.isNotBlank())
        require(lastSeenSequence == null || lastSeenSequence >= 0)
        require(lastKnownRevision == null || lastKnownRevision >= 0)
        require(protocolVersion == ProtocolVersion.CURRENT)
    }
}

@Serializable
sealed interface ReconnectResponse {
    val latestSequence: Long

    @Serializable
    @SerialName("Snapshot")
    data class Snapshot(
        val snapshot: MatchSnapshot,
        override val latestSequence: Long,
    ) : ReconnectResponse

    @Serializable
    @SerialName("Replay")
    data class Replay(
        val events: List<EventEnvelope>,
        override val latestSequence: Long,
    ) : ReconnectResponse
}
