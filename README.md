# Card Royale Suite

An Android-first card game suite with **human-versus-human Canasta** as the flagship game and an extensible library of offline Solitaire variants.

This repository is the first executable Kotlin foundation, not a finished game. It deliberately separates deterministic game rules from Android UI and future network transport.

## Included in this slice

- Native Android application shell using Kotlin and Jetpack Compose.
- Adaptive phone and tablet navigation.
- Modular Home, Canasta lobby/table, Solitaire library, profile, competition, and blueprint screens.
- Interactive deterministic Canasta table sandbox.
- Pure Kotlin card/deck module with seeded shuffling.
- Pure Kotlin Canasta state-machine foundation.
- Fourteen Solitaire variant definitions and deterministic Klondike deals.
- Multiplayer protocol models designed around server-authoritative commands and events.
- JVM unit tests, GitHub Actions, and a standalone engine smoke test.
- Google Play Games integration boundary with a safe local stub.

## What is intentionally not claimed as complete

The current Canasta engine proves state transitions and module boundaries. It does **not** yet implement every Classic Canasta rule. See [`docs/CANASTA_RULE_GAPS.md`](docs/CANASTA_RULE_GAPS.md).

Online matchmaking, production authentication, WebSockets, persistence, ratings, moderation, and Google Play Console credentials are not connected yet.

## Open in Android Studio

1. Clone or download this repository.
2. Open the repository root in a current Android Studio release.
3. Install Android SDK 36 if prompted.
4. Let Gradle sync.
5. Run the `app` configuration on an Android 8.0+ device or emulator.

The project targets Java 17 bytecode. Android Studio's bundled JDK is recommended.

## Command-line checks

```bash
./gradlew test
./gradlew :app:assembleDebug
```

The included `gradlew` and `gradlew.bat` are lightweight bootstrap scripts. They download Gradle 9.4.1 on first use because the binary wrapper JAR is not embedded.

## Module map

```text
app                 Compose UI and Android integration boundaries
core:cards          Card identities, decks, deterministic shuffle
core:canasta        Canasta rules and state-machine foundation
core:solitaire      Variant catalog and Klondike deal model
core:protocol       Serializable multiplayer commands and events
```

## Product constraints

- No AI opponents.
- No gambling, wagering, purchasable advantage, or casino economy.
- Canasta is server-authoritative in production.
- Offline Solitaire remains available without account sign-in.
- Other players' hidden cards are never transmitted to a client.

## Immediate next milestone

Complete and freeze the Classic Canasta rules specification, add exhaustive named rule tests, then connect private two-player rooms to an authoritative backend.
