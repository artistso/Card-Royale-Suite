package com.soquarky.cardtable.cards

import java.util.Random

enum class Suit(val symbol: String) {
    CLUBS("♣"),
    DIAMONDS("♦"),
    HEARTS("♥"),
    SPADES("♠"),
}

enum class Rank(val label: String, val order: Int) {
    ACE("A", 1),
    TWO("2", 2),
    THREE("3", 3),
    FOUR("4", 4),
    FIVE("5", 5),
    SIX("6", 6),
    SEVEN("7", 7),
    EIGHT("8", 8),
    NINE("9", 9),
    TEN("10", 10),
    JACK("J", 11),
    QUEEN("Q", 12),
    KING("K", 13),
}

data class Card(
    val id: Int,
    val rank: Rank? = null,
    val suit: Suit? = null,
    val joker: Boolean = false,
) {
    init {
        require(joker || (rank != null && suit != null)) {
            "A non-joker card requires both rank and suit"
        }
        require(!joker || (rank == null && suit == null)) {
            "A joker must not have a rank or suit"
        }
    }

    val isWild: Boolean
        get() = joker || rank == Rank.TWO

    val displayName: String
        get() = if (joker) "Joker" else "${rank!!.label}${suit!!.symbol}"
}

object DeckFactory {
    fun standard(decks: Int = 1, jokersPerDeck: Int = 0): List<Card> {
        require(decks > 0) { "decks must be positive" }
        require(jokersPerDeck >= 0) { "jokersPerDeck cannot be negative" }

        var nextId = 0
        return buildList {
            repeat(decks) {
                Suit.entries.forEach { suit ->
                    Rank.entries.forEach { rank ->
                        add(Card(id = nextId++, rank = rank, suit = suit))
                    }
                }
                repeat(jokersPerDeck) {
                    add(Card(id = nextId++, joker = true))
                }
            }
        }
    }

    /** Two 52-card decks plus four jokers. */
    fun classicCanasta(): List<Card> = standard(decks = 2, jokersPerDeck = 2)
}

object DeterministicShuffle {
    fun <T> shuffle(values: List<T>, seed: Long): List<T> {
        val result = values.toMutableList()
        val random = Random(seed)
        for (index in result.lastIndex downTo 1) {
            val swapWith = random.nextInt(index + 1)
            val temporary = result[index]
            result[index] = result[swapWith]
            result[swapWith] = temporary
        }
        return result
    }
}
