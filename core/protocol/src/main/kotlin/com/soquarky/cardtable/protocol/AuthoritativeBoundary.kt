package com.soquarky.cardtable.protocol

import com.soquarky.cardtable.canasta.CanastaEvent
import com.soquarky.cardtable.canasta.CanastaIntent
import com.soquarky.cardtable.canasta.MatchState
import com.soquarky.cardtable.canasta.PartnerPermission
import com.soquarky.cardtable.canasta.PlayerId
import com.soquarky.cardtable.cards.Card

sealed interface EventAudience {
    data object AllPlayers : EventAudience
    data class Player(val playerId: String) : EventAudience
    data class Team(val teamId: String) : EventAudience
}

data class EventEmission(
    val audience: EventAudience,
    val payload: ClientMatchEvent,
)

private data class StoredEvent(
    val sequence: Long,
    val eventId: String,
    val commandId: String,
    val revision: Long,
    val audience: EventAudience,
    val payload: ClientMatchEvent,
)

object EngineEventProjector {
    fun project(
        before: MatchState,
        after: MatchState,
        engineEvents: List<CanastaEvent>,
    ): List<EventEmission> = buildList {
        val beforePlayers = before.players.associateBy { it.seat.playerId }
        after.players.sortedBy { it.seat.index }.forEach { player ->
            val previous = beforePlayers.getValue(player.seat.playerId)
            if (previous.hand != player.hand) {
                add(
                    EventEmission(
                        EventAudience.AllPlayers,
                        PublicMatchEvent.HandCountChanged(player.seat.playerId.value, player.hand.size),
                    ),
                )
                add(
                    EventEmission(
                        EventAudience.Player(player.seat.playerId.value),
                        PrivateMatchEvent.HandReplaced(player.seat.playerId.value, player.hand.map(Card::toView)),
                    ),
                )
            }
        }

        if (before.stock.size != after.stock.size) {
            add(EventEmission(EventAudience.AllPlayers, PublicMatchEvent.StockCountChanged(after.stock.size)))
        }
        if (before.discardPile != after.discardPile || before.discardPileFrozen != after.discardPileFrozen) {
            add(
                EventEmission(
                    EventAudience.AllPlayers,
                    PublicMatchEvent.DiscardPileChanged(
                        cards = after.discardPile.map(Card::toView),
                        frozen = after.discardPileFrozen,
                    ),
                ),
            )
        }

        val beforeTeams = before.teams.associateBy { it.id }
        after.teams.sortedBy { it.id.value }.forEach { team ->
            val previous = beforeTeams.getValue(team.id)
            val previousMelds = previous.melds.associateBy { it.rank }
            team.melds.sortedBy { it.rank.order }.forEach { meld ->
                if (previousMelds[meld.rank] != meld) {
                    add(
                        EventEmission(
                            EventAudience.AllPlayers,
                            PublicMatchEvent.MeldChanged(
                                teamId = team.id.value,
                                rank = meld.rank.name,
                                cards = meld.cards.map(Card::toView),
                                naturalCanasta = meld.isNatural,
                                mixedCanasta = meld.isMixed,
                            ),
                        ),
                    )
                }
            }
            if (previous.redThrees != team.redThrees) {
                add(
                    EventEmission(
                        EventAudience.AllPlayers,
                        PublicMatchEvent.RedThreesChanged(team.id.value, team.redThrees.map(Card::toView)),
                    ),
                )
            }
        }

        engineEvents.filterIsInstance<CanastaEvent.CardDiscarded>().forEach { event ->
            add(
                EventEmission(
                    EventAudience.AllPlayers,
                    PublicMatchEvent.CardDiscarded(event.playerId.value, event.card.toView()),
                ),
            )
        }

        after.roundResult?.takeIf { before.roundResult != it }?.let { result ->
            add(
                EventEmission(
                    EventAudience.AllPlayers,
                    PublicMatchEvent.RoundFinished(
                        reason = result.reason.name,
                        winnerPlayerId = result.winnerPlayerId?.value,
                        scores = result.scores.map { score ->
                            TeamScoreView(score.teamId.value, score.roundScore, score.total)
                        },
                    ),
                ),
            )
        }
    }
}

