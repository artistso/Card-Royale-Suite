package com.soquarky.cardtable.protocol

import com.soquarky.cardtable.canasta.CanastaEngine
import com.soquarky.cardtable.canasta.CanastaIntent
import com.soquarky.cardtable.canasta.CanastaRules
import com.soquarky.cardtable.canasta.EngineResult
import com.soquarky.cardtable.canasta.MatchState
import com.soquarky.cardtable.canasta.PartnerPermission
import com.soquarky.cardtable.canasta.PlayerId
import com.soquarky.cardtable.canasta.PlayerSeat
import com.soquarky.cardtable.canasta.PlayerState
import com.soquarky.cardtable.canasta.TeamId
import com.soquarky.cardtable.canasta.TeamState
import com.soquarky.cardtable.canasta.TurnPhase
import com.soquarky.cardtable.cards.Card
import com.soquarky.cardtable.cards.Rank
import com.soquarky.cardtable.cards.Suit
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProtocolTest {
    private val json = Json {
        classDiscriminator = "type"
        encodeDefaults = true
    }

    @Test
    fun `command envelope round trips with actor connection and explicit type`() {
        val command = commandEnvelope(
            commandId = "command-1",
            payload = PlayerCommand.Discard(cardId = 73),
            expectedRevision = 12,
        )

        val encoded = json.encodeToString(CommandEnvelope.serializer(), command)
        val decoded = json.decodeFromString(CommandEnvelope.serializer(), encoded)

        assertEquals(command, decoded)
        assertTrue(encoded.contains("Discard"))
        assertTrue(encoded.contains("connection-lovely"))
    }

    @Test
    fun `snapshot exposes viewer hand but never opponent hand or stock order`() {
        val ownCard = card(101, Rank.ACE, Suit.CLUBS)
        val opponentSecret = card(99_001, Rank.QUEEN, Suit.HEARTS)
        val stockSecret = card(88_001, Rank.KING, Suit.SPADES)
        val state = state(
            hand0 = listOf(ownCard),
            hand1 = listOf(opponentSecret),
            stock = listOf(stockSecret),
        )

        val snapshot = MatchStateProjector.project("match-1", "classic_pagat_2025", state, "lovely")
        val encoded = json.encodeToString(MatchSnapshot.serializer(), snapshot)

        assertEquals(listOf(ownCard.id), snapshot.players.first { it.playerId == "lovely" }.hand!!.map(CardView::id))
        assertNull(snapshot.players.first { it.playerId == "guest" }.hand)
        assertEquals(1, snapshot.players.first { it.playerId == "guest" }.handCount)
        assertFalse(encoded.contains(opponentSecret.id.toString()))
        assertFalse(encoded.contains(stockSecret.id.toString()))
        assertFalse(encoded.contains(opponentSecret.displayName))
        assertEquals(1, snapshot.stockCount)
    }

    @Test
    fun `each seated viewer receives only their own private hand`() {
        val lovelyCard = card(101, Rank.ACE, Suit.CLUBS)
        val guestCard = card(202, Rank.KING, Suit.HEARTS)
        val state = state(hand0 = listOf(lovelyCard), hand1 = listOf(guestCard))

        val lovely = MatchStateProjector.project("match-1", "classic_pagat_2025", state, "lovely")
        val guest = MatchStateProjector.project("match-1", "classic_pagat_2025", state, "guest")

        assertEquals(listOf(lovelyCard.id), lovely.players.first { it.playerId == "lovely" }.hand!!.map(CardView::id))
        assertNull(lovely.players.first { it.playerId == "guest" }.hand)
        assertEquals(listOf(guestCard.id), guest.players.first { it.playerId == "guest" }.hand!!.map(CardView::id))
        assertNull(guest.players.first { it.playerId == "lovely" }.hand)
    }

    @Test
    fun `draw event sends exact cards only to drawing player`() {
        val firstDraw = card(88_001, Rank.KING, Suit.SPADES)
        val secondDraw = card(88_002, Rank.QUEEN, Suit.DIAMONDS)
        val before = state(stock = listOf(firstDraw, secondDraw, card(88_003, Rank.FOUR, Suit.CLUBS)))
        val engine = accepted(
            CanastaEngine.apply(before, CanastaIntent.DrawStock(PlayerId("lovely"), before.revision)),
        )
        val emissions = EngineEventProjector.project(before, engine.state, engine.events)
        val ledger = AuthoritativeEventLedger("match-1")
        ledger.append("draw-command", engine.state.revision, emissions)

        val lovelyEvents = ledger.eventsAfter(0, "lovely", "lovely")
        val guestEvents = ledger.eventsAfter(0, "guest", "guest")
        val lovelyJson = json.encodeToString(lovelyEvents)
        val guestJson = json.encodeToString(guestEvents)

        assertTrue(lovelyEvents.any { it.payload is PrivateMatchEvent.HandReplaced })
        assertFalse(guestEvents.any { it.payload is PrivateMatchEvent.HandReplaced })
        assertTrue(lovelyJson.contains(firstDraw.id.toString()))
        assertTrue(lovelyJson.contains(secondDraw.id.toString()))
        assertFalse(guestJson.contains(firstDraw.id.toString()))
        assertFalse(guestJson.contains(secondDraw.id.toString()))
        assertTrue(guestEvents.any { it.payload is PublicMatchEvent.HandCountChanged })
    }

    @Test
    fun `event identifiers and sequences are deterministic and monotonic`() {
        val ledger = AuthoritativeEventLedger("match-83")
        val eventIds = ledger.append(
            commandId = "command-7",
            revision = 4,
            emissions = listOf(
                EventEmission(EventAudience.AllPlayers, PublicMatchEvent.StockCountChanged(42)),
                EventEmission(EventAudience.AllPlayers, PublicMatchEvent.HandCountChanged("lovely", 12)),
            ),
        )
        val events = ledger.eventsAfter(0, "lovely", "lovely")

        assertEquals(listOf("match-83:4:command-7:1", "match-83:4:command-7:2"), eventIds)
        assertEquals(listOf(1L, 2L), events.map(EventEnvelope::sequence))
    }

    @Test
    fun `reconnect replays retained visible events`() {
        val snapshot = MatchStateProjector.project("match-1", "classic_pagat_2025", state(), "lovely")
        val ledger = AuthoritativeEventLedger("match-1")
        ledger.append("c1", 1, listOf(EventEmission(EventAudience.AllPlayers, PublicMatchEvent.StockCountChanged(40))))
        ledger.append("c2", 2, listOf(EventEmission(EventAudience.AllPlayers, PublicMatchEvent.HandCountChanged("guest", 9))))

        val response = ReconnectCoordinator.resume(
            request = ReconnectRequest("match-1", "lovely", "new-connection", 1, 1),
            currentSnapshot = snapshot.copy(revision = 2),
            viewerTeamId = "lovely",
            ledger = ledger,
        )

        val replay = assertInstanceOf(ReconnectResponse.Replay::class.java, response)
        assertEquals(listOf(2L), replay.events.map(EventEnvelope::sequence))
        assertEquals(2L, replay.latestSequence)
    }

    @Test
    fun `reconnect falls back to secure snapshot when cursor is outside retention`() {
        val snapshot = MatchStateProjector.project("match-1", "classic_pagat_2025", state(), "lovely")
        val ledger = AuthoritativeEventLedger("match-1", retentionLimit = 2)
        ledger.append("c1", 1, listOf(EventEmission(EventAudience.AllPlayers, PublicMatchEvent.StockCountChanged(40))))
        ledger.append("c2", 2, listOf(EventEmission(EventAudience.AllPlayers, PublicMatchEvent.StockCountChanged(39))))
        ledger.append("c3", 3, listOf(EventEmission(EventAudience.AllPlayers, PublicMatchEvent.StockCountChanged(38))))

        val response = ReconnectCoordinator.resume(
            request = ReconnectRequest("match-1", "lovely", "new-connection", 0, 0),
            currentSnapshot = snapshot.copy(revision = 3),
            viewerTeamId = "lovely",
            ledger = ledger,
        )

        assertInstanceOf(ReconnectResponse.Snapshot::class.java, response)
    }

    @Test
    fun `identical command retry returns original response without running handler twice`() {
        val store = CommandIdempotencyStore()
        val envelope = commandEnvelope("same-command", PlayerCommand.DrawStock)
        var executions = 0
        val handler = {
            executions++
            CommandResponse.Accepted(envelope.commandId, acceptedRevision = 1, eventIds = listOf("event-1"))
        }

        val first = store.execute(envelope, handler)
        val retry = store.execute(envelope, handler)

        assertInstanceOf(IdempotentCommandResult.Fresh::class.java, first)
        assertInstanceOf(IdempotentCommandResult.Duplicate::class.java, retry)
        assertEquals(1, executions)
        assertEquals(first.response, retry.response)
    }

    @Test
    fun `reusing command id for different payload is a conflict`() {
        val store = CommandIdempotencyStore()
        val first = commandEnvelope("same-command", PlayerCommand.DrawStock)
        val changed = commandEnvelope("same-command", PlayerCommand.Discard(44))
        store.execute(first) { CommandResponse.Accepted("same-command", 1, emptyList()) }

        val result = store.execute(changed) { CommandResponse.Accepted("same-command", 2, emptyList()) }

        assertInstanceOf(IdempotentCommandResult.Conflict::class.java, result)
        assertNull(result.response)
    }

    @Test
    fun `permission prompt is visible only to named partner`() {
        val permissions = PartnerPermissionCoordinator()
        val record = permissions.request(
            requestId = "permission-1",
            matchId = "match-1",
            requestingPlayerId = "lovely",
            partnerPlayerId = "partner",
            requestedAtRevision = 12,
            expiresAtEpochMs = 20_000,
            nowEpochMs = 10_000,
        )
        val ledger = AuthoritativeEventLedger("match-1")
        ledger.append("permission-command", 12, listOf(permissions.promptEmission(record)))

        assertTrue(ledger.eventsAfter(0, "partner", "lovely").single().payload is PrivateMatchEvent.PartnerPermissionPrompt)
        assertTrue(ledger.eventsAfter(0, "opponent", "opponents").isEmpty())
    }

    @Test
    fun `permission answer is binding and expires at server deadline`() {
        val permissions = PartnerPermissionCoordinator()
        permissions.request("permission-1", "match-1", "lovely", "partner", 12, 20_000, 10_000)

        assertThrows(IllegalArgumentException::class.java) {
            permissions.answer("permission-1", "opponent", granted = true, nowEpochMs = 15_000)
        }
        val expired = permissions.answer("permission-1", "partner", granted = true, nowEpochMs = 20_000)

        assertEquals(PartnerPermissionStatus.EXPIRED, expired.status)
        assertEquals(PartnerPermissionStatus.EXPIRED, permissions.status("permission-1", 21_000))
    }

    @Test
    fun `go-out routing waits for pending permission and maps granted response`() {
        val permissions = PartnerPermissionCoordinator()
        permissions.request("permission-1", "match-1", "lovely", "partner", 12, 20_000, 10_000)
        val envelope = commandEnvelope(
            "go-out-command",
            PlayerCommand.GoOut(emptyList(), discardCardId = 51, partnerPermissionRequestId = "permission-1"),
            expectedRevision = 12,
        )

        val pending = CanastaCommandRouter.route(envelope, permissions, nowEpochMs = 15_000)
        assertEquals("PARTNER_PERMISSION_PENDING", assertInstanceOf(CommandRoutingResult.Rejected::class.java, pending).code)

        permissions.answer("permission-1", "partner", granted = true, nowEpochMs = 16_000)
        val routed = assertInstanceOf(
            CommandRoutingResult.Engine::class.java,
            CanastaCommandRouter.route(envelope, permissions, nowEpochMs = 16_001),
        )
        val intent = assertInstanceOf(CanastaIntent.GoOut::class.java, routed.intent)
        assertEquals(PartnerPermission.GRANTED, intent.partnerPermission)
    }

    @Test
    fun `permission commands remain at boundary instead of entering rules engine`() {
        val permissions = PartnerPermissionCoordinator()
        val envelope = commandEnvelope(
            "permission-command",
            PlayerCommand.RequestPartnerPermission("permission-1", "partner", 20_000),
        )

        val routed = CanastaCommandRouter.route(envelope, permissions, nowEpochMs = 10_000)

        assertInstanceOf(CommandRoutingResult.Boundary::class.java, routed)
    }

    @Test
    fun `envelopes reject blank identity and unsupported protocol versions`() {
        assertThrows(IllegalArgumentException::class.java) {
            CommandEnvelope("", "match-1", "lovely", "connection", 0, PlayerCommand.DrawStock)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CommandEnvelope("c", "match-1", "lovely", "connection", 0, PlayerCommand.DrawStock, protocolVersion = 99)
        }
    }

    private fun commandEnvelope(
        commandId: String,
        payload: PlayerCommand,
        expectedRevision: Long = 0,
    ): CommandEnvelope = CommandEnvelope(
        commandId = commandId,
        matchId = "match-1",
        playerId = "lovely",
        connectionId = "connection-lovely",
        expectedRevision = expectedRevision,
        payload = payload,
    )

    private fun state(
        hand0: List<Card> = listOf(card(10, Rank.FOUR, Suit.CLUBS)),
        hand1: List<Card> = listOf(card(20, Rank.FIVE, Suit.HEARTS)),
        stock: List<Card> = listOf(card(30, Rank.SIX, Suit.SPADES), card(31, Rank.SEVEN, Suit.CLUBS)),
    ): MatchState {
        val seats = listOf(
            PlayerSeat(0, PlayerId("lovely"), "Lovely", TeamId("lovely")),
            PlayerSeat(1, PlayerId("guest"), "Guest", TeamId("guest")),
        )
        return MatchState(
            rules = CanastaRules.classic(2),
            players = listOf(PlayerState(seats[0], hand0), PlayerState(seats[1], hand1)),
            teams = listOf(TeamState(TeamId("lovely")), TeamState(TeamId("guest"))),
            stock = stock,
            discardPile = listOf(card(40, Rank.EIGHT, Suit.DIAMONDS)),
            discardPileFrozen = false,
            activeSeatIndex = 0,
            phase = TurnPhase.DRAW,
            revision = 0,
        )
    }

    private fun card(id: Int, rank: Rank, suit: Suit): Card = Card(id, rank, suit)

    private fun accepted(result: EngineResult): EngineResult.Accepted =
        assertInstanceOf(EngineResult.Accepted::class.java, result)
}
