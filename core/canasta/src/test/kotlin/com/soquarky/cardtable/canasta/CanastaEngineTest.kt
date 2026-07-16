package com.soquarky.cardtable.canasta

import com.soquarky.cardtable.cards.Card
import com.soquarky.cardtable.cards.Rank
import com.soquarky.cardtable.cards.Suit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CanastaEngineTest {
    private val seats = listOf(
        PlayerSeat(0, PlayerId("lovely"), "Lovely", TeamId("lovely")),
        PlayerSeat(1, PlayerId("guest"), "Guest", TeamId("guest")),
    )

    @Test
    fun `two-player game deals fifteen cards and opens discard`() {
        val state = CanastaEngine.newGame(seats, seed = 83L)

        assertEquals(listOf(15, 15), state.players.map { it.hand.size })
        assertEquals(77, state.stock.size)
        assertEquals(1, state.discardPile.size)
        assertEquals(TurnPhase.DRAW, state.phase)
    }

    @Test
    fun `only active player can act`() {
        val state = CanastaEngine.newGame(seats, seed = 83L)
        val result = CanastaEngine.apply(
            state,
            CanastaIntent.DrawStock(PlayerId("guest"), expectedRevision = 0),
        )

        val rejected = assertInstanceOf(EngineResult.Rejected::class.java, result)
        assertEquals(RejectionCode.NOT_YOUR_TURN, rejected.code)
    }

    @Test
    fun `drawing transitions to meld or discard phase`() {
        val state = CanastaEngine.newGame(seats, seed = 83L)
        val result = CanastaEngine.apply(
            state,
            CanastaIntent.DrawStock(PlayerId("lovely"), expectedRevision = 0),
        ) as EngineResult.Accepted

        assertEquals(17, result.state.activePlayer.hand.size)
        assertEquals(TurnPhase.MELD_OR_DISCARD, result.state.phase)
        assertEquals(1, result.state.revision)
    }

    @Test
    fun `discard advances turn and revision`() {
        val state = CanastaEngine.newGame(seats, seed = 83L)
        val drawn = CanastaEngine.apply(
            state,
            CanastaIntent.DrawStock(PlayerId("lovely"), 0),
        ) as EngineResult.Accepted
        val card = drawn.state.activePlayer.hand.first()
        val discarded = CanastaEngine.apply(
            drawn.state,
            CanastaIntent.Discard(PlayerId("lovely"), 1, card.id),
        ) as EngineResult.Accepted

        assertEquals(1, discarded.state.activeSeatIndex)
        assertEquals(TurnPhase.DRAW, discarded.state.phase)
        assertEquals(2, discarded.state.revision)
        assertEquals(card, discarded.state.discardPile.last())
    }

    @Test
    fun `basic meld accepts matching natural rank with bounded wilds`() {
        val cards = listOf(
            Card(1, Rank.SEVEN, Suit.CLUBS),
            Card(2, Rank.SEVEN, Suit.HEARTS),
            Card(3, Rank.TWO, Suit.SPADES),
        )

        val result = CanastaEngine.validateNewMeld(cards)
        assertEquals(MeldValidation.Valid(Rank.SEVEN), result)
    }

    @Test
    fun `basic meld rejects mixed natural ranks`() {
        val cards = listOf(
            Card(1, Rank.SEVEN, Suit.CLUBS),
            Card(2, Rank.EIGHT, Suit.HEARTS),
            Card(3, Rank.TWO, Suit.SPADES),
        )

        assertTrue(CanastaEngine.validateNewMeld(cards) is MeldValidation.Invalid)
    }

    @Test
    fun `initial meld thresholds follow classic score bands`() {
        assertEquals(15, CanastaScoring.initialMeldRequirement(-1))
        assertEquals(50, CanastaScoring.initialMeldRequirement(0))
        assertEquals(90, CanastaScoring.initialMeldRequirement(1_500))
        assertEquals(120, CanastaScoring.initialMeldRequirement(3_000))
    }
}
