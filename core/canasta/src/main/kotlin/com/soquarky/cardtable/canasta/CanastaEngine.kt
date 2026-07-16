package com.soquarky.cardtable.canasta

import com.soquarky.cardtable.cards.Card
import com.soquarky.cardtable.cards.DeckFactory
import com.soquarky.cardtable.cards.DeterministicShuffle
import com.soquarky.cardtable.cards.Rank
import com.soquarky.cardtable.cards.Suit

@JvmInline
value class PlayerId(val value: String)

@JvmInline
value class TeamId(val value: String)

data class PlayerSeat(
    val index: Int,
    val playerId: PlayerId,
    val displayName: String,
    val teamId: TeamId,
)

data class CanastaRules(
    val playerCount: Int,
    val handSize: Int,
    val drawCount: Int,
    val canastaSize: Int = 7,
    val targetScore: Int = 5_000,
    val requiredCanastasToGoOut: Int,
    val naturalCanastaBonus: Int = 500,
    val mixedCanastaBonus: Int = 300,
    val goingOutBonus: Int = 100,
    val concealedOutBonus: Int = 100,
) {
    init {
        require(playerCount == 2 || playerCount == 4) { "Classic rooms support 2 or 4 players" }
        require(handSize > 0)
        require(drawCount > 0)
        require(canastaSize >= 7)
        require(requiredCanastasToGoOut > 0)
    }

    companion object {
        fun classic(playerCount: Int): CanastaRules = when (playerCount) {
            2 -> CanastaRules(playerCount = 2, handSize = 15, drawCount = 2, requiredCanastasToGoOut = 2)
            4 -> CanastaRules(playerCount = 4, handSize = 11, drawCount = 1, requiredCanastasToGoOut = 1)
            else -> error("Classic rooms support 2 or 4 players")
        }
    }
}

enum class TurnPhase {
    DRAW,
    MELD_OR_DISCARD,
    ROUND_OVER,
}

enum class DrawSource {
    NONE,
    STOCK,
    DISCARD_PILE,
}

enum class PartnerPermission {
    NOT_ASKED,
    GRANTED,
    DENIED,
}

enum class RoundEndReason {
    PLAYER_WENT_OUT,
    STOCK_EXHAUSTED,
    LAST_STOCK_CARD_WAS_RED_THREE,
}

data class PlayerState(
    val seat: PlayerSeat,
    val hand: List<Card>,
)

data class Meld(
    val rank: Rank,
    val cards: List<Card>,
) {
    val isCanasta: Boolean get() = cards.size >= 7
    val isNatural: Boolean get() = isCanasta && cards.none(Card::isWild)
    val isMixed: Boolean get() = isCanasta && cards.any(Card::isWild)
}

data class TeamState(
    val id: TeamId,
    val melds: List<Meld> = emptyList(),
    val redThrees: List<Card> = emptyList(),
    val roundScore: Int = 0,
    val totalScore: Int = 0,
) {
    val hasOpened: Boolean get() = melds.isNotEmpty()
    val canastaCount: Int get() = melds.count(Meld::isCanasta)
    val hasCanasta: Boolean get() = canastaCount > 0
}

data class RoundScore(
    val teamId: TeamId,
    val meldCardPoints: Int,
    val handPenalty: Int,
    val canastaBonus: Int,
    val redThreeScore: Int,
    val goingOutScore: Int,
    val total: Int,
)

data class RoundResult(
    val reason: RoundEndReason,
    val winnerPlayerId: PlayerId? = null,
    val concealedOut: Boolean = false,
    val scores: List<RoundScore>,
)

data class MatchState(
    val rules: CanastaRules,
    val players: List<PlayerState>,
    val teams: List<TeamState>,
    val stock: List<Card>,
    val discardPile: List<Card>,
    val discardPileFrozen: Boolean,
    val activeSeatIndex: Int,
    val phase: TurnPhase,
    val revision: Long,
    val turnDrawSource: DrawSource = DrawSource.NONE,
    val roundResult: RoundResult? = null,
) {
    val activePlayer: PlayerState get() = players.first { it.seat.index == activeSeatIndex }
    val activeTeam: TeamState get() = teams.first { it.id == activePlayer.seat.teamId }
}

sealed interface CanastaIntent {
    val playerId: PlayerId
    val expectedRevision: Long

    data class DrawStock(
        override val playerId: PlayerId,
        override val expectedRevision: Long,
    ) : CanastaIntent

    data class TakeDiscardPile(
        override val playerId: PlayerId,
        override val expectedRevision: Long,
        val matchingCardIds: Set<Int>,
        val openingMeldGroups: List<Set<Int>> = emptyList(),
    ) : CanastaIntent

    data class OpenMelds(
        override val playerId: PlayerId,
        override val expectedRevision: Long,
        val groups: List<Set<Int>>,
    ) : CanastaIntent

    data class MeldCards(
        override val playerId: PlayerId,
        override val expectedRevision: Long,
        val cardIds: Set<Int>,
    ) : CanastaIntent

