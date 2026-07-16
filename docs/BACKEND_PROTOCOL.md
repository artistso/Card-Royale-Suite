# Multiplayer protocol outline

## Connection lifecycle

1. Authenticate with the application's backend.
2. Exchange the Google Play Games player identity for an application session.
3. Join a queue or private room.
4. Receive a short-lived match connection token.
5. Open a secure WebSocket.
6. Resume from the client's last acknowledged revision.

## Command envelope

```json
{
  "commandId": "0190...",
  "matchId": "match_...",
  "playerId": "player_...",
  "expectedRevision": 41,
  "sentAtEpochMs": 1784190000000,
  "payload": {
    "type": "Discard",
    "cardId": 73
  }
}
```

Commands are idempotent by `commandId`. The server rejects stale revisions and returns a current snapshot or missing event range.

## Event envelope

```json
{
  "matchId": "match_...",
  "revision": 42,
  "eventId": "evt_...",
  "payload": {
    "type": "CardDiscarded",
    "playerId": "player_1",
    "card": { "id": 73, "rank": "KING", "suit": "HEARTS" }
  }
}
```

## Required rejection codes

- `NOT_YOUR_TURN`
- `STALE_REVISION`
- `ILLEGAL_PHASE`
- `CARD_NOT_IN_HAND`
- `ILLEGAL_MELD`
- `DISCARD_PILE_FROZEN`
- `INITIAL_MELD_REQUIREMENT_NOT_MET`
- `MUST_HAVE_CANASTA_TO_GO_OUT`
- `MATCH_ALREADY_FINISHED`
- `RATE_LIMITED`

## Reconnection

The server retains the player's seat through a configurable grace period. Reconnection presents the latest safe player projection plus all unacknowledged events. Ranked penalties are policy decisions made by the backend, never by the Android client.
