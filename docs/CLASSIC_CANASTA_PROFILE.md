# Canonical Classic Canasta profile

Profile identifier: `classic_pagat_2025`

This project needs one deterministic rules contract before it can offer table-rule customization. The canonical contract follows Pagat's **Classic Canasta** description, including its two-player modifications. Bicycle's Canasta rules are used as a corroborating reference for the common four-player rules and scoring.

## Fixed rules

- Two standard 52-card decks plus four jokers: 108 cards.
- Four players: fixed partnerships, partners opposite, 11 cards each, draw one, one canasta required to go out.
- Two players: 15 cards each, draw two, discard one, two canastas required to go out.
- Jokers and twos are wild. An ordinary meld has at least three cards, at least two natural cards, and no more than three wild cards.
- A partnership has only one meld of a given rank; later cards merge into that meld.
- A canasta has seven or more cards. Natural canastas score 500; mixed canastas score 300.
- Initial meld requirements are 15 for a negative cumulative score, 50 from 0–1495, 90 from 1500–2995, and 120 from 3000 upward.
- An opening wild card or red three is buried under further upcards and freezes the pile. Under this profile, a black three may remain as the visible upcard; it blocks pickup but does not itself freeze the buried pile.
- A pile frozen globally or against an unopened team requires two matching natural cards from hand. An unfrozen pile may also be taken with one matching natural plus one wild, or by adding the top discard to an existing team meld.
- Red threes are exposed immediately. A buried red three is exposed when the pile is taken and is not replaced.
- Black threes may only be melded as a group of three or four in the final go-out action.
- Going out requires the room's canasta count. Permission is optional, but a requested partner answer is binding.
- Concealed going out scores an additional 100 points. A player going out concealed after drawing from stock does not need to meet the initial meld minimum; a concealed out using the discard pile does.
- Round score equals bonuses plus melded-card values minus cards remaining in both team hands.

## Deliberate profile choice

Published Classic Canasta descriptions differ on a few setup details, especially the treatment of an opening black three. The engine does not silently combine those variants. `classic_pagat_2025` follows Pagat for this point. A future Bicycle-compatible or table-customized profile must receive a distinct identifier and its own conformance tests.

## References

- Pagat, “Canasta: rules and variations of the card game,” Classic Canasta and two-player sections, updated July 22, 2025: https://www.pagat.com/rummy/canasta.html
- Bicycle Cards, “Canasta,” consulted July 16, 2026: https://bicyclecards.com/how-to-play/canasta/