    data class Discard(
        override val playerId: PlayerId,
        override val expectedRevision: Long,
        val cardId: Int,
    ) : CanastaIntent

    data class GoOut(
        override val playerId: PlayerId,
        override val expectedRevision: Long,
        val meldGroups: List<Set<Int>>,
        val discardCardId: Int? = null,
        val partnerPermission: PartnerPermission = PartnerPermission.NOT_ASKED,
    ) : CanastaIntent

    data class EndRoundForExhaustedStock(
        override val playerId: PlayerId,
        override val expectedRevision: Long,
    ) : CanastaIntent
}

sealed interface CanastaEvent {
    val revision: Long

    data class RedThreesExposed(
        override val revision: Long,
        val playerId: PlayerId,
        val cardIds: List<Int>,
    ) : CanastaEvent

    data class StockDrawn(
        override val revision: Long,
        val playerId: PlayerId,
        val count: Int,
    ) : CanastaEvent

    data class DiscardPileTaken(
        override val revision: Long,
        val playerId: PlayerId,
        val count: Int,
        val topCardId: Int,
    ) : CanastaEvent

    data class MeldCreated(
        override val revision: Long,
        val playerId: PlayerId,
        val teamId: TeamId,
        val rank: Rank,
        val cardIds: List<Int>,
    ) : CanastaEvent

    data class MeldExtended(
        override val revision: Long,
        val playerId: PlayerId,
        val teamId: TeamId,
        val rank: Rank,
        val cardIds: List<Int>,
    ) : CanastaEvent

    data class CardDiscarded(
        override val revision: Long,
        val playerId: PlayerId,
        val card: Card,
    ) : CanastaEvent

    data class RoundEnded(
        override val revision: Long,
        val reason: RoundEndReason,
        val winnerPlayerId: PlayerId?,
        val scores: List<RoundScore>,
    ) : CanastaEvent
}

sealed interface EngineResult {
    data class Accepted(
        val state: MatchState,
        val events: List<CanastaEvent>,
    ) : EngineResult

    data class Rejected(
        val state: MatchState,
        val code: RejectionCode,
        val message: String,
    ) : EngineResult
}

enum class RejectionCode {
    STALE_REVISION,
    NOT_YOUR_TURN,
    ILLEGAL_PHASE,
    STOCK_EXHAUSTED,
    CARD_NOT_IN_HAND,
    ILLEGAL_MELD,
    INITIAL_MELD_TOO_SMALL,
    DISCARD_PILE_BLOCKED,
    DISCARD_PICKUP_REQUIREMENT,
    MUST_GO_OUT_EXPLICITLY,
    GOING_OUT_REQUIRES_CANASTA,
    PARTNER_DENIED_GOING_OUT,
    STOCK_STILL_AVAILABLE,
    DISCARD_PICKUP_AVAILABLE,
    ROUND_ALREADY_OVER,
}

object CanastaEngine {
    fun newGame(
        seats: List<PlayerSeat>,
        seed: Long,
        rules: CanastaRules = CanastaRules.classic(seats.size),
    ): MatchState {
        validateSeats(seats, rules)
        val deck = DeterministicShuffle.shuffle(DeckFactory.classicCanasta(), seed)
        val orderedSeats = seats.sortedBy(PlayerSeat::index)
        val hands = orderedSeats.associateWith { mutableListOf<Card>() }
        var cursor = 0

        repeat(rules.handSize) {
            orderedSeats.forEach { seat -> hands.getValue(seat).add(deck[cursor++]) }
        }

        val redThreesByTeam = mutableMapOf<TeamId, MutableList<Card>>()
        orderedSeats.forEach { seat ->
            val hand = hands.getValue(seat)
            var index = 0
            while (index < hand.size) {
                if (hand[index].isRedThree) {
                    redThreesByTeam.getOrPut(seat.teamId, ::mutableListOf).add(hand.removeAt(index))
                    while (cursor < deck.size) {
                        val replacement = deck[cursor++]
                        if (replacement.isRedThree) {
                            redThreesByTeam.getOrPut(seat.teamId, ::mutableListOf).add(replacement)
                        } else {
                            hand.add(replacement)
                            break
                        }
                    }
                } else {
                    index++
                }
            }
        }

        val openingCards = mutableListOf<Card>()
        var frozen = false
        while (cursor < deck.size) {
            val card = deck[cursor++]
            openingCards += card
            if (card.isWild || card.isRedThree) frozen = true
            if (!card.isWild && !card.isRedThree) break
        }
        require(openingCards.isNotEmpty()) { "A Canasta game requires an opening discard" }

        val players = orderedSeats.map { seat -> PlayerState(seat, hands.getValue(seat).toList()) }
        val teams = orderedSeats.map(PlayerSeat::teamId).distinct().map { teamId ->
            TeamState(id = teamId, redThrees = redThreesByTeam[teamId].orEmpty())
        }

        return MatchState(
            rules = rules,
            players = players,
            teams = teams,
            stock = deck.drop(cursor),
            discardPile = openingCards,
            discardPileFrozen = frozen,
            activeSeatIndex = 0,
            phase = TurnPhase.DRAW,
            revision = 0,
        )
    }

