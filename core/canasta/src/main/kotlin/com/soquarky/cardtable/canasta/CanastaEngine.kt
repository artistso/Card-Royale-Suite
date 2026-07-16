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
) {
    init {
        require(playerCount == 2 || playerCount == 4) { "Classic rooms support 2 or 4 players" }
        require(handSize > 0)
        require(drawCount > 0)
    }

    companion object {
        fun classic(playerCount: Int): CanastaRules = when (playerCount) {
            2 -> CanastaRules(playerCount = 2, handSize = 15, drawCount = 2)
            4 -> CanastaRules(playerCount = 4, handSize = 11, drawCount = 1)
            else -> error("Classic rooms support 2 or 4 players")
        }
    }
}

enum class TurnPhase {
    DRAW,
    MELD_OR_DISCARD,
    ROUND_OVER,
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
}

data class TeamState(
    val id: TeamId,
    val melds: List<Meld> = emptyList(),
    val roundScore: Int = 0,
    val totalScore: Int = 0,
)

data class MatchState(
    val rules: CanastaRules,
    val players: List<PlayerState>,
    val teams: List<TeamState>,
    val stock: List<Card>,
    val discardPile: List<Card>,
    val activeSeatIndex: Int,
    val phase: TurnPhase,
    val revision: Long,
) {
    val activePlayer: PlayerState get() = players.first { it.seat.index == activeSeatIndex }
}

sealed interface CanastaIntent {
    val playerId: PlayerId
    val expectedRevision: Long

    data class DrawStock(
        override val playerId: PlayerId,
        override val expectedRevision: Long,
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
}

sealed interface CanastaEvent {
    val revision: Long

    data class StockDrawn(
        override val revision: Long,
        val playerId: PlayerId,
        val count: Int,
    ) : CanastaEvent

    data class MeldCreated(
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
        val playerId: PlayerId,
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
    ROUND_ALREADY_OVER,
}

object CanastaEngine {
    fun newGame(
        seats: List<PlayerSeat>,
        seed: Long,
        rules: CanastaRules = CanastaRules.classic(seats.size),
    ): MatchState {
        require(seats.size == rules.playerCount)
        require(seats.map(PlayerSeat::index).toSet() == (0 until seats.size).toSet())
        require(seats.map(PlayerSeat::playerId).toSet().size == seats.size)

        val deck = DeterministicShuffle.shuffle(DeckFactory.classicCanasta(), seed)
        val hands = seats.associateWith { mutableListOf<Card>() }
        var cursor = 0

        repeat(rules.handSize) {
            seats.sortedBy(PlayerSeat::index).forEach { seat ->
                hands.getValue(seat).add(deck[cursor++])
            }
        }

        val openingDiscard = deck[cursor++]
        val players = seats.sortedBy(PlayerSeat::index).map { seat ->
            PlayerState(seat = seat, hand = hands.getValue(seat).toList())
        }
        val teams = seats.map(PlayerSeat::teamId).distinct().map(::TeamState)

        return MatchState(
            rules = rules,
            players = players,
            teams = teams,
            stock = deck.drop(cursor),
            discardPile = listOf(openingDiscard),
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
            is CanastaIntent.MeldCards -> meldCards(state, intent)
            is CanastaIntent.Discard -> discard(state, intent)
        }
    }

