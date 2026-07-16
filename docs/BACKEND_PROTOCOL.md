# Authoritative multiplayer protocol

The Android client is a presentation and input surface. It never owns the deck, validates its own move as final, advances the turn, or computes an authoritative score.

## Connection lifecycle

1. Authenticate with the application backend.
2. Exchange the Google Play Games identity assertion for an application session.
3. Join matchmaking or a private room.
4. Receive a short-lived match connection token.
5. Open a secure WebSocket and bind it to one player seat.
6. Send commands carrying a unique command ID and the last accepted match revision.
7. Acknowledge the highest event sequence applied locally.
8. Reconnect by requesting replay from that sequence; receive a secure snapshot when replay history is unavailable.

## Command envelope

```json
{
  "protocolVersion": 1,
  "commandId": "0190...",
  "matchId": "match_...",
  "playerId": "player_...",
  "connectionId": "connection_...",
  "expectedRevision": 41,
  "payload": {
    "type": "Discard",
    "cardId": 73
  }
}
```

The authenticated session—not the JSON field alone—determines the actor. The server verifies that `playerId`, `connectionId`, and the occupied seat agree.

## Idempotency

`commandId` is immutable within a match:

- The first occurrence executes and stores its response.
- An identical retry returns the original response without re-running the rules engine.
- Reusing the same ID with a different actor, revision, connection, or payload is a protocol conflict.

This prevents duplicated draws, discards, or melds after mobile-network retries.

## Event ordering

Every client event contains both a match revision and a strictly increasing stream sequence.

```json
{
  "matchId": "match_...",
  "revision": 42,
  "sequence": 318,
  "eventId": "match_...:42:command_...:1",
  "commandId": "command_...",
  "payload": {
    "type": "CardDiscarded",
    "playerId": "player_1",
    "card": {
      "id": 73,
      "rank": "KING",
      "suit": "HEARTS",
      "joker": false,
      "displayName": "K♥"
    }
  }
}
```

A command may create multiple events at one revision. `sequence` orders those events and is the reconnect cursor. Event IDs are deterministic from match, revision, command, and event position.

## Visibility boundary

The authoritative match contains every hand and the exact stock order. A client projection does not.

Every seated player may receive:

- Their own full hand.
- Opponent hand counts, never opponent card identities.
- The stock count, never stock order or next-card identity.
- Public discards, melds, red threes, scores, turn state, and room rules.

Private hand replacement events are addressed to exactly one player. Partner-permission prompts are addressed only to the named partner. The server filters audience metadata before constructing the client event envelope; raw internal events are never placed on the socket.

## Reconnection

The client sends:

```json
{
  "protocolVersion": 1,
  "matchId": "match_...",
  "playerId": "player_...",
  "connectionId": "new_connection_...",
  "lastSeenSequence": 317,
  "lastKnownRevision": 41
}
```

The server responds with one of two shapes:

- **Replay:** all retained events after the acknowledged sequence that are visible to that player.
- **Snapshot:** the latest player-specific secure projection when the cursor is missing, ahead of the server, or older than retained history.

A snapshot contains only a stock count and hides all non-viewer hands. Reconnection never broadens visibility.

## Timed partner permission

A four-player go-out permission request is a server-side record with:

- Immutable request ID.
- Requesting player and named partner.
- Match revision at request time.
- Server deadline.
- `PENDING`, `GRANTED`, `DENIED`, or `EXPIRED` status.

Only the named partner may answer. A response at or after the deadline resolves as `EXPIRED`. Once resolved, the status cannot be changed. A pending request cannot be routed into the rules engine as permission to go out.

## Boundary versus rules commands

Rules-engine commands include stock draw, discard-pile pickup, opening melds, meld extension, discard, explicit going out, and exhausted-stock termination.

Partner permission request/answer commands remain at the multiplayer boundary. They generate audience-filtered events and are converted into the rules engine's binding permission value only after resolution.

## Required rejection categories

- `NOT_YOUR_TURN`
- `STALE_REVISION`
- `ILLEGAL_PHASE`
- `CARD_NOT_IN_HAND`
- `ILLEGAL_MELD`
- `INITIAL_MELD_TOO_SMALL`
- `DISCARD_PILE_BLOCKED`
- `DISCARD_PICKUP_REQUIREMENT`
- `GOING_OUT_REQUIRES_CANASTA`
- `PARTNER_PERMISSION_PENDING`
- `PARTNER_PERMISSION_UNKNOWN`
- `PARTNER_DENIED_GOING_OUT`
- `ROUND_ALREADY_OVER`
- `COMMAND_ID_CONFLICT`
- `RATE_LIMITED`

Ranked penalties, disconnect forfeits, and grace periods are backend policy. They are never decided by the Android client.