    fun apply(state: MatchState, intent: CanastaIntent): EngineResult {
        if (state.phase == TurnPhase.ROUND_OVER) {
            return state.rejected(RejectionCode.ROUND_ALREADY_OVER, "The round has ended")
        }
        if (intent.expectedRevision != state.revision) {
            return state.rejected(RejectionCode.STALE_REVISION, "Expected revision ${state.revision}")
        }
        if (intent.playerId != state.activePlayer.seat.playerId) {
            return state.rejected(RejectionCode.NOT_YOUR_TURN, "It is ${state.activePlayer.seat.displayName}'s turn")
        }

        return when (intent) {
            is CanastaIntent.DrawStock -> drawStock(state, intent)
            is CanastaIntent.TakeDiscardPile -> takeDiscardPile(state, intent)
            is CanastaIntent.OpenMelds -> openMelds(state, intent)
            is CanastaIntent.MeldCards -> meldCards(state, intent)
            is CanastaIntent.Discard -> discard(state, intent)
            is CanastaIntent.GoOut -> goOut(state, intent)
            is CanastaIntent.EndRoundForExhaustedStock -> endRoundForExhaustedStock(state, intent)
        }
    }

    private fun drawStock(state: MatchState, intent: CanastaIntent.DrawStock): EngineResult {
        if (state.phase != TurnPhase.DRAW) {
            return state.rejected(RejectionCode.ILLEGAL_PHASE, "A stock draw is only legal at the start of a turn")
        }
        if (state.stock.isEmpty()) {
            return state.rejected(RejectionCode.STOCK_EXHAUSTED, "The stock is exhausted; take the discard pile or end the round")
        }

        var stock = state.stock
        val drawn = mutableListOf<Card>()
        val redThrees = mutableListOf<Card>()
        var terminalRedThree = false

        repeat(state.rules.drawCount) {
            if (stock.isEmpty()) return@repeat
            var card = stock.first()
            stock = stock.drop(1)
            while (card.isRedThree) {
                redThrees += card
                if (stock.isEmpty()) {
                    terminalRedThree = drawn.isEmpty()
                    break
                }
                card = stock.first()
                stock = stock.drop(1)
            }
            if (!terminalRedThree && !card.isRedThree) drawn += card
        }

        val player = state.activePlayer
        val players = state.players.updateActive(state) { copy(hand = hand + drawn) }
        val teams = state.teams.map { team ->
            if (team.id == player.seat.teamId) team.copy(redThrees = team.redThrees + redThrees) else team
        }
        val nextRevision = state.revision + 1
        val baseState = state.copy(players = players, teams = teams, stock = stock, revision = nextRevision)

        if (terminalRedThree) {
            return finishRound(
                state = baseState,
                reason = RoundEndReason.LAST_STOCK_CARD_WAS_RED_THREE,
                winnerPlayerId = null,
                concealedOut = false,
                extraEvents = redThreeEvent(nextRevision, intent.playerId, redThrees),
            )
        }

        val nextState = baseState.copy(phase = TurnPhase.MELD_OR_DISCARD, turnDrawSource = DrawSource.STOCK)
        return EngineResult.Accepted(
            state = nextState,
            events = buildList {
                addAll(redThreeEvent(nextRevision, intent.playerId, redThrees))
                add(CanastaEvent.StockDrawn(nextRevision, intent.playerId, drawn.size))
            },
        )
    }

