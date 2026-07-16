# Build status — 2026-07-16

## Verified in this generation environment

- Pure Kotlin card, Canasta, and Solitaire sources compile with `kotlinc`.
- Standalone engine smoke test executes successfully.
- Classic Canasta deck contains 108 cards with four jokers.
- Two-player deterministic deal produces 15-card hands, one opening discard, and a 77-card stock.
- Draw and discard commands advance phase, active seat, and revision.
- Solitaire catalog contains 14 unique variants.
- Klondike deal produces 28 tableau cards and 24 stock cards.
- XML and TOML configuration files parse successfully.
- Shell bootstrap script passes syntax validation.

## Not verified in this generation environment

- Full Android/Compose compilation and APK assembly, because the active container does not include the Android SDK and cannot download Gradle/Maven artifacts.
- Google Play Games authentication, because Play Console credentials and a signed application are required.
- Multiplayer networking, because the authoritative backend has not been implemented yet.

Run the following in Android Studio or a configured Android CI environment:

```bash
./gradlew test
./gradlew :app:assembleDebug
```
