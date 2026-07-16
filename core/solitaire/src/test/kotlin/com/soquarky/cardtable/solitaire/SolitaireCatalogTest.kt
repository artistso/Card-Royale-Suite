package com.soquarky.cardtable.solitaire

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SolitaireCatalogTest {
    @Test
    fun `catalog exposes fourteen unique launch variants`() {
        assertEquals(14, SolitaireCatalog.variants.size)
        assertEquals(14, SolitaireCatalog.variants.map(SolitaireVariant::id).toSet().size)
    }

    @Test
    fun `Klondike deal uses all 52 cards with correct face-up pattern`() {
        val deal = KlondikeDealer.deal(83L)
        val tableauCards = deal.tableau.flatten()

        assertEquals(28, tableauCards.size)
        assertEquals(24, deal.stock.size)
        assertEquals(7, tableauCards.count(TableauCard::faceUp))
        assertTrue(deal.tableau.all { column -> column.dropLast(1).none(TableauCard::faceUp) })
        assertEquals(52, (tableauCards.map { it.card } + deal.stock).map { it.id }.toSet().size)
    }
}