    private fun takeDiscardPile(state: MatchState, intent: CanastaIntent.TakeDiscardPile): EngineResult {
        if (state.phase != TurnPhase.DRAW) {
            return state.rejected(RejectionCode.ILLEGAL_PHASE, "The discard pile may only be taken at the start of a turn")
        }
        val top = state.discardPile.lastOrNull()
            ?: return state.rejected(RejectionCode.DISCARD_PILE_BLOCKED, "The discard pile is empty")
        if (top.isWild || top.isBlackThree || top.isRedThree) {
            return state.rejected(RejectionCode.DISCARD_PILE_BLOCKED, "A wild card or three blocks pickup")
        }
        if (state.activePlayer.hand.size == 1 && state.discardPile.size == 1) {
            return state.rejected(RejectionCode.DISCARD_PICKUP_REQUIREMENT, "A one-card hand cannot take a one-card discard pile")
        }
        val topRank = top.rank!!
        val player = state.activePlayer
        val team = state.activeTeam
        val handById = player.hand.associateBy(Card::id)
        val matching = intent.matchingCardIds.mapNotNull(handById::get)
        if (matching.size != intent.matchingCardIds.size) {
            return state.rejected(RejectionCode.CARD_NOT_IN_HAND, "One or more pickup cards are not in the hand")
        }

        val frozenForTeam = state.discardPileFrozen || !team.hasOpened
        val pickupCards = matching + top
        val existing = team.melds.firstOrNull { it.rank == topRank }
        val pickupValidation = when {
            frozenForTeam -> validateFrozenPickup(topRank, matching)
            existing != null -> validateExtension(existing, pickupCards)
            else -> validateNewMeld(pickupCards)
        }
        if (pickupValidation is MeldValidation.Invalid) {
            return state.rejected(RejectionCode.DISCARD_PICKUP_REQUIREMENT, pickupValidation.reason)
        }

        val openingGroups = resolveGroups(player.hand, intent.openingMeldGroups, excludedIds = intent.matchingCardIds)
            ?: return state.rejected(RejectionCode.CARD_NOT_IN_HAND, "An opening meld references a card not in the hand")
        if (intent.openingMeldGroups.flatten().toSet().size != intent.openingMeldGroups.sumOf(Set<Int>::size)) {
            return state.rejected(RejectionCode.ILLEGAL_MELD, "A card cannot appear in more than one opening meld")
        }
        val allOpeningGroups = if (!team.hasOpened) listOf(pickupCards) + openingGroups else emptyList()
        if (!team.hasOpened) {
            val openingValidation = validateOpeningGroups(allOpeningGroups, team.totalScore)
            if (openingValidation is OpeningValidation.Invalid) {
                return state.rejected(RejectionCode.INITIAL_MELD_TOO_SMALL, openingValidation.reason)
            }
        } else if (openingGroups.isNotEmpty()) {
            return state.rejected(RejectionCode.ILLEGAL_MELD, "Additional opening groups are only used while opening a team")
        }

        val consumed = intent.matchingCardIds + intent.openingMeldGroups.flatten()
        val pileRemainder = state.discardPile.dropLast(1)
        val exposedRedThrees = pileRemainder.filter(Card::isRedThree)
        val cardsAddedToHand = pileRemainder.filterNot(Card::isRedThree)
        val players = state.players.updateActive(state) {
            copy(hand = hand.filterNot { it.id in consumed } + cardsAddedToHand)
        }
        var updatedTeam = team.copy(redThrees = team.redThrees + exposedRedThrees).addCardsToRank(topRank, pickupCards)
        openingGroups.forEach { group -> updatedTeam = updatedTeam.addCardsToRank(group.firstNaturalRank(), group) }
        val teams = state.teams.map { if (it.id == team.id) updatedTeam else it }
        val nextRevision = state.revision + 1
        val nextState = state.copy(
            players = players,
            teams = teams,
            discardPile = emptyList(),
            discardPileFrozen = false,
            phase = TurnPhase.MELD_OR_DISCARD,
            revision = nextRevision,
            turnDrawSource = DrawSource.DISCARD_PILE,
        )
        return EngineResult.Accepted(
            nextState,
            buildList {
                add(CanastaEvent.DiscardPileTaken(nextRevision, intent.playerId, state.discardPile.size, top.id))
                addAll(redThreeEvent(nextRevision, intent.playerId, exposedRedThrees))
                addAll(meldEventsForGroups(nextRevision, intent.playerId, team, listOf(pickupCards) + openingGroups))
            },
        )
    }

    private fun openMelds(state: MatchState, intent: CanastaIntent.OpenMelds): EngineResult {
        if (state.phase != TurnPhase.MELD_OR_DISCARD) {
            return state.rejected(RejectionCode.ILLEGAL_PHASE, "Draw before opening melds")
        }
        val team = state.activeTeam
        if (team.hasOpened) {
            return state.rejected(RejectionCode.ILLEGAL_MELD, "This team has already made its initial meld")
        }
        if (intent.groups.isEmpty()) {
            return state.rejected(RejectionCode.ILLEGAL_MELD, "At least one opening meld is required")
        }
        val groups = resolveGroups(state.activePlayer.hand, intent.groups)
            ?: return state.rejected(RejectionCode.CARD_NOT_IN_HAND, "An opening meld references a card not in the hand")
        if (intent.groups.flatten().toSet().size != intent.groups.sumOf(Set<Int>::size)) {
            return state.rejected(RejectionCode.ILLEGAL_MELD, "A card cannot appear in more than one opening meld")
        }
        val validation = validateOpeningGroups(groups, team.totalScore)
        if (validation is OpeningValidation.Invalid) {
            return state.rejected(RejectionCode.INITIAL_MELD_TOO_SMALL, validation.reason)
        }
        val consumed = intent.groups.flatten().toSet()
        if (consumed.size == state.activePlayer.hand.size) {
            return state.rejected(RejectionCode.MUST_GO_OUT_EXPLICITLY, "Use the explicit go-out action to play the final card")
        }
        val players = state.players.updateActive(state) { copy(hand = hand.filterNot { it.id in consumed }) }
        var updatedTeam = team
        groups.forEach { group -> updatedTeam = updatedTeam.addCardsToRank(group.firstNaturalRank(), group) }
        val teams = state.teams.map { if (it.id == team.id) updatedTeam else it }
        val nextRevision = state.revision + 1
        return EngineResult.Accepted(
            state.copy(players = players, teams = teams, revision = nextRevision),
            meldEventsForGroups(nextRevision, intent.playerId, team, groups),
        )
    }

