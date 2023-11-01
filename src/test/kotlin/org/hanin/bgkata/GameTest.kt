package org.hanin.bgkata

import org.hanin.bgkata.ReadyToPlay.*
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.*

class GameTest {
    @Test
    fun `should have 2 players by default`() {
        with(Game.init()) {
            expectThat(players).hasSize(2)
            expectThat(players[0].cards.hand).isEmpty()
        }
    }

    @Test
    fun `should have no cards in hand by default`() {
        with(Game.init()) {
            players.forEach {
                expectThat(it.cards.hand).isEmpty()
            }
        }
    }

    @Test
    fun `should start by dealing 5 cards per player`() {
        with(Game.init().start()) {
            players.forEach {
                expectThat(it.cards.hand).hasSize(5)
            }
        }
    }

    @Test
    fun `should players have different cards at start`() {
        with(Game.init().start()) {
            players.forEach { player ->
                val otherCards = players.filter { it != player }.flatMap { it.cards.hand }
                expectThat(player.cards.hand.intersect(otherCards)).isEmpty()
            }
        }
    }

    @Test
    fun `should start with one player's turn`() {
        with(Game.init().start()) {
            expectThat(players.filter { it.readyToPlay != NOT_READY_TO_PLAY }).hasSize(1)
        }
    }

    @Test
    fun `should not allow to play attack when not ready`() {
        with(Game.init().start()) {
            expectCatching {
                players.first { it.readyToPlay == NOT_READY_TO_PLAY }.also {
                    playAttack(it, it.cards.hand)
                }
            }.isFailure()
        }
    }

    @Test
    fun `should not allow to play with cards from other player`() {
        with(Game.init().start()) {
            expectCatching {
                players.first { it.readyToPlay == ATTACK }.also {
                    playAttack(it, players.first { it.readyToPlay != ATTACK }.cards.hand)
                }
            }.isFailure()
        }
    }

    @Test
    fun `should cards played in attack be removed from hand`() {
        with(Game.init().start()) {
            val attackingPlayer = players.first { it.readyToPlay == ATTACK }
            val handCount = attackingPlayer.cards.hand.size
            val cardsToPlayInAttack = attackingPlayer.cards.hand.take(3)
            playAttack(attackingPlayer, cardsToPlayInAttack).also {
                expectThat(it.playerInGame(attackingPlayer).cards.hand).hasSize(handCount - cardsToPlayInAttack.size)
                expectThat(it.playerInGame(attackingPlayer).cards.hand.intersect(cardsToPlayInAttack)).isEmpty()
            }
        }
    }

    @Test
    fun `should cards played in attack be placed in attack`() {
        with(Game.init().start()) {
            val attackingPlayer = players.first { it.readyToPlay == ATTACK }
            val cardsToPlayInAttack = attackingPlayer.cards.hand.take(3)
            playAttack(attackingPlayer, cardsToPlayInAttack).also {
                expectThat(it.playerInGame(attackingPlayer).cards.attack).isEqualTo(cardsToPlayInAttack)
            }
        }
    }

    @Test
    fun `should turn move to other player defense when attack is done`() {
        with(Game.init().start()) {
            val attackingPlayer = players.first { it.readyToPlay == ATTACK }
            with(playAttack(attackingPlayer, listOf())) {
                expectThat(playerInGame(attackingPlayer).readyToPlay).isEqualTo(NOT_READY_TO_PLAY)
                expectThat(players.filter { it.readyToPlay != NOT_READY_TO_PLAY }).hasSize(1).first().and {
                    get { readyToPlay }.isEqualTo(DEFEND)
                }
            }
        }
    }

    @Test
    fun `should other player be able to prepare defense after other attack`() {
        with(Game.init().start()) {
            val attackingPlayer = players.first { it.readyToPlay == ATTACK }
            with(playAttack(attackingPlayer, listOf())) {
                val defendingPlayer = players.first { it.readyToPlay == DEFEND }
                val defendingCards = defendingPlayer.cards.hand.take(2)
                playDefend(defendingPlayer, defendingCards).also { game ->
                    expectThat(game.playerInGame(defendingPlayer).cards).and {
                        get { hand }.doesNotContain(defendingCards)
                        get { attack }.isEmpty()
                        get { defense }.containsExactly(defendingCards)
                    }
                    expectThat(game.playerInGame(defendingPlayer).readyToPlay).isEqualTo(ATTACK)
                }
            }
        }
    }

    @Test
    fun `should prepare a full turn battle`() {
        Game.init().start()
            .attackWith("player 1", 3)
            .defendWith("player 2", 3)
            .attackWith("player 2", 2)
            .defendWith("player 1", 2).also { game ->
                expectThat(game.playerInGame("player 1").cards).and {
                    get { hand }.isEmpty()
                    get { attack }.hasSize(3)
                    get { defense }.hasSize(2)
                }
                expectThat(game.playerInGame("player 2").cards).and {
                    get { hand }.isEmpty()
                    get { attack }.hasSize(2)
                    get { defense }.hasSize(3)
                }
                game.players.forEach {
                    expectThat(it.readyToPlay).describedAs("${it.name} turn").isEqualTo(WAIT_FOR_BATTLE)
                }
            }
    }
}

private fun Game.attackWith(playerName: String, cardsCount: Int): Game {
    val player = players.first { it.readyToPlay == ATTACK }
    expectThat(player.name).describedAs("player to attack").isEqualTo(playerName)
    val cardsToPlay = player.cards.hand.take(cardsCount)
    return playAttack(player, cardsToPlay)
}
private fun Game.defendWith(playerName: String, cardsCount: Int): Game {
    val player = players.first { it.readyToPlay == DEFEND }
    expectThat(player.name).describedAs("player to defend").isEqualTo(playerName)
    val cardsToPlay = player.cards.hand.take(cardsCount)
    return playDefend(player, cardsToPlay)
}