class AuthoritativeEventLedger(
    val matchId: String,
    private val retentionLimit: Int = 512,
) {
    private val records = mutableListOf<StoredEvent>()
    private var nextSequence = 1L

    init {
        require(matchId.isNotBlank())
        require(retentionLimit > 0)
    }

    val latestSequence: Long
        get() = records.lastOrNull()?.sequence ?: 0L

    val earliestSequence: Long
        get() = records.firstOrNull()?.sequence ?: (latestSequence + 1L)

    fun append(
        commandId: String,
        revision: Long,
        emissions: List<EventEmission>,
    ): List<String> {
        require(commandId.isNotBlank())
        require(revision >= 0)
        if (records.isNotEmpty()) {
            require(revision >= records.last().revision) { "Event revisions must be monotonic" }
        }

        val appended = emissions.mapIndexed { index, emission ->
            StoredEvent(
                sequence = nextSequence++,
                eventId = "$matchId:$revision:$commandId:${index + 1}",
                commandId = commandId,
                revision = revision,
                audience = emission.audience,
                payload = emission.payload,
            )
        }
        records += appended
        while (records.size > retentionLimit) records.removeAt(0)
        return appended.map(StoredEvent::eventId)
    }

    fun canReplayFrom(lastSeenSequence: Long): Boolean {
        if (lastSeenSequence < 0 || lastSeenSequence > latestSequence) return false
        if (records.isEmpty()) return lastSeenSequence == 0L
        return lastSeenSequence >= earliestSequence - 1L
    }

    fun eventsAfter(
        lastSeenSequence: Long,
        viewerPlayerId: String,
        viewerTeamId: String,
    ): List<EventEnvelope> {
        require(canReplayFrom(lastSeenSequence)) { "Requested sequence is outside retained history" }
        return records.asSequence()
            .filter { it.sequence > lastSeenSequence }
            .filter { it.audience.visibleTo(viewerPlayerId, viewerTeamId) }
            .map { event ->
                EventEnvelope(
                    matchId = matchId,
                    revision = event.revision,
                    sequence = event.sequence,
                    eventId = event.eventId,
                    commandId = event.commandId,
                    payload = event.payload,
                )
            }
            .toList()
    }

    private fun EventAudience.visibleTo(playerId: String, teamId: String): Boolean = when (this) {
        EventAudience.AllPlayers -> true
        is EventAudience.Player -> this.playerId == playerId
        is EventAudience.Team -> this.teamId == teamId
    }
}

object ReconnectCoordinator {
    fun resume(
        request: ReconnectRequest,
        currentSnapshot: MatchSnapshot,
        viewerTeamId: String,
        ledger: AuthoritativeEventLedger,
    ): ReconnectResponse {
        require(request.matchId == currentSnapshot.matchId)
        require(request.matchId == ledger.matchId)
        require(request.playerId == currentSnapshot.viewerPlayerId)

        val cursor = request.lastSeenSequence
        val revisionIsPlausible = request.lastKnownRevision == null || request.lastKnownRevision <= currentSnapshot.revision
        return if (cursor == null || !revisionIsPlausible || !ledger.canReplayFrom(cursor)) {
            ReconnectResponse.Snapshot(currentSnapshot, ledger.latestSequence)
        } else {
            ReconnectResponse.Replay(
                events = ledger.eventsAfter(cursor, request.playerId, viewerTeamId),
                latestSequence = ledger.latestSequence,
            )
        }
    }
}

sealed interface IdempotentCommandResult {
    val response: CommandResponse?

    data class Fresh(override val response: CommandResponse) : IdempotentCommandResult
    data class Duplicate(override val response: CommandResponse) : IdempotentCommandResult
    data class Conflict(val commandId: String) : IdempotentCommandResult {
        override val response: CommandResponse? = null
    }
}