    private fun meldCards(state: MatchState, intent: CanastaIntent.MeldCards): EngineResult {
        if (state.phase != TurnPhase.MELD_OR_DISCARD) {
            return state.rejected(RejectionCode.ILLEGAL_PHASE, "Draw before melding")
        }
        val hand = state.activePlayer.hand
        val selected = hand.filter { it.id in intent.cardIds }
        if (selected.size != intent.cardIds.size) {
            return state.rejected(RejectionCode.CARD_NOT_IN_HAND, "One or more selected cards are not in the hand")
        }
        if (selected.size == hand.size) {
            return state.rejected(RejectionCode.MUST_GO_OUT_EXPLICITLY, "Use the explicit go-out action to play the final card")
        }
        val team = state.activeTeam
        val rank = selected.firstNaturalRankOrNull()
            ?: return state.rejected(RejectionCode.ILLEGAL_MELD, "A meld requires natural cards")
        val existing = team.melds.firstOrNull { it.rank == rank }
        val validation = if (existing == null) validateNewMeld(selected) else validateExtension(existing, selected)
        if (validation is MeldValidation.Invalid) {
            return state.rejected(RejectionCode.ILLEGAL_MELD, validation.reason)
        }
        if (!team.hasOpened) {
            val opening = validateOpeningGroups(listOf(selected), team.totalScore)
            if (opening is OpeningValidation.Invalid) {
                return state.rejected(RejectionCode.INITIAL_MELD_TOO_SMALL, opening.reason)
            }
        }

        val players = state.players.updateActive(state) { copy(hand = hand.filterNot { it.id in intent.cardIds }) }
        val updatedTeam = team.addCardsToRank(rank, selected)
        val teams = state.teams.map { if (it.id == team.id) updatedTeam else it }
        val nextRevision = state.revision + 1
        val event = if (existing == null) {
            CanastaEvent.MeldCreated(nextRevision, intent.playerId, team.id, rank, selected.map(Card::id))
        } else {
            CanastaEvent.MeldExtended(nextRevision, intent.playerId, team.id, rank, selected.map(Card::id))
        }
        return EngineResult.Accepted(state.copy(players = players, teams = teams, revision = nextRevision), listOf(event))
    }

    private fun discard(state: MatchState, intent: CanastaIntent.Discard): EngineResult {
        if (state.phase != TurnPhase.MELD_OR_DISCARD) {
            return state.rejected(RejectionCode.ILLEGAL_PHASE, "Draw before discarding")
        }
        val card = state.activePlayer.hand.firstOrNull { it.id == intent.cardId }
            ?: return state.rejected(RejectionCode.CARD_NOT_IN_HAND, "The discarded card is not in the hand")
        if (state.activePlayer.hand.size == 1) {
            return state.rejected(RejectionCode.MUST_GO_OUT_EXPLICITLY, "Use the explicit go-out action for the final card")
        }

        val players = state.players.updateActive(state) { copy(hand = hand.filterNot { it.id == card.id }) }
        val nextRevision = state.revision + 1
        val nextState = state.copy(
            players = players,
            discardPile = state.discardPile + card,
            discardPileFrozen = state.discardPileFrozen || card.isWild,
            activeSeatIndex = (state.activeSeatIndex + 1) % state.players.size,
            phase = TurnPhase.DRAW,
            revision = nextRevision,
            turnDrawSource = DrawSource.NONE,
        )
        return EngineResult.Accepted(nextState, listOf(CanastaEvent.CardDiscarded(nextRevision, intent.playerId, card)))
    }

