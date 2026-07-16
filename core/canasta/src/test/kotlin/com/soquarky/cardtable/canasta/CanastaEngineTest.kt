package com.soquarky.cardtable.canasta

import com.soquarky.cardtable.cards.Card
import com.soquarky.cardtable.cards.Rank
import com.soquarky.cardtable.cards.Suit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CanastaEngineTest {
    private val duelSeats = listOf(
        PlayerSeat(0, PlayerId("lovely"), "Lovely", TeamId("lovely")),
        PlayerSeat(1, PlayerId("guest"), "Guest", TeamId("guest")),
    )

    @Test
    fun `two-player classic deal keeps fifteen cards after exposing red threes`() {
        val state = CanastaEngine.newGame(duelSeats, seed = 0L)

        assertEquals(listOf(15, 15), state.players.map { it.hand.size })
        assertEquals(1, state.teams.sumOf { it.redThrees.size })
        assertEquals(TurnPhase.DRAW, state.phase)
    }

    @Test
    fun `opening wild card stays buried and freezes the discard pile`() {
        val state = CanastaEngine.newGame(duelSeats, seed = 26L)

        assertTrue(state.discardPile.size > 1)
        assertTrue(state.discardPile.dropLast(1).any(Card::isWild))
        assertTrue(state.discardPileFrozen)
        assertFalse(state.discardPile.last().isWild)
    }

    @Test
    fun `red three turned during opening freezes the discard pile`() {
        val state = CanastaEngine.newGame(duelSeats, seed = 64L)

        assertTrue(state.discardPile.dropLast(1).any { it.rank == Rank.THREE && it.suit in setOf(Suit.HEARTS, Suit.DIAMONDS) })
        assertTrue(state.discardPileFrozen)
    }

    @Test
    fun `four-player partners must occupy opposite seats`() {
        val invalid = listOf(
            PlayerSeat(0, PlayerId("a"), "A", TeamId("x")),
            PlayerSeat(1, PlayerId("b"), "B", TeamId("x")),
            PlayerSeat(2, PlayerId("c"), "C", TeamId("y")),
            PlayerSeat(3, PlayerId("d"), "D", TeamId("y")),
        )

        assertThrows(IllegalArgumentException::class.java) {
            CanastaEngine.newGame(invalid, seed = 1L)
        }
    }

    @Test
    fun `drawn red three is exposed and replaced`() {
        val redThree = card(1, Rank.THREE, Suit.HEARTS)
        val seven = card(2, Rank.SEVEN, Suit.CLUBS)
        val king = card(3, Rank.KING, Suit.SPADES)
        val state = state(stock = listOf(redThree, seven, king))

        val result = accepted(CanastaEngine.apply(state, CanastaIntent.DrawStock(PlayerId("lovely"), 0)))

        assertEquals(listOf(seven, king), result.state.activePlayer.hand.takeLast(2))
        assertEquals(listOf(redThree), result.state.activeTeam.redThrees)
        assertTrue(result.events.any { it is CanastaEvent.RedThreesExposed })
    }

    @Test
    fun `red three as final stock card ends the round immediately`() {
        val state = state(stock = listOf(card(1, Rank.THREE, Suit.DIAMONDS)))

        val result = accepted(CanastaEngine.apply(state, CanastaIntent.DrawStock(PlayerId("lovely"), 0)))

        assertEquals(TurnPhase.ROUND_OVER, result.state.phase)
        assertEquals(RoundEndReason.LAST_STOCK_CARD_WAS_RED_THREE, result.state.roundResult?.reason)
    }

    @Test
    fun `wild discard freezes pile until it is taken`() {
        val wild = card(1, Rank.TWO, Suit.CLUBS)
        val state = state(
            phase = TurnPhase.MELD_OR_DISCARD,
            hand0 = listOf(wild, card(2, Rank.FOUR, Suit.CLUBS)),
        )

        val result = accepted(CanastaEngine.apply(state, CanastaIntent.Discard(PlayerId("lovely"), 0, wild.id)))

        assertTrue(result.state.discardPileFrozen)
    }

    @Test
    fun `black three blocks discard pile pickup`() {
        val state = state(discard = listOf(card(50, Rank.THREE, Suit.CLUBS)))

        val result = rejected(
            CanastaEngine.apply(
                state,
                CanastaIntent.TakeDiscardPile(PlayerId("lovely"), 0, emptySet()),
            ),
        )

        assertEquals(RejectionCode.DISCARD_PILE_BLOCKED, result.code)
    }

    @Test
    fun `unopened team must meet threshold while taking frozen pile`() {
        val kings = listOf(
            card(1, Rank.KING, Suit.CLUBS),
            card(2, Rank.KING, Suit.HEARTS),
        )
        val aces = listOf(
            card(3, Rank.ACE, Suit.CLUBS),
            card(4, Rank.ACE, Suit.DIAMONDS),
            card(5, Rank.ACE, Suit.HEARTS),
        )
        val top = card(50, Rank.KING, Suit.SPADES)
        val state = state(hand0 = kings + aces + card(6, Rank.FOUR, Suit.CLUBS), discard = listOf(top))

        val tooSmall = rejected(
            CanastaEngine.apply(
                state,
                CanastaIntent.TakeDiscardPile(PlayerId("lovely"), 0, kings.map(Card::id).toSet()),
            ),
        )
        assertEquals(RejectionCode.INITIAL_MELD_TOO_SMALL, tooSmall.code)

        val accepted = accepted(
            CanastaEngine.apply(
                state,
                CanastaIntent.TakeDiscardPile(
                    PlayerId("lovely"),
                    0,
                    kings.map(Card::id).toSet(),
                    openingMeldGroups = listOf(aces.map(Card::id).toSet()),
                ),
            ),
        )
        assertTrue(accepted.state.activeTeam.hasOpened)
        assertTrue(accepted.state.discardPile.isEmpty())
    }

    @Test
    fun `frozen pile requires two natural matches rather than a wild card`() {
        val natural = card(1, Rank.NINE, Suit.CLUBS)
        val wild = card(2, Rank.TWO, Suit.SPADES)
        val top = card(50, Rank.NINE, Suit.HEARTS)
        val opened = TeamState(
            id = TeamId("lovely"),
            melds = listOf(Meld(Rank.FOUR, listOf(card(20, Rank.FOUR, Suit.CLUBS), card(21, Rank.FOUR, Suit.HEARTS), card(22, Rank.FOUR, Suit.SPADES)))),
        )
        val state = state(hand0 = listOf(natural, wild, card(3, Rank.FIVE, Suit.CLUBS)), discard = listOf(top), frozen = true, team0 = opened)

        val result = rejected(
            CanastaEngine.apply(
                state,
                CanastaIntent.TakeDiscardPile(PlayerId("lovely"), 0, setOf(natural.id, wild.id)),
            ),
        )

        assertEquals(RejectionCode.DISCARD_PICKUP_REQUIREMENT, result.code)
    }

    @Test
    fun `opened team can take unfrozen top card directly into existing meld`() {
        val existing = Meld(
            Rank.SEVEN,
            listOf(card(20, Rank.SEVEN, Suit.CLUBS), card(21, Rank.SEVEN, Suit.HEARTS), card(22, Rank.SEVEN, Suit.SPADES)),
        )
        val buried = card(40, Rank.FOUR, Suit.CLUBS)
        val top = card(41, Rank.SEVEN, Suit.DIAMONDS)
        val state = state(
            hand0 = listOf(card(1, Rank.FIVE, Suit.CLUBS), card(2, Rank.SIX, Suit.CLUBS)),
            discard = listOf(buried, top),
            team0 = TeamState(TeamId("lovely"), melds = listOf(existing)),
        )

        val result = accepted(
            CanastaEngine.apply(
                state,
                CanastaIntent.TakeDiscardPile(PlayerId("lovely"), 0, emptySet()),
            ),
        )

        assertEquals(4, result.state.activeTeam.melds.single { it.rank == Rank.SEVEN }.cards.size)
        assertTrue(buried in result.state.activePlayer.hand)
        assertFalse(top in result.state.activePlayer.hand)
    }

    @Test
    fun `red threes buried in a taken discard pile are exposed without replacement`() {
        val existing = Meld(
            Rank.SEVEN,
            listOf(card(20, Rank.SEVEN, Suit.CLUBS), card(21, Rank.SEVEN, Suit.HEARTS), card(22, Rank.SEVEN, Suit.SPADES)),
        )
        val redThree = card(40, Rank.THREE, Suit.HEARTS)
        val top = card(41, Rank.SEVEN, Suit.DIAMONDS)
        val state = state(
            hand0 = listOf(card(1, Rank.SEVEN, Suit.CLUBS), card(2, Rank.SEVEN, Suit.HEARTS), card(3, Rank.FIVE, Suit.CLUBS)),
            discard = listOf(redThree, top),
            frozen = true,
            team0 = TeamState(TeamId("lovely"), melds = listOf(existing)),
        )

        val result = accepted(
            CanastaEngine.apply(
                state,
                CanastaIntent.TakeDiscardPile(
                    PlayerId("lovely"),
                    0,
                    matchingCardIds = setOf(1, 2),
                ),
            ),
        )

        assertTrue(redThree in result.state.activeTeam.redThrees)
        assertFalse(redThree in result.state.activePlayer.hand)
    }

    @Test
    fun `multiple opening melds are evaluated atomically against threshold`() {
        val kings = listOf(card(1, Rank.KING, Suit.CLUBS), card(2, Rank.KING, Suit.DIAMONDS), card(3, Rank.KING, Suit.HEARTS))
        val eights = listOf(card(4, Rank.EIGHT, Suit.CLUBS), card(5, Rank.EIGHT, Suit.DIAMONDS), card(6, Rank.EIGHT, Suit.HEARTS))
        val state = state(
            phase = TurnPhase.MELD_OR_DISCARD,
            hand0 = kings + eights + card(7, Rank.FOUR, Suit.CLUBS),
        )

        val single = rejected(
            CanastaEngine.apply(state, CanastaIntent.OpenMelds(PlayerId("lovely"), 0, listOf(kings.map(Card::id).toSet()))),
        )
        assertEquals(RejectionCode.INITIAL_MELD_TOO_SMALL, single.code)

        val combined = accepted(
            CanastaEngine.apply(
                state,
                CanastaIntent.OpenMelds(
                    PlayerId("lovely"),
                    0,
                    listOf(kings.map(Card::id).toSet(), eights.map(Card::id).toSet()),
                ),
            ),
        )
        assertEquals(setOf(Rank.KING, Rank.EIGHT), combined.state.activeTeam.melds.map(Meld::rank).toSet())
    }

    @Test
    fun `partner adds cards to the shared team meld`() {
        val seats = partnershipSeats()
        val existing = Meld(
            Rank.QUEEN,
            listOf(card(20, Rank.QUEEN, Suit.CLUBS), card(21, Rank.QUEEN, Suit.DIAMONDS), card(22, Rank.QUEEN, Suit.HEARTS)),
        )
        val added = card(1, Rank.QUEEN, Suit.SPADES)
        val state = MatchState(
            rules = CanastaRules.classic(4),
            players = seats.map { seat -> PlayerState(seat, if (seat.index == 2) listOf(added, card(2, Rank.FOUR, Suit.CLUBS)) else listOf(card(100 + seat.index, Rank.FIVE, Suit.CLUBS))) },
            teams = listOf(TeamState(TeamId("a"), melds = listOf(existing)), TeamState(TeamId("b"))),
            stock = listOf(card(90, Rank.SIX, Suit.CLUBS)),
            discardPile = listOf(card(91, Rank.SEVEN, Suit.CLUBS)),
            discardPileFrozen = false,
            activeSeatIndex = 2,
            phase = TurnPhase.MELD_OR_DISCARD,
            revision = 0,
        )

        val result = accepted(
            CanastaEngine.apply(state, CanastaIntent.MeldCards(PlayerId("c"), 0, setOf(added.id))),
        )

        assertEquals(4, result.state.teams.first { it.id == TeamId("a") }.melds.single().cards.size)
    }

    @Test
    fun `classic meld may contain three wild cards with two naturals`() {
        val cards = listOf(
            card(1, Rank.QUEEN, Suit.CLUBS),
            card(2, Rank.QUEEN, Suit.HEARTS),
            card(3, Rank.TWO, Suit.CLUBS),
            card(4, Rank.TWO, Suit.HEARTS),
            Card(5, joker = true),
        )

        assertEquals(MeldValidation.Valid(Rank.QUEEN), CanastaEngine.validateNewMeld(cards))
    }

    @Test
    fun `natural and mixed canastas are classified`() {
        val natural = Meld(Rank.KING, (1..7).map { card(it, Rank.KING, Suit.CLUBS) })
        val mixed = Meld(
            Rank.QUEEN,
            (10..14).map { card(it, Rank.QUEEN, Suit.HEARTS) } +
                listOf(card(15, Rank.TWO, Suit.CLUBS), Card(16, joker = true)),
        )

        assertTrue(natural.isCanasta)
        assertTrue(natural.isNatural)
        assertTrue(mixed.isMixed)
    }

    @Test
    fun `going out is rejected until team has a canasta`() {
        val opened = TeamState(
            TeamId("lovely"),
            melds = listOf(Meld(Rank.FOUR, listOf(card(20, Rank.FOUR, Suit.CLUBS), card(21, Rank.FOUR, Suit.HEARTS), card(22, Rank.FOUR, Suit.SPADES)))),
        )
        val final = listOf(card(1, Rank.NINE, Suit.CLUBS), card(2, Rank.NINE, Suit.HEARTS), card(3, Rank.NINE, Suit.SPADES))
        val state = state(phase = TurnPhase.MELD_OR_DISCARD, hand0 = final, team0 = opened)

        val result = rejected(
            CanastaEngine.apply(
                state,
                CanastaIntent.GoOut(PlayerId("lovely"), 0, listOf(final.map(Card::id).toSet())),
            ),
        )

        assertEquals(RejectionCode.GOING_OUT_REQUIRES_CANASTA, result.code)
    }

    @Test
    fun `extending six-card meld permits going out and awards bonus`() {
        val existingCards = (20..25).map { card(it, Rank.SEVEN, Suit.CLUBS) }
        val existing = Meld(Rank.SEVEN, existingCards)
        val secondCanasta = Meld(Rank.KING, (30..36).map { card(it, Rank.KING, Suit.CLUBS) })
        val seventh = card(1, Rank.SEVEN, Suit.HEARTS)
        val discard = card(2, Rank.FOUR, Suit.SPADES)
        val state = state(
            phase = TurnPhase.MELD_OR_DISCARD,
            hand0 = listOf(seventh, discard),
            team0 = TeamState(TeamId("lovely"), melds = listOf(existing, secondCanasta)),
        )

        val result = accepted(
            CanastaEngine.apply(
                state,
                CanastaIntent.GoOut(
                    PlayerId("lovely"),
                    0,
                    meldGroups = listOf(setOf(seventh.id)),
                    discardCardId = discard.id,
                ),
            ),
        )

        assertEquals(TurnPhase.ROUND_OVER, result.state.phase)
        assertEquals(2, result.state.activeTeam.canastaCount)
        assertEquals(100, result.state.roundResult!!.scores.first { it.teamId == TeamId("lovely") }.goingOutScore)
    }

    @Test
    fun `partner denial is binding`() {
        val canasta = Meld(Rank.SEVEN, (20..26).map { card(it, Rank.SEVEN, Suit.CLUBS) })
        val secondCanasta = Meld(Rank.KING, (30..36).map { card(it, Rank.KING, Suit.CLUBS) })
        val final = card(1, Rank.FOUR, Suit.CLUBS)
        val state = state(phase = TurnPhase.MELD_OR_DISCARD, hand0 = listOf(final), team0 = TeamState(TeamId("lovely"), melds = listOf(canasta, secondCanasta)))

        val result = rejected(
            CanastaEngine.apply(
                state,
                CanastaIntent.GoOut(
                    PlayerId("lovely"),
                    0,
                    meldGroups = emptyList(),
                    discardCardId = final.id,
                    partnerPermission = PartnerPermission.DENIED,
                ),
            ),
        )

        assertEquals(RejectionCode.PARTNER_DENIED_GOING_OUT, result.code)
    }

    @Test
    fun `black threes can be melded only in final go-out action`() {
        val canasta = Meld(Rank.SEVEN, (20..26).map { card(it, Rank.SEVEN, Suit.CLUBS) })
        val secondCanasta = Meld(Rank.KING, (30..36).map { card(it, Rank.KING, Suit.CLUBS) })
        val blackThrees = listOf(
            card(1, Rank.THREE, Suit.CLUBS),
            card(2, Rank.THREE, Suit.SPADES),
            card(3, Rank.THREE, Suit.CLUBS),
        )
        assertTrue(CanastaEngine.validateNewMeld(blackThrees) is MeldValidation.Invalid)
        val state = state(phase = TurnPhase.MELD_OR_DISCARD, hand0 = blackThrees, team0 = TeamState(TeamId("lovely"), melds = listOf(canasta, secondCanasta)))

        val result = accepted(
            CanastaEngine.apply(
                state,
                CanastaIntent.GoOut(PlayerId("lovely"), 0, listOf(blackThrees.map(Card::id).toSet())),
            ),
        )

        assertEquals(3, result.state.activeTeam.melds.single { it.rank == Rank.THREE }.cards.size)
    }

    @Test
    fun `concealed out receives two hundred point going-out score`() {
        val fours = (1..7).map { card(it, Rank.FOUR, Suit.CLUBS) }
        val fives = (10..16).map { card(it, Rank.FIVE, Suit.HEARTS) }
        val state = state(
            phase = TurnPhase.MELD_OR_DISCARD,
            hand0 = fours + fives,
            team0 = TeamState(TeamId("lovely"), totalScore = 3_000),
            drawSource = DrawSource.STOCK,
        )

        val result = accepted(
            CanastaEngine.apply(
                state,
                CanastaIntent.GoOut(
                    PlayerId("lovely"),
                    0,
                    listOf(fours.map(Card::id).toSet(), fives.map(Card::id).toSet()),
                ),
            ),
        )

        assertTrue(result.state.roundResult!!.concealedOut)
        assertEquals(200, result.state.roundResult!!.scores.first { it.teamId == TeamId("lovely") }.goingOutScore)
    }

    @Test
    fun `concealed out after taking discard pile still meets initial minimum`() {
        val fours = (1..7).map { card(it, Rank.FOUR, Suit.CLUBS) }
        val fives = (10..16).map { card(it, Rank.FIVE, Suit.HEARTS) }
        val state = state(
            phase = TurnPhase.MELD_OR_DISCARD,
            hand0 = fours + fives,
            team0 = TeamState(TeamId("lovely"), totalScore = 3_000),
            drawSource = DrawSource.DISCARD_PILE,
        )

        val result = rejected(
            CanastaEngine.apply(
                state,
                CanastaIntent.GoOut(
                    PlayerId("lovely"),
                    0,
                    listOf(fours.map(Card::id).toSet(), fives.map(Card::id).toSet()),
                ),
            ),
        )

        assertEquals(RejectionCode.INITIAL_MELD_TOO_SMALL, result.code)
    }

    @Test
    fun `round scoring includes melds canastas red threes hand penalties and going out`() {
        val natural = Meld(Rank.KING, (1..7).map { card(it, Rank.KING, Suit.CLUBS) })
        val redThrees = listOf(
            card(30, Rank.THREE, Suit.HEARTS),
            card(31, Rank.THREE, Suit.DIAMONDS),
            card(32, Rank.THREE, Suit.HEARTS),
            card(33, Rank.THREE, Suit.DIAMONDS),
        )
        val mixed = Meld(
            Rank.QUEEN,
            (40..44).map { card(it, Rank.QUEEN, Suit.CLUBS) } +
                listOf(card(45, Rank.TWO, Suit.CLUBS), card(46, Rank.TWO, Suit.HEARTS)),
        )
        val state = state(
            hand0 = emptyList(),
            hand1 = listOf(card(70, Rank.ACE, Suit.CLUBS)),
            team0 = TeamState(TeamId("lovely"), melds = listOf(natural), redThrees = redThrees),
            team1 = TeamState(TeamId("guest"), melds = listOf(mixed)),
        )

        val scores = CanastaScoring.scoreRound(state, PlayerId("lovely"), concealedOut = false)
        val lovely = scores.first { it.teamId == TeamId("lovely") }
        val guest = scores.first { it.teamId == TeamId("guest") }

        assertEquals(1_470, lovely.total)
        assertEquals(370, guest.total)
    }

    @Test
    fun `red threes are negative when team never opens`() {
        val red = listOf(card(1, Rank.THREE, Suit.HEARTS), card(2, Rank.THREE, Suit.DIAMONDS))
        assertEquals(-200, CanastaScoring.redThreeScore(red, teamOpened = false))
    }

    @Test
    fun `stock exhaustion ends round only when discard cannot be taken`() {
        val blocked = state(stock = emptyList(), discard = listOf(card(50, Rank.THREE, Suit.SPADES)))
        val ended = accepted(
            CanastaEngine.apply(blocked, CanastaIntent.EndRoundForExhaustedStock(PlayerId("lovely"), 0)),
        )
        assertEquals(RoundEndReason.STOCK_EXHAUSTED, ended.state.roundResult?.reason)

        val existing = Meld(Rank.SEVEN, listOf(card(20, Rank.SEVEN, Suit.CLUBS), card(21, Rank.SEVEN, Suit.HEARTS), card(22, Rank.SEVEN, Suit.SPADES)))
        val available = state(
            stock = emptyList(),
            discard = listOf(card(50, Rank.SEVEN, Suit.DIAMONDS)),
            team0 = TeamState(TeamId("lovely"), melds = listOf(existing)),
        )
        val rejected = rejected(
            CanastaEngine.apply(available, CanastaIntent.EndRoundForExhaustedStock(PlayerId("lovely"), 0)),
        )
        assertEquals(RejectionCode.DISCARD_PICKUP_AVAILABLE, rejected.code)
    }

    @Test
    fun `classic player counts select draw hand and going-out requirements`() {
        assertEquals(15, CanastaRules.classic(2).handSize)
        assertEquals(2, CanastaRules.classic(2).drawCount)
        assertEquals(2, CanastaRules.classic(2).requiredCanastasToGoOut)
        assertEquals(11, CanastaRules.classic(4).handSize)
        assertEquals(1, CanastaRules.classic(4).drawCount)
        assertEquals(1, CanastaRules.classic(4).requiredCanastasToGoOut)
    }

    @Test
    fun `initial meld thresholds follow classic score bands`() {
        assertEquals(15, CanastaScoring.initialMeldRequirement(-1))
        assertEquals(50, CanastaScoring.initialMeldRequirement(0))
        assertEquals(90, CanastaScoring.initialMeldRequirement(1_500))
        assertEquals(120, CanastaScoring.initialMeldRequirement(3_000))
    }

    private fun state(
        hand0: List<Card> = listOf(card(10, Rank.FOUR, Suit.CLUBS), card(11, Rank.FIVE, Suit.CLUBS)),
        hand1: List<Card> = listOf(card(12, Rank.SIX, Suit.CLUBS)),
        stock: List<Card> = listOf(card(80, Rank.SEVEN, Suit.CLUBS), card(81, Rank.EIGHT, Suit.CLUBS)),
        discard: List<Card> = listOf(card(90, Rank.NINE, Suit.CLUBS)),
        frozen: Boolean = false,
        phase: TurnPhase = TurnPhase.DRAW,
        team0: TeamState = TeamState(TeamId("lovely")),
        team1: TeamState = TeamState(TeamId("guest")),
        drawSource: DrawSource = DrawSource.NONE,
    ): MatchState = MatchState(
        rules = CanastaRules.classic(2),
        players = listOf(PlayerState(duelSeats[0], hand0), PlayerState(duelSeats[1], hand1)),
        teams = listOf(team0, team1),
        stock = stock,
        discardPile = discard,
        discardPileFrozen = frozen,
        activeSeatIndex = 0,
        phase = phase,
        revision = 0,
        turnDrawSource = drawSource,
    )

    private fun partnershipSeats(): List<PlayerSeat> = listOf(
        PlayerSeat(0, PlayerId("a"), "A", TeamId("a")),
        PlayerSeat(1, PlayerId("b"), "B", TeamId("b")),
        PlayerSeat(2, PlayerId("c"), "C", TeamId("a")),
        PlayerSeat(3, PlayerId("d"), "D", TeamId("b")),
    )

    private fun card(id: Int, rank: Rank, suit: Suit): Card = Card(id, rank, suit)

    private fun accepted(result: EngineResult): EngineResult.Accepted =
        assertInstanceOf(EngineResult.Accepted::class.java, result)

    private fun rejected(result: EngineResult): EngineResult.Rejected =
        assertInstanceOf(EngineResult.Rejected::class.java, result)
}
