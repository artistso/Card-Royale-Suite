# Architecture

## Design principle

The client is a rendering and input surface. The authoritative multiplayer server owns shuffle, deal, legality, turn order, hidden information, scoring, timeout decisions, and final results.

## Production topology

```text
Android client
  ├─ Compose UI
  ├─ local Solitaire session
  ├─ cached profile/preferences
  └─ secure WebSocket
       ↓
API gateway / authentication
       ↓
matchmaking queues ── ratings/profile store
       ↓
one authoritative room coordinator per Canasta match
       ├─ append-only event log
       ├─ snapshots
       ├─ reconnect leases
       └─ moderation/audit metadata
```

## Determinism

Every accepted command advances a monotonically increasing revision and emits one or more immutable events. The event stream must reconstruct the public match state and support replay, recovery, dispute analysis, and regression testing.

## Hidden information

A server event has one canonical internal form and player-specific projections. A client receives only:

- its own hand;
- public melds and discard pile;
- counts for hidden hands and stock;
- public turn, timer, and score data.

Never send an encrypted or merely obfuscated full deck to the client.

## Android module boundaries

The pure Kotlin modules have no Android dependency. They can be used by JVM backend code, command-line simulations, property tests, and the Android app. Production may later split shared public rules from backend-only authority code.

## Persistence

Solitaire saves are local-first. Ranked Canasta summaries, ratings, sanctions, and server match logs are backend-owned. Google Play leaderboards mirror selected verified server statistics rather than becoming the source of truth.