    private fun goOut(state: MatchState, intent: CanastaIntent.GoOut): EngineResult {
        if (state.phase != TurnPhase.MELD_OR_DISCARD) {
            return state.rejected(RejectionCode.ILLEGAL_PHASE, "Draw before going out")
        }
        if (intent.partnerPermission == PartnerPermission.DENIED) {
            return state.rejected(RejectionCode.PARTNER_DENIED_GOING_OUT, "The partner denied permission to go out")
        }
        val player = state.activePlayer
        val handById = player.hand.associateBy(Card::id)
        val groups = resolveGroups(player.hand, intent.meldGroups)
            ?: return state.rejected(RejectionCode.CARD_NOT_IN_HAND, "A go-out meld references a card not in the hand")
        if (intent.meldGroups.flatten().toSet().size != intent.meldGroups.sumOf(Set<Int>::size)) {
            return state.rejected(RejectionCode.ILLEGAL_MELD, "A card cannot appear in more than one meld")
        }
        val discard = intent.discardCardId?.let(handById::get)
            ?: if (intent.discardCardId != null) {
                return state.rejected(RejectionCode.CARD_NOT_IN_HAND, "The final discard is not in the hand")
            } else null
        val consumed = intent.meldGroups.flatten().toSet() + listOfNotNull(intent.discardCardId)
        if (consumed.size != player.hand.size) {
            return state.rejected(RejectionCode.ILLEGAL_MELD, "Going out must account for every card in the hand")
        }

        val team = state.activeTeam
        val normalGroups = groups.filterNot(::isBlackThreeMeld)
        val blackThreeGroups = groups.filter(::isBlackThreeMeld)
        if (blackThreeGroups.any { it.size < 3 } || groups.any { group -> group.any(Card::isBlackThree) && !isBlackThreeMeld(group) }) {
            return state.rejected(RejectionCode.ILLEGAL_MELD, "Black threes may only be melded as a group of at least three while going out")
        }
        normalGroups.forEach { group ->
            val rank = group.firstNaturalRankOrNull()
                ?: return state.rejected(RejectionCode.ILLEGAL_MELD, "A meld requires natural cards")
            val existing = team.melds.firstOrNull { it.rank == rank }
            val validation = if (existing == null) validateNewMeld(group) else validateExtension(existing, group)
            if (validation is MeldValidation.Invalid) return state.rejected(RejectionCode.ILLEGAL_MELD, validation.reason)
        }
        val normalRanks = normalGroups.map { it.firstNaturalRank() }
        if (normalRanks.toSet().size != normalRanks.size) {
            return state.rejected(RejectionCode.ILLEGAL_MELD, "Go-out meld groups must use distinct ranks")
        }
        val concealedFromStock = !team.hasOpened && state.turnDrawSource != DrawSource.DISCARD_PILE
        if (!team.hasOpened && !concealedFromStock) {
            val openingValidation = validateOpeningGroups(normalGroups, team.totalScore)
            if (openingValidation is OpeningValidation.Invalid) {
                return state.rejected(RejectionCode.INITIAL_MELD_TOO_SMALL, openingValidation.reason)
            }
        }

        var updatedTeam = team
        normalGroups.forEach { group -> updatedTeam = updatedTeam.addCardsToRank(group.firstNaturalRank(), group) }
        blackThreeGroups.forEach { group -> updatedTeam = updatedTeam.addCardsToRank(Rank.THREE, group) }
        if (updatedTeam.canastaCount < state.rules.requiredCanastasToGoOut) {
            return state.rejected(
                RejectionCode.GOING_OUT_REQUIRES_CANASTA,
                "This room requires ${state.rules.requiredCanastasToGoOut} canasta(s) before going out",
            )
        }
        val concealed = !team.hasOpened && normalGroups.none { group -> team.melds.any { it.rank == group.firstNaturalRank() } }
        val players = state.players.updateActive(state) { copy(hand = emptyList()) }
        val teams = state.teams.map { if (it.id == team.id) updatedTeam else it }
        val nextRevision = state.revision + 1
        val baseState = state.copy(
            players = players,
            teams = teams,
            discardPile = if (discard == null) state.discardPile else state.discardPile + discard,
            discardPileFrozen = state.discardPileFrozen || (discard?.isWild == true),
            revision = nextRevision,
        )
        return finishRound(
            state = baseState,
            reason = RoundEndReason.PLAYER_WENT_OUT,
            winnerPlayerId = intent.playerId,
            concealedOut = concealed,
            extraEvents = buildList {
                addAll(meldEventsForGroups(nextRevision, intent.playerId, team, normalGroups + blackThreeGroups))
                if (discard != null) add(CanastaEvent.CardDiscarded(nextRevision, intent.playerId, discard))
            },
        )
    }

    private fun endRoundForExhaustedStock(
        state: MatchState,
        intent: CanastaIntent.EndRoundForExhaustedStock,
    ): EngineResult {
        check(intent.playerId == state.activePlayer.seat.playerId)
        if (state.phase != TurnPhase.DRAW) {
            return state.rejected(RejectionCode.ILLEGAL_PHASE, "The stock is checked at the start of a turn")
        }
        if (state.stock.isNotEmpty()) {
            return state.rejected(RejectionCode.STOCK_STILL_AVAILABLE, "Cards remain in the stock")
        }
        if (mustTakeDiscardWhenStockEmpty(state)) {
            return state.rejected(
                RejectionCode.DISCARD_PICKUP_AVAILABLE,
                "The unfrozen top discard matches an existing team meld and must be taken",
            )
        }
        return finishRound(
            state = state.copy(revision = state.revision + 1),
            reason = RoundEndReason.STOCK_EXHAUSTED,
            winnerPlayerId = null,
            concealedOut = false,
        )
    }

    fun mustTakeDiscardWhenStockEmpty(state: MatchState): Boolean {
        if (state.phase != TurnPhase.DRAW || state.discardPile.isEmpty()) return false
        if (state.activePlayer.hand.size == 1 && state.discardPile.size == 1) return false
        val top = state.discardPile.last()
        if (top.isWild || top.isBlackThree || top.isRedThree) return false
        if (state.discardPileFrozen || !state.activeTeam.hasOpened) return false
        return state.activeTeam.melds.any { it.rank == top.rank }
    }

    fun canTakeDiscardPile(state: MatchState): Boolean {
        if (state.phase != TurnPhase.DRAW || state.discardPile.isEmpty()) return false
        val top = state.discardPile.last()
        if (top.isWild || top.isBlackThree || top.isRedThree) return false
        val rank = top.rank!!
        val team = state.activeTeam
        val hand = state.activePlayer.hand
        val frozenForTeam = state.discardPileFrozen || !team.hasOpened
        if (frozenForTeam) return hand.count { !it.isWild && it.rank == rank } >= 2
        if (team.melds.any { it.rank == rank }) return true
        val naturals = hand.count { !it.isWild && it.rank == rank }
        val wilds = hand.count(Card::isWild)
        return naturals >= 2 || (naturals >= 1 && wilds >= 1)
    }

