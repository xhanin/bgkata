package org.hanin.bgkata

import org.hanin.bgkata.ReadyToPlay.*
import kotlin.math.max

class Game private constructor(
    val players: List<Player>,
    val deck: Deck,
    val discard: Discard,
    val rounds: GameRounds,
) {
    val dealCardsPerPlayer: Int = 5

    fun start(): Game = startNewRound()
    fun startNewRound(): Game = newRound().deal().selectPlayer(players[0], ATTACK)

    private fun newRound() = withRounds(rounds.newRound())

    fun playAttack(player: Player, cards: List<Card>): Game {
        val playerInGame = playerInGame(player)
            .checkCanPlayCards(cards)
            .checkTurn(ATTACK)
        return withPlayer(playerInGame.prepareAttack(cards))
            .withRounds(rounds.played(player, ATTACK))
            .selectPlayer(nextPlayer(player), DEFEND)
    }

    fun playDefend(player: Player, cards: List<Card>): Game {
        val playerInGame = playerInGame(player)
            .checkCanPlayCards(cards)
            .checkTurn(DEFEND)
        return withPlayer(playerInGame.prepareDefend(cards))
            .withRounds(rounds.played(player, DEFEND))
            .let { game ->
                if (game.isPreparedForBattle()) {
                    game.withPlayers(game.players.map { it.readyToPlay(WAIT_FOR_BATTLE) })
                } else {
                    game.selectPlayer(player, ATTACK)
                }
            }
    }

    private fun isPreparedForBattle(): Boolean = players.all { player ->
        rounds.currentRound().actions
            .filter { it.playerName == player.name }
            .map { it.action }
            .toSet()
            .containsAll(setOf(ATTACK, DEFEND))
    }

    private fun nextPlayer(player: Player): Player =
        players.indexOfFirst { it.name == player.name }
            .let { (it + 1) % players.size }
            .let { players[it] }

    private fun withPlayer(player: Player): Game = withPlayers(
        players.map {
            if (it.name == player.name) {
                player
            } else {
                it
            }
        },
    )

    private fun selectPlayer(selectedPlayer: Player, readyToPlay: ReadyToPlay): Game = withPlayers(
        players.map { player ->
            player.readyToPlay(
                if (player.name == selectedPlayer.name) {
                    readyToPlay
                } else {
                    NOT_READY_TO_PLAY
                },
            )
        },
    )

    private fun deal(): Game {
        var game = this
        val players = players.map {
            it.takeCards(
                game.drawCards(max(dealCardsPerPlayer - it.cards.hand.size, 0)).also {
                    game = it.first
                }.second,
            )
        }
        return game.withPlayers(players)
    }

    private fun drawCards(count: Int): Pair<Game, List<Card>> {
        val (deck, cards) = deck.drawCards(count)
        return Game(players, deck, discard, rounds) to cards
    }

    private fun withPlayers(players: List<Player>) = Game(players, deck, discard, rounds)
    private fun withRounds(rounds: GameRounds) = Game(players, deck, discard, rounds)
    fun playerInGame(player: Player): Player = playerInGame(player.name)
    fun playerInGame(player: String): Player = players.firstOrNull { it.name == player }
        ?: throw IllegalArgumentException("unknown player $player")

    companion object {
        fun init(): Game = Game(
            listOf(
                Player("player 1", PlayerCards()),
                Player("player 2", PlayerCards()),
            ),
            Deck((1..52).map { Card(it) }),
            Discard(),
            GameRounds(listOf()),
        )
    }
}

class Player(val name: String, val cards: PlayerCards, val readyToPlay: ReadyToPlay = NOT_READY_TO_PLAY) {

    fun takeCards(newCards: List<Card>) = Player(name, cards.takeCards(newCards), readyToPlay)
    fun readyToPlay(readyToPlay: ReadyToPlay) = Player(name, cards, readyToPlay)
    fun prepareAttack(cards: List<Card>): Player = Player(name, this.cards.prepareAttack(cards), readyToPlay)
    fun prepareDefend(cards: List<Card>): Player = Player(name, this.cards.prepareDefend(cards), readyToPlay)
    fun checkCanPlayCards(cards: List<Card>): Player = this.also {
        (cards - it.cards.hand).also {
            if (it.isNotEmpty()) {
                throw IllegalArgumentException("player $name can't play cards which are not in hand: $it")
            }
        }
    }

    fun checkTurn(turn: ReadyToPlay): Player = this.also {
        if (readyToPlay != turn) {
            throw IllegalStateException("player $name can't play $turn now!")
        }
    }
}

class GameRounds(val rounds: List<GameRound>) {
    fun newRound() = GameRounds(rounds + GameRound(listOf()))

    fun currentRound() = rounds.lastOrNull() ?: throw IllegalStateException("no current round - game not started")

    fun played(player: Player, action: ReadyToPlay): GameRounds =
        GameRounds(rounds.dropLast(1) + currentRound().played(player, action))
}

class GameRound(val actions: List<PlayerAction>) {
    fun played(player: Player, action: ReadyToPlay) = GameRound(actions + PlayerAction(player.name, action))
}

class PlayerAction(val playerName: String, val action: ReadyToPlay)

class PlayerCards(
    val hand: List<Card> = listOf(),
    val attack: List<Card> = listOf(),
    val defense: List<Card> = listOf(),
) {
    fun takeCards(newCards: List<Card>) = PlayerCards(hand + newCards, attack, defense)
    fun prepareAttack(cards: List<Card>) = PlayerCards(hand - cards, attack + cards, defense)
    fun prepareDefend(cards: List<Card>) = PlayerCards(hand - cards, attack, defense + cards)
}

enum class ReadyToPlay {
    NOT_READY_TO_PLAY,
    ATTACK,
    DEFEND,
    WAIT_FOR_BATTLE,
}

data class Card(val id: Int)

class Deck(val cards: List<Card> = listOf()) {
    fun drawCards(count: Int): Pair<Deck, List<Card>> = Deck(cards.drop(count)) to cards.take(count)
}

class Discard(val cards: List<Card> = listOf())
