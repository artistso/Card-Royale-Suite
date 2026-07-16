package com.soquarky.cardtable.protocol

import com.soquarky.cardtable.canasta.CanastaEngine
import com.soquarky.cardtable.canasta.MatchState
import com.soquarky.cardtable.canasta.PlayerId
import com.soquarky.cardtable.cards.Card
import kotlinx.serialization.Serializable

@Serializable
data class PlayerView(
    val playerId: String,
    val displayName: String,
    val seatIndex: Int,
    val teamId: String,
    val handCount: Int,
    val hand: List<CardView>? = null,
)

@Serializable
data class MeldView(
    val rank: String,
    val cards: List<CardView>,
    val isCanasta: Boolean,
    val isNatural: Boolean,
    val isMixed: Boolean,
)

@Serializable
data class TeamView(
    val teamId: String,
    val melds: List<MeldView>,
    val redThrees: List<CardView>,
    val roundScore: Int,
    val totalScore: Int,
    val canastaCount: Int,
)

@Serializable
data class RoundScoreProjection(
    val teamId: String,
    val meldCardPoints: Int,
    val handPenalty: Int,
    val canastaBonus: Int,
    val redThreeScore: Int,
    val goingOutScore: Int,
    val total: Int,
)

@Serializable
data class RoundResultProjection(
    val reason: String,
    val winnerPlayerId: String?,
    val concealedOut: Boolean,
    val scores: List<RoundScoreProjection>,
)

@Serializable
data class MatchSnapshot(
    val matchId: String,
    val rulesProfileId: String,
    val revision: Long,
    val phase: String,
    val viewerPlayerId: String,
    val activePlayerId: String,
    val activeSeatIndex: Int,
    val stockCount: Int,
    val discardPile: List<CardView>,
    val discardPileFrozen: Boolean,
    val players: List<PlayerView>,
    val teams: List<TeamView>,
    val viewerCanTakeDiscardPile: Boolean,
    val roundResult: RoundResultProjection?,
)

object MatchStateProjector {
    fun project(
        matchId: String,
        rulesProfileId: String,
        state: MatchState,
        viewerPlayerId: String,
    ): MatchSnapshot {
        require(matchId.isNotBlank())
        require(rulesProfileId.isNotBlank())
        val viewer = state.players.firstOrNull { it.seat.playerId.value == viewerPlayerId }
            ?: error("Viewer $viewerPlayerId is not seated in this match")

        return MatchSnapshot(
            matchId = matchId,
            rulesProfileId = rulesProfileId,
            revision = state.revision,
            phase = state.phase.name,
            viewerPlayerId = viewerPlayerId,
            activePlayerId = state.activePlayer.seat.playerId.value,
            activeSeatIndex = state.activeSeatIndex,
            stockCount = state.stock.size,
            discardPile = state.discardPile.map(Card::toView),
            discardPileFrozen = state.discardPileFrozen,
            players = state.players.sortedBy { it.seat.index }.map { player ->
                val ownsHand = player.seat.playerId == viewer.seat.playerId
                PlayerView(
                    playerId = player.seat.playerId.value,
                    displayName = player.seat.displayName,
                    seatIndex = player.seat.index,
                    teamId = player.seat.teamId.value,
                    handCount = player.hand.size,
                    hand = if (ownsHand) player.hand.map(Card::toView) else null,
                )
            },
            teams = state.teams.sortedBy { it.id.value }.map { team ->
                TeamView(
                    teamId = team.id.value,
                    melds = team.melds.sortedBy { it.rank.order }.map { meld ->
                        MeldView(
                            rank = meld.rank.name,
                            cards = meld.cards.map(Card::toView),
                            isCanasta = meld.isCanasta,
                            isNatural = meld.isNatural,
                            isMixed = meld.isMixed,
                        )
                    },
                    redThrees = team.redThrees.map(Card::toView),
                    roundScore = team.roundScore,
                    totalScore = team.totalScore,
                    canastaCount = team.canastaCount,
                )
            },
            viewerCanTakeDiscardPile =
                state.activePlayer.seat.playerId == PlayerId(viewerPlayerId) && CanastaEngine.canTakeDiscardPile(state),
            roundResult = state.roundResult?.let { result ->
                RoundResultProjection(
                    reason = result.reason.name,
                    winnerPlayerId = result.winnerPlayerId?.value,
                    concealedOut = result.concealedOut,
                    scores = result.scores.map { score ->
                        RoundScoreProjection(
                            teamId = score.teamId.value,
                            meldCardPoints = score.meldCardPoints,
                            handPenalty = score.handPenalty,
                            canastaBonus = score.canastaBonus,
                            redThreeScore = score.redThreeScore,
                            goingOutScore = score.goingOutScore,
                            total = score.total,
                        )
                    },
                )
            },
        )
    }
}

internal fun Card.toView(): CardView = CardView(
    id = id,
    rank = rank?.name,
    suit = suit?.name,
    joker = joker,
    displayName = displayName,
)
