package com.juri.memory.presentation.default_game

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.juri.memory.R
import com.juri.memory.domain.game.BoardSize
import com.juri.memory.domain.model.MemoryCard
import com.squareup.picasso.Picasso
import kotlin.math.min

class MemoryBoardAdapter(
    private val context: Context,
    private val boardSize: BoardSize,
    private val cards: List<MemoryCard>,
    private val cardClickListener: CardClickListener
) : RecyclerView.Adapter<MemoryBoardAdapter.ViewHolder>() {

    companion object {
        private const val MARGIN_SIZE = 10
    }

    interface CardClickListener {
        fun onCardClick(position: Int)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val width = parent.width / boardSize.getWidth() - (2 * MARGIN_SIZE)
        val height = parent.height / boardSize.getHeight() - (2 * MARGIN_SIZE)
        val cardSideLength = min(width, height)
        val view = LayoutInflater.from(context).inflate(R.layout.memory_card, parent, false)
        val layoutParams =
            view.findViewById<CardView>(R.id.cardView).layoutParams as ViewGroup.MarginLayoutParams
        layoutParams.width = cardSideLength
        layoutParams.height = cardSideLength
        layoutParams.setMargins(MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE, MARGIN_SIZE)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int {
        return boardSize.numCards
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageButton = itemView.findViewById<ImageButton>(R.id.imageButton)

        fun bind(position: Int) {
            val currentCard = cards[position]
            if (currentCard.isFacedUp) {
                if (currentCard.imageUrl != null) {
                    Picasso.get()
                        .load(currentCard.imageUrl)
                        .placeholder(R.drawable.ic_image)
                        .into(imageButton)
                } else {
                    imageButton.setImageResource(currentCard.identifier)
                }
            } else {
                imageButton.setImageResource(R.drawable.question_mark)
            }

            imageButton.alpha = if (currentCard.isMatched) 0.4f else 1.0f
            val colorStateList = if (currentCard.isMatched) ContextCompat.getColorStateList(
                context,
                R.color.grey_color
            ) else null
            ViewCompat.setBackgroundTintList(imageButton, colorStateList)

            imageButton.setOnClickListener {
                cardClickListener.onCardClick(position)
            }
        }
    }
}
