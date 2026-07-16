# Classic Canasta rule completion ledger

The engine now implements the named **Pagat Classic Canasta** profile for two-player and four-player rooms. Every completed rule below is covered by a named JVM test.

## Implemented and tested

- [x] Deterministic 108-card deck, deal, and opening discard construction.
- [x] Opening wild cards and red threes remain buried and freeze the discard pile.
- [x] Red threes dealt or drawn are exposed and replaced; buried red threes are exposed when the pile is taken without replacement.
- [x] A final-stock red three ends the hand only when no ordinary card was obtained in that draw.
- [x] Black threes block pile pickup and may only be melded as a group of three or four while going out.
- [x] Frozen and team-frozen discard-pile pickup requires two natural cards matching the top discard.
- [x] Unfrozen pickup supports a natural pair, one natural plus one wild, or direct addition to an existing team meld.
- [x] Wild cards and threes cannot top a taken pile; a one-card hand cannot take a one-card pile.
- [x] Melds require at least two naturals, no more than three wild cards, and no duplicate team melds of the same rank.
- [x] Atomic initial meld thresholds of 15, 50, 90, and 120 points across multiple meld groups.
- [x] Only the top discard—not buried cards—counts toward an initial meld requirement.
- [x] Partnership ownership of melds and adding to a partner's existing meld.
- [x] Natural and mixed canasta classification, including conversion when a wild card is added.
- [x] Explicit go-out action, canasta prerequisites, binding partner denial, and final black-three melds.
- [x] Concealed-out bonus and the Classic exception that waives the initial minimum after a stock draw, but not after taking the discard pile.
- [x] Exhausted-stock terminal handling and mandatory pickup when an unfrozen top discard matches an existing team meld.
- [x] Full round scoring: melded cards, hand penalties, natural/mixed canastas, red threes, going out, and concealed going out.
- [x] Four-player partnership seats must be opposite.
- [x] Two-player profile: 15-card hands, draw two, one-card final draw, and two canastas required to go out.

## Remaining before ranked multiplayer

- [ ] Replace the current `PartnerPermission` command field with a timed server-side ask/answer protocol that enforces when permission may be requested.
- [ ] Add dealer rotation, successive hands, cumulative match scoring, and the 5,000-point game-end tiebreak.
- [ ] Add configurable rule-profile identifiers and migration tests before introducing regional or tournament variants.
- [ ] Add property-based and mutation tests for every command/state invariant.
- [ ] Add authoritative projection tests proving that hidden hands and stock order never enter another player's payload.

No remaining item above should be represented as complete in the UI or backend until its tests pass in CI.
