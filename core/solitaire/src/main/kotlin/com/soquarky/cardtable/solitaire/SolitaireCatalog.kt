package com.soquarky.cardtable.solitaire

import com.soquarky.cardtable.cards.Card
import com.soquarky.cardtable.cards.DeckFactory
import com.soquarky.cardtable.cards.DeterministicShuffle

enum class Difficulty {
    RELAXED,
    MODERATE,
    HARD,
    EXPERT,
}

data class SolitaireVariant(
    val id: String,
    val title: String,
    val difficulty: Difficulty,
    val deckCount: Int,
    val estimatedMinutes: IntRange,
    val summary: String,
    val dailyChallengeEligible: Boolean,
)

object SolitaireCatalog {
    val variants: List<SolitaireVariant> = listOf(
        SolitaireVariant("klondike", "Klondike", Difficulty.MODERATE, 1, 5..15, "Build down in alternating colors and move all cards to foundations.", true),
        SolitaireVariant("spider", "Spider", Difficulty.HARD, 2, 10..25, "Build descending suited runs and clear eight complete sequences.", true),
        SolitaireVariant("freecell", "FreeCell", Difficulty.MODERATE, 1, 5..20, "Use four free cells to expose and organize every card.", true),
        SolitaireVariant("pyramid", "Pyramid", Difficulty.MODERATE, 1, 5..12, "Remove exposed pairs totaling thirteen.", true),
        SolitaireVariant("tripeaks", "TriPeaks", Difficulty.RELAXED, 1, 3..8, "Clear three peaks by selecting cards one rank above or below the waste.", true),
        SolitaireVariant("golf", "Golf", Difficulty.MODERATE, 1, 3..8, "Clear seven columns by playing adjacent ranks.", true),
        SolitaireVariant("yukon", "Yukon", Difficulty.HARD, 1, 10..25, "Move exposed groups freely while building tableau columns down by alternating color.", true),
        SolitaireVariant("scorpion", "Scorpion", Difficulty.HARD, 1, 10..25, "Build four same-suit descending runs from king to ace.", true),
        SolitaireVariant("forty-thieves", "Forty Thieves", Difficulty.EXPERT, 2, 15..35, "Build down by suit across ten tableau columns.", true),
        SolitaireVariant("canfield", "Canfield", Difficulty.HARD, 1, 10..25, "Build wrapping foundations from a random base rank.", true),
        SolitaireVariant("bakers-game", "Baker's Game", Difficulty.HARD, 1, 8..20, "A FreeCell relative that builds tableau sequences by suit.", true),
        SolitaireVariant("accordion", "Accordion", Difficulty.EXPERT, 1, 10..30, "Compress the deck by matching rank or suit at fixed distances.", false),
        SolitaireVariant("clock", "Clock", Difficulty.RELAXED, 1, 5..10, "Reveal cards into twelve clock positions before all kings appear.", false),
        SolitaireVariant("calculation", "Calculation", Difficulty.EXPERT, 1, 15..35, "Build four foundations in arithmetic sequences without suit restrictions.", true),
    )

    fun find(id: String): SolitaireVariant? = variants.firstOrNull { it.id == id }
}

data class TableauCard(
    val card: Card,
    val faceUp: Boolean,
)

data class KlondikeDeal(
    val tableau: List<List<TableauCard>>,
    val stock: List<Card>,
    val waste: List<Card> = emptyList(),
    val foundations: List<List<Card>> = List(4) { emptyList() },
)

object KlondikeDealer {
    fun deal(seed: Long): KlondikeDeal {
        val deck = DeterministicShuffle.shuffle(DeckFactory.standard(), seed)
        var cursor = 0
        val tableau = (1..7).map { columnSize ->
            List(columnSize) { index ->
                TableauCard(card = deck[cursor++], faceUp = index == columnSize - 1)
            }
        }
        return KlondikeDeal(tableau = tableau, stock = deck.drop(cursor))
    }
}