    fun validateNewMeld(cards: List<Card>): MeldValidation {
        if (cards.size < 3) return MeldValidation.Invalid("A new meld requires at least three cards")
        if (cards.any { it.rank == Rank.THREE }) return MeldValidation.Invalid("Threes cannot form an ordinary meld")
        val naturalCards = cards.filterNot(Card::isWild)
        val wildCards = cards.filter(Card::isWild)
        if (naturalCards.size < 2) return MeldValidation.Invalid("A meld requires at least two natural cards")
        val naturalRank = naturalCards.first().rank!!
        if (naturalCards.any { it.rank != naturalRank }) {
            return MeldValidation.Invalid("All natural cards in a meld must share a rank")
        }
        if (wildCards.size > 3) return MeldValidation.Invalid("A meld cannot contain more than three wild cards")
        return MeldValidation.Valid(naturalRank)
    }

    fun validateExtension(existing: Meld, additions: List<Card>): MeldValidation {
        if (additions.isEmpty()) return MeldValidation.Invalid("At least one card must be added")
        if (additions.any { it.rank == Rank.THREE }) return MeldValidation.Invalid("Threes cannot extend an ordinary meld")
        val naturalAdditions = additions.filterNot(Card::isWild)
        if (naturalAdditions.any { it.rank != existing.rank }) {
            return MeldValidation.Invalid("Natural additions must match the meld rank")
        }
        val combined = existing.cards + additions
        val wildCount = combined.count(Card::isWild)
        if (wildCount > 3) return MeldValidation.Invalid("A meld cannot contain more than three wild cards")
        return MeldValidation.Valid(existing.rank)
    }

    private fun validateFrozenPickup(rank: Rank, matching: List<Card>): MeldValidation {
        if (matching.size < 2) return MeldValidation.Invalid("A frozen pile requires two matching natural cards from the hand")
        if (matching.any { it.isWild || it.rank != rank }) {
            return MeldValidation.Invalid("A frozen pile requires two natural cards matching the top discard")
        }
        return MeldValidation.Valid(rank)
    }

    private fun validateOpeningGroups(groups: List<List<Card>>, teamTotalScore: Int): OpeningValidation {
        if (groups.isEmpty()) return OpeningValidation.Invalid("At least one meld is required to open")
        val ranks = mutableSetOf<Rank>()
        groups.forEach { group ->
            val validation = validateNewMeld(group)
            if (validation is MeldValidation.Invalid) return OpeningValidation.Invalid(validation.reason)
            validation as MeldValidation.Valid
            if (!ranks.add(validation.rank)) {
                return OpeningValidation.Invalid("Opening meld groups must use distinct ranks")
            }
        }
        val points = groups.flatten().sumOf(CanastaScoring::cardPoints)
        val required = CanastaScoring.initialMeldRequirement(teamTotalScore)
        return if (points >= required) OpeningValidation.Valid(points) else {
            OpeningValidation.Invalid("Initial melds total $points points; $required are required")
        }
    }

    private fun finishRound(
        state: MatchState,
        reason: RoundEndReason,
        winnerPlayerId: PlayerId?,
        concealedOut: Boolean,
        extraEvents: List<CanastaEvent> = emptyList(),
    ): EngineResult.Accepted {
        val scores = CanastaScoring.scoreRound(state, winnerPlayerId, concealedOut)
        val scoreByTeam = scores.associateBy(RoundScore::teamId)
        val teams = state.teams.map { team ->
            val score = scoreByTeam.getValue(team.id)
            team.copy(roundScore = score.total, totalScore = team.totalScore + score.total)
        }
        val result = RoundResult(reason, winnerPlayerId, concealedOut, scores)
        val nextState = state.copy(teams = teams, phase = TurnPhase.ROUND_OVER, roundResult = result)
        return EngineResult.Accepted(
            nextState,
            extraEvents + CanastaEvent.RoundEnded(state.revision, reason, winnerPlayerId, scores),
        )
    }

    private fun validateSeats(seats: List<PlayerSeat>, rules: CanastaRules) {
        require(seats.size == rules.playerCount)
        require(seats.map(PlayerSeat::index).toSet() == (0 until seats.size).toSet())
        require(seats.map(PlayerSeat::playerId).toSet().size == seats.size)
        if (seats.size == 2) {
            require(seats.map(PlayerSeat::teamId).distinct().size == 2) { "Two-player rooms use individual teams" }
        }
        if (seats.size == 4) {
            val ordered = seats.sortedBy(PlayerSeat::index)
            require(ordered[0].teamId == ordered[2].teamId && ordered[1].teamId == ordered[3].teamId) {
                "Four-player partners must occupy opposite seats"
            }
            require(ordered[0].teamId != ordered[1].teamId) { "Four-player rooms require two partnerships" }
        }
    }