class CommandIdempotencyStore {
    private data class Entry(
        val envelope: CommandEnvelope,
        val response: CommandResponse,
    )

    private val entries = mutableMapOf<String, Entry>()

    fun execute(
        envelope: CommandEnvelope,
        handler: () -> CommandResponse,
    ): IdempotentCommandResult {
        val existing = entries[envelope.commandId]
        if (existing != null) {
            return if (existing.envelope == envelope) {
                IdempotentCommandResult.Duplicate(existing.response)
            } else {
                IdempotentCommandResult.Conflict(envelope.commandId)
            }
        }
        val response = handler()
        entries[envelope.commandId] = Entry(envelope, response)
        return IdempotentCommandResult.Fresh(response)
    }
}

enum class PartnerPermissionStatus {
    PENDING,
    GRANTED,
    DENIED,
    EXPIRED,
}

data class PartnerPermissionRecord(
    val requestId: String,
    val matchId: String,
    val requestingPlayerId: String,
    val partnerPlayerId: String,
    val requestedAtRevision: Long,
    val expiresAtEpochMs: Long,
    val status: PartnerPermissionStatus,
)

class PartnerPermissionCoordinator {
    private val records = mutableMapOf<String, PartnerPermissionRecord>()

    fun request(
        requestId: String,
        matchId: String,
        requestingPlayerId: String,
        partnerPlayerId: String,
        requestedAtRevision: Long,
        expiresAtEpochMs: Long,
        nowEpochMs: Long,
    ): PartnerPermissionRecord {
        require(requestId.isNotBlank())
        require(matchId.isNotBlank())
        require(requestingPlayerId.isNotBlank())
        require(partnerPlayerId.isNotBlank())
        require(requestingPlayerId != partnerPlayerId)
        require(requestedAtRevision >= 0)
        require(expiresAtEpochMs > nowEpochMs)
        require(requestId !in records) { "Permission request IDs are immutable" }

        return PartnerPermissionRecord(
            requestId = requestId,
            matchId = matchId,
            requestingPlayerId = requestingPlayerId,
            partnerPlayerId = partnerPlayerId,
            requestedAtRevision = requestedAtRevision,
            expiresAtEpochMs = expiresAtEpochMs,
            status = PartnerPermissionStatus.PENDING,
        ).also { records[requestId] = it }
    }

    fun answer(
        requestId: String,
        respondingPlayerId: String,
        granted: Boolean,
        nowEpochMs: Long,
    ): PartnerPermissionRecord {
        val current = requireNotNull(records[requestId]) { "Unknown permission request" }
        require(current.partnerPlayerId == respondingPlayerId) { "Only the named partner may answer" }
        require(current.status == PartnerPermissionStatus.PENDING) { "Permission request is already resolved" }

        val status = if (nowEpochMs >= current.expiresAtEpochMs) {
            PartnerPermissionStatus.EXPIRED
        } else if (granted) {
            PartnerPermissionStatus.GRANTED
        } else {
            PartnerPermissionStatus.DENIED
        }
        return current.copy(status = status).also { records[requestId] = it }
    }

    fun status(requestId: String, nowEpochMs: Long): PartnerPermissionStatus? {
        val current = records[requestId] ?: return null
        if (current.status == PartnerPermissionStatus.PENDING && nowEpochMs >= current.expiresAtEpochMs) {
            val expired = current.copy(status = PartnerPermissionStatus.EXPIRED)
            records[requestId] = expired
            return expired.status
        }
        return current.status
    }

    fun promptEmission(record: PartnerPermissionRecord): EventEmission = EventEmission(
        audience = EventAudience.Player(record.partnerPlayerId),
        payload = PrivateMatchEvent.PartnerPermissionPrompt(
            requestId = record.requestId,
            requestingPlayerId = record.requestingPlayerId,
            expiresAtEpochMs = record.expiresAtEpochMs,
        ),
    )

