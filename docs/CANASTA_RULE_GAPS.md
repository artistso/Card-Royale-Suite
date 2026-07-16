# Classic Canasta rule completion ledger

The current engine supports deterministic dealing, active-player enforcement, stock drawing, basic same-rank meld validation, discarding, revision tracking, and initial score-threshold calculation.

The following rules must be formalized and tested before multiplayer is considered correct:

- Opening discard constraints and red-three replacement draws.
- Black-three restrictions.
- Frozen and unfrozen discard-pile pickup.
- Top-discard matching requirements and natural-card requirements.
- Wild-card ratios for existing and new melds.
- Initial meld thresholds enforced across a complete turn.
- Team ownership of melds and adding to partner melds.
- Natural and mixed canasta classification.
- Going-out permission, partner permission, and canasta prerequisites.
- Concealed-out bonus.
- Exhausted stock and terminal round conditions.
- Full hand, meld, canasta, red-three, and going-out scoring.
- Four-player partnership seat order.
- Two-player draw-two behavior and variant-specific hand sizes.
- Tournament/regional rule-profile configuration.

Each rule should be represented by a named test before the implementation is accepted.
