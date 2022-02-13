package com.juri.memory.domain.game

import com.juri.memory.domain.model.MemoryCard
import com.juri.memory.utils.defaultIcons

class MemoryGame(
    private val boardSize: BoardSize,
    private val customImages: List<String>?
) {

    val cards: List<MemoryCard>
    var numPairsFound: Int = 0
    private var moves: Int = 0
    var positionOfSelectedCard: Int? = null

    init {
        if (customImages == null) {
            val chosenImages = defaultIcons.shuffled().take(boardSize.getNumPairs())
            val randomizedImages = (chosenImages + chosenImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it) }
        } else {
            val randomizedImages = (customImages + customImages).shuffled()
            cards = randomizedImages.map { MemoryCard(it.hashCode(), it) }
        }
    }

    fun flipCard(position: Int): Boolean {
        moves++
        val card = cards[position]
        var foundMatch = false
        if (positionOfSelectedCard == null) {
            restoreCards()
            positionOfSelectedCard = position
        } else {
            foundMatch = checkCards(positionOfSelectedCard!!, position)
            positionOfSelectedCard = null
        }
        card.isFacedUp = !card.isFacedUp
        return foundMatch
    }

    private fun restoreCards() {
        for (card in cards) {
            if (!card.isMatched) {
                card.isFacedUp = false
            }
        }
    }

    private fun checkCards(position1: Int, position2: Int): Boolean {
        if (cards[position1].identifier != cards[position2].identifier) {
            return false
        }
        cards[position1].isMatched = true
        cards[position2].isMatched = true
        numPairsFound++
        return true
    }

    fun cardAlreadyMatched(position: Int): Boolean {
        return cards[position].isMatched
    }

    fun alreadyWon(): Boolean {
        return numPairsFound == boardSize.getNumPairs()
    }

    fun alreadyFacedUp(position: Int): Boolean {
        return cards[position].isFacedUp
    }

    fun getNumMoves(): Int {
        return moves / 2
    }
}