    private fun drawStock(state: MatchState, intent: CanastaIntent.DrawStock): EngineResult {
        if (state.phase != TurnPhase.DRAW) {
            return state.rejected(RejectionCode.ILLEGAL_PHASE, "A stock draw is only legal at the start of a turn")
        }
        if (state.stock.size < state.rules.drawCount) {
            return state.rejected(RejectionCode.STOCK_EXHAUSTED, "Not enough cards remain in the stock")
        }

        val drawn = state.stock.take(state.rules.drawCount)
        val players = state.players.updateActive(state) { copy(hand = hand + drawn) }
        val nextRevision = state.revision + 1
        val nextState = state.copy(
            players = players,
            stock = state.stock.drop(state.rules.drawCount),
            phase = TurnPhase.MELD_OR_DISCARD,
            revision = nextRevision,
        )
        return EngineResult.Accepted(
            state = nextState,
            events = listOf(CanastaEvent.StockDrawn(nextRevision, intent.playerId, drawn.size)),
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
        val validation = validateNewMeld(selected)
        if (validation is MeldValidation.Invalid) {
            return state.rejected(RejectionCode.ILLEGAL_MELD, validation.reason)
        }
        validation as MeldValidation.Valid

        val player = state.activePlayer
        val meld = Meld(rank = validation.rank, cards = selected)
        val players = state.players.updateActive(state) {
            copy(hand = hand.filterNot { it.id in intent.cardIds })
        }
        val teams = state.teams.map { team ->
            if (team.id == player.seat.teamId) team.copy(melds = team.melds + meld) else team
        }
        val nextRevision = state.revision + 1
        val nextState = state.copy(players = players, teams = teams, revision = nextRevision)
        return EngineResult.Accepted(
            state = nextState,
            events = listOf(
                CanastaEvent.MeldCreated(
                    revision = nextRevision,
                    playerId = intent.playerId,
                    teamId = player.seat.teamId,
                    rank = validation.rank,
                    cardIds = selected.map(Card::id),
                ),
            ),
        )
    }

    private fun discard(state: MatchState, intent: CanastaIntent.Discard): EngineResult {
        if (state.phase != TurnPhase.MELD_OR_DISCARD) {
            return state.rejected(RejectionCode.ILLEGAL_PHASE, "Draw before discarding")
        }

        val card = state.activePlayer.hand.firstOrNull { it.id == intent.cardId }
            ?: return state.rejected(RejectionCode.CARD_NOT_IN_HAND, "The discarded card is not in the hand")

        val players = state.players.updateActive(state) {
            copy(hand = hand.filterNot { it.id == card.id })
        }
        val nextRevision = state.revision + 1
        val roundEnded = players.first { it.seat.index == state.activeSeatIndex }.hand.isEmpty()
        val nextSeat = if (roundEnded) state.activeSeatIndex else (state.activeSeatIndex + 1) % state.players.size
        val nextState = state.copy(
            players = players,
            discardPile = state.discardPile + card,
            activeSeatIndex = nextSeat,
            phase = if (roundEnded) TurnPhase.ROUND_OVER else TurnPhase.DRAW,
            revision = nextRevision,
        )
        val events = buildList {
            add(CanastaEvent.CardDiscarded(nextRevision, intent.playerId, card))
            if (roundEnded) add(CanastaEvent.RoundEnded(nextRevision, intent.playerId))
        }
        return EngineResult.Accepted(nextState, events)
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
        if (wildCards.size > naturalCards.size) {
            return MeldValidation.Invalid("Wild cards cannot outnumber natural cards")
        }
        return MeldValidation.Valid(naturalRank)
    }

    private fun MatchState.rejected(code: RejectionCode, message: String): EngineResult.Rejected =
        EngineResult.Rejected(this, code, message)

    private fun List<PlayerState>.updateActive(
        state: MatchState,
        transform: PlayerState.() -> PlayerState,
    ): List<PlayerState> = map { player ->
        if (player.seat.index == state.activeSeatIndex) player.transform() else player
    }
}

sealed interface MeldValidation {
    data class Valid(val rank: Rank) : MeldValidation
    data class Invalid(val reason: String) : MeldValidation
}

object CanastaScoring {
    fun cardPoints(card: Card): Int = when {
        card.joker -> 50
        card.rank == Rank.TWO -> 20
        card.rank == Rank.ACE -> 20
        card.rank in setOf(Rank.EIGHT, Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING) -> 10
        card.rank == Rank.THREE && card.suit in setOf(Suit.HEARTS, Suit.DIAMONDS) -> 100
        else -> 5
    }

    fun initialMeldRequirement(teamTotalScore: Int): Int = when {
        teamTotalScore < 0 -> 15
        teamTotalScore < 1_500 -> 50
        teamTotalScore < 3_000 -> 90
        else -> 120
    }
}