    private fun MatchState.rejected(code: RejectionCode, message: String): EngineResult.Rejected =
        EngineResult.Rejected(this, code, message)

    private fun List<PlayerState>.updateActive(
        state: MatchState,
        transform: PlayerState.() -> PlayerState,
    ): List<PlayerState> = map { player ->
        if (player.seat.index == state.activeSeatIndex) player.transform() else player
    }

    private fun resolveGroups(
        hand: List<Card>,
        groups: List<Set<Int>>,
        excludedIds: Set<Int> = emptySet(),
    ): List<List<Card>>? {
        val handById = hand.associateBy(Card::id)
        return groups.map { ids ->
            if (ids.any { it in excludedIds }) return null
            val cards = ids.mapNotNull(handById::get)
            if (cards.size != ids.size) return null
            cards
        }
    }

    private fun TeamState.addCardsToRank(rank: Rank, cards: List<Card>): TeamState {
        val existing = melds.firstOrNull { it.rank == rank }
        val updated = if (existing == null) {
            melds + Meld(rank, cards)
        } else {
            melds.map { if (it.rank == rank) it.copy(cards = it.cards + cards) else it }
        }
        return copy(melds = updated)
    }

    private fun meldEventsForGroups(
        revision: Long,
        playerId: PlayerId,
        originalTeam: TeamState,
        groups: List<List<Card>>,
    ): List<CanastaEvent> = groups.map { group ->
        val rank = group.firstNaturalRank()
        if (originalTeam.melds.any { it.rank == rank }) {
            CanastaEvent.MeldExtended(revision, playerId, originalTeam.id, rank, group.map(Card::id))
        } else {
            CanastaEvent.MeldCreated(revision, playerId, originalTeam.id, rank, group.map(Card::id))
        }
    }

    private fun redThreeEvent(revision: Long, playerId: PlayerId, cards: List<Card>): List<CanastaEvent> =
        if (cards.isEmpty()) emptyList() else listOf(CanastaEvent.RedThreesExposed(revision, playerId, cards.map(Card::id)))
}

sealed interface MeldValidation {
    data class Valid(val rank: Rank) : MeldValidation
    data class Invalid(val reason: String) : MeldValidation
}

sealed interface OpeningValidation {
    data class Valid(val points: Int) : OpeningValidation
    data class Invalid(val reason: String) : OpeningValidation
}

object CanastaScoring {
    fun cardPoints(card: Card): Int = when {
        card.joker -> 50
        card.rank == Rank.TWO -> 20
        card.rank == Rank.ACE -> 20
        card.rank in setOf(Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING) -> 10
        else -> 5
    }

    fun initialMeldRequirement(teamTotalScore: Int): Int = when {
        teamTotalScore < 0 -> 15
        teamTotalScore < 1_500 -> 50
        teamTotalScore < 3_000 -> 90
        else -> 120
    }

    fun redThreeScore(redThrees: List<Card>, teamOpened: Boolean): Int {
        val count = redThrees.size
        if (count == 0) return 0
        val value = if (count == 4) 800 else count * 100
        return if (teamOpened) value else -value
    }

    fun scoreRound(state: MatchState, winnerPlayerId: PlayerId?, concealedOut: Boolean): List<RoundScore> {
        val winningTeamId = winnerPlayerId?.let { id -> state.players.first { it.seat.playerId == id }.seat.teamId }
        return state.teams.map { team ->
            val meldCardPoints = team.melds.sumOf { meld -> meld.cards.sumOf(::cardPoints) }
            val handPenalty = state.players
                .filter { it.seat.teamId == team.id }
                .sumOf { player -> player.hand.sumOf(::cardPoints) }
            val canastaBonus = team.melds.sumOf { meld ->
                when {
                    !meld.isCanasta -> 0
                    meld.isNatural -> state.rules.naturalCanastaBonus
                    else -> state.rules.mixedCanastaBonus
                }
            }
            val redThreeScore = redThreeScore(team.redThrees, team.hasOpened)
            val goingOutScore = if (team.id == winningTeamId) {
                state.rules.goingOutBonus + if (concealedOut) state.rules.concealedOutBonus else 0
            } else 0
            val total = meldCardPoints - handPenalty + canastaBonus + redThreeScore + goingOutScore
            RoundScore(team.id, meldCardPoints, handPenalty, canastaBonus, redThreeScore, goingOutScore, total)
        }
    }
}

private val Card.isRedThree: Boolean
    get() = rank == Rank.THREE && suit in setOf(Suit.HEARTS, Suit.DIAMONDS)

private val Card.isBlackThree: Boolean
    get() = rank == Rank.THREE && suit in setOf(Suit.CLUBS, Suit.SPADES)

private fun List<Card>.firstNaturalRankOrNull(): Rank? = firstOrNull { !it.isWild }?.rank
private fun List<Card>.firstNaturalRank(): Rank = requireNotNull(firstNaturalRankOrNull())
private fun isBlackThreeMeld(cards: List<Card>): Boolean = cards.isNotEmpty() && cards.all { it.isBlackThree }
