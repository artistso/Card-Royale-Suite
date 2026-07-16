package com.soquarky.cardtable.cards

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CardsTest {
    @Test
    fun `standard deck contains 52 uniquely identified cards`() {
        val deck = DeckFactory.standard()
        assertEquals(52, deck.size)
        assertEquals(52, deck.map(Card::id).toSet().size)
    }

    @Test
    fun `classic Canasta deck contains 108 cards and four jokers`() {
        val deck = DeckFactory.classicCanasta()
        assertEquals(108, deck.size)
        assertEquals(4, deck.count(Card::joker))
        assertEquals(8, deck.count { it.rank == Rank.TWO })
    }

    @Test
    fun `shuffle is reproducible and seed sensitive`() {
        val deck = DeckFactory.classicCanasta()
        val first = DeterministicShuffle.shuffle(deck, 83L)
        val second = DeterministicShuffle.shuffle(deck, 83L)
        val other = DeterministicShuffle.shuffle(deck, 18L)

        assertEquals(first, second)
        assertNotEquals(first, other)
        assertTrue(first.toSet() == deck.toSet())
    }
}