    fun resolutionEmission(record: PartnerPermissionRecord): EventEmission = EventEmission(
        audience = EventAudience.AllPlayers,
        payload = PublicMatchEvent.PartnerPermissionResolved(
            requestId = record.requestId,
            requestingPlayerId = record.requestingPlayerId,
            partnerPlayerId = record.partnerPlayerId,
            status = record.status.name,
        ),
    )
}

sealed interface CommandRoutingResult {
    data class Engine(val intent: CanastaIntent) : CommandRoutingResult
    data class Boundary(val command: PlayerCommand) : CommandRoutingResult
    data class Rejected(val code: String, val message: String) : CommandRoutingResult
}

object CanastaCommandRouter {
    fun route(
        envelope: CommandEnvelope,
        permissions: PartnerPermissionCoordinator,
        nowEpochMs: Long,
    ): CommandRoutingResult {
        val playerId = PlayerId(envelope.playerId)
        val revision = envelope.expectedRevision
        return when (val command = envelope.payload) {
            PlayerCommand.DrawStock -> CommandRoutingResult.Engine(CanastaIntent.DrawStock(playerId, revision))
            is PlayerCommand.TakeDiscardPile -> CommandRoutingResult.Engine(
                CanastaIntent.TakeDiscardPile(
                    playerId = playerId,
                    expectedRevision = revision,
                    matchingCardIds = command.matchingCardIds.toSet(),
                    openingMeldGroups = command.openingMeldGroups.map { it.toSet() },
                ),
            )
            is PlayerCommand.CreateMeld -> CommandRoutingResult.Engine(
                CanastaIntent.MeldCards(playerId, revision, command.cardIds.toSet()),
            )
            is PlayerCommand.OpenMelds -> CommandRoutingResult.Engine(
                CanastaIntent.OpenMelds(playerId, revision, command.groups.map { it.toSet() }),
            )
            is PlayerCommand.Discard -> CommandRoutingResult.Engine(
                CanastaIntent.Discard(playerId, revision, command.cardId),
            )
            is PlayerCommand.GoOut -> routeGoOut(envelope, command, permissions, nowEpochMs)
            PlayerCommand.EndRoundForExhaustedStock -> CommandRoutingResult.Engine(
                CanastaIntent.EndRoundForExhaustedStock(playerId, revision),
            )
            is PlayerCommand.RequestPartnerPermission,
            is PlayerCommand.AnswerPartnerPermission,
            -> CommandRoutingResult.Boundary(command)
        }
    }

    private fun routeGoOut(
        envelope: CommandEnvelope,
        command: PlayerCommand.GoOut,
        permissions: PartnerPermissionCoordinator,
        nowEpochMs: Long,
    ): CommandRoutingResult {
        val permission = when (val requestId = command.partnerPermissionRequestId) {
            null -> PartnerPermission.NOT_ASKED
            else -> when (permissions.status(requestId, nowEpochMs)) {
                PartnerPermissionStatus.GRANTED -> PartnerPermission.GRANTED
                PartnerPermissionStatus.DENIED,
                PartnerPermissionStatus.EXPIRED,
                -> PartnerPermission.DENIED
                PartnerPermissionStatus.PENDING -> return CommandRoutingResult.Rejected(
                    "PARTNER_PERMISSION_PENDING",
                    "The partner permission request has not been answered",
                )
                null -> return CommandRoutingResult.Rejected(
                    "PARTNER_PERMISSION_UNKNOWN",
                    "The partner permission request does not exist",
                )
            }
        }
        return CommandRoutingResult.Engine(
            CanastaIntent.GoOut(
                playerId = PlayerId(envelope.playerId),
                expectedRevision = envelope.expectedRevision,
                meldGroups = command.meldGroups.map { it.toSet() },
                discardCardId = command.discardCardId,
                partnerPermission = permission,
            ),
        )
    }
}
