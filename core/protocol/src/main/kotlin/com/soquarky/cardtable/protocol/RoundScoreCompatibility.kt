package com.soquarky.cardtable.protocol

import com.soquarky.cardtable.canasta.RoundScore

/**
 * The protocol calls the per-hand result `roundScore`; the rules engine stores
 * the same value as `total` within an individual RoundScore breakdown.
 */
internal val RoundScore.roundScore: Int
    get() = total
