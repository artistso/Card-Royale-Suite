import com.soquarky.cardtable.canasta.CanastaEngine
import com.soquarky.cardtable.canasta.CanastaIntent
import com.soquarky.cardtable.canasta.EngineResult
import com.soquarky.cardtable.canasta.PlayerId
import com.soquarky.cardtable.canasta.PlayerSeat
import com.soquarky.cardtable.canasta.TeamId
import com.soquarky.cardtable.cards.DeckFactory
import com.soquarky.cardtable.cards.DeterministicShuffle
import com.soquarky.cardtable.solitaire.KlondikeDealer
import com.soquarky.cardtable.solitaire.SolitaireCatalog

fun main() {
    val canastaDeck = DeckFactory.classicCanasta()
    check(canastaDeck.size == 108)
    check(DeterministicShuffle.shuffle(canastaDeck, 83L) == DeterministicShuffle.shuffle(canastaDeck, 83L))

    val seats = listOf(
        PlayerSeat(0, PlayerId("lovely"), "Lovely", TeamId("lovely")),
        PlayerSeat(1, PlayerId("guest"), "Guest", TeamId("guest")),
    )
    val match = CanastaEngine.newGame(seats, seed = 83L)
    check(match.players.all { it.hand.size == 15 })

    val drawn = CanastaEngine.apply(match, CanastaIntent.DrawStock(PlayerId("lovely"), 0))
    check(drawn is EngineResult.Accepted)
    check(drawn.state.activePlayer.hand.size == 17)

    val discarded = CanastaEngine.apply(
        drawn.state,
        CanastaIntent.Discard(PlayerId("lovely"), 1, drawn.state.activePlayer.hand.first().id),
    )
    check(discarded is EngineResult.Accepted)
    check(discarded.state.activeSeatIndex == 1)

    val klondike = KlondikeDealer.deal(83L)
    check(klondike.tableau.flatten().size == 28)
    check(klondike.stock.size == 24)
    check(SolitaireCatalog.variants.size == 14)

    println("Engine smoke test passed: 108-card Canasta deck, turn transition, 14 Solitaire variants, deterministic Klondike deal.")
}
