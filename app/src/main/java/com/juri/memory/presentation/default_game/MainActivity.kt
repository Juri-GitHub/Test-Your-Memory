package com.juri.memory.presentation.default_game

import android.animation.ArgbEvaluator
import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.juri.memory.R
import com.juri.memory.domain.game.BoardSize
import com.juri.memory.domain.game.MemoryGame
import com.juri.memory.common.Constants.CHOSEN_BOARD_SIZE
import com.juri.memory.common.Constants.CREATED_GAME_NAME
import com.juri.memory.common.Constants.CUSTOM_GAME_CODE
import com.juri.memory.domain.model.UserImagesList
import com.juri.memory.presentation.custom_game.CustomActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.squareup.picasso.Picasso

class MainActivity : AppCompatActivity() {

    private lateinit var clRoot: ConstraintLayout
    private lateinit var rvBoard: RecyclerView
    private lateinit var tvNumMoves: TextView
    private lateinit var tvNumPairs: TextView
    private lateinit var adapter: MemoryBoardAdapter

    private lateinit var memoryGame: MemoryGame
    private var boardSize: BoardSize = BoardSize.EASY
    private var customGameName: String? = null
    private var customGameImages: List<String>? = null

    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clRoot = findViewById(R.id.clRoot)
        rvBoard = findViewById(R.id.rvBoard)
        tvNumMoves = findViewById(R.id.tvNumMoves)
        tvNumPairs = findViewById(R.id.tvNumPairs)

        setUpGame()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.mi_refresh -> {
                if (memoryGame.getNumMoves() > 0 && !memoryGame.alreadyWon()) {
                    showAlertDialog(
                        "You will lose your current progress. Do you really want to continue?",
                        null
                    ) {
                        setUpGame()
                    }
                } else {
                    setUpGame()
                }
            }
            R.id.mi_level -> {
                showSizeDialog()
                return true
            }
            R.id.mi_custom -> {
                showCustomGameDialog()
                return true
            }
            R.id.mi_download -> {
                showDownloadDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showAlertDialog(
        title: String,
        view: View?,
        positiveClickListener: View.OnClickListener
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(view)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Okay") { _, _ ->
                positiveClickListener.onClick(null)
            }
            .setCancelable(false)
            .show()
    }

    private fun showSizeDialog() {
        val boardSizeView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_board_size, null)
        val radioGroup = boardSizeView.findViewById<RadioGroup>(R.id.rbGroup)

        when (boardSize) {
            BoardSize.EASY -> radioGroup.check(R.id.rbEasy)
            BoardSize.MEDIUM -> radioGroup.check(R.id.rbMedium)
            BoardSize.HARD -> radioGroup.check(R.id.rbHard)
        }

        showAlertDialog(
            "Choose game level.",
            boardSizeView
        ) {
            boardSize = when (radioGroup.checkedRadioButtonId) {
                R.id.rbEasy -> BoardSize.EASY
                R.id.rbMedium -> BoardSize.MEDIUM
                else -> BoardSize.HARD
            }
            if (memoryGame.getNumMoves() > 0 && !memoryGame.alreadyWon()) {
                showAlertDialog(
                    "You will lose your current progress. Do you really want to continue?",
                    null
                ) {
                    customGameName = null
                    customGameImages = null
                    setUpGame()
                }
            } else {
                customGameName = null
                customGameImages = null
                setUpGame()
            }
        }
    }

    private fun showCustomGameDialog() {
        val boardSizeView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_board_size, null)
        val radioGroup = boardSizeView.findViewById<RadioGroup>(R.id.rbGroup)
        showAlertDialog(
            title = "Create your own game.",
            view = boardSizeView
        ) {
            val chosenBoardSize =
                when (radioGroup.checkedRadioButtonId) {
                    R.id.rbEasy -> BoardSize.EASY
                    R.id.rbMedium -> BoardSize.MEDIUM
                    else -> BoardSize.HARD
                }
            val intent = Intent(this, CustomActivity::class.java)
            intent.putExtra(CHOSEN_BOARD_SIZE, chosenBoardSize)
            startActivityForResult(intent, CUSTOM_GAME_CODE)
        }
    }

    private fun setUpGame() {
        supportActionBar?.title = customGameName ?: getString(R.string.app_name)
        when (boardSize) {
            BoardSize.EASY -> {
                tvNumMoves.text = "Moves: 0"
                tvNumPairs.text = "Pairs: 0 / 4"
            }
            BoardSize.MEDIUM -> {
                tvNumMoves.text = "Moves: 0"
                tvNumPairs.text = "Pairs: 0 / 9"
            }
            BoardSize.HARD -> {
                tvNumMoves.text = "Moves: 0"
                tvNumPairs.text = "Pairs: 0 / 12"
            }
        }

        tvNumPairs.setTextColor(
            ContextCompat.getColor(this, R.color.progress_none)
        )
        memoryGame = MemoryGame(boardSize, customGameImages)

        rvBoard.setHasFixedSize(true)
        rvBoard.layoutManager = GridLayoutManager(this, boardSize.getWidth())
        adapter = MemoryBoardAdapter(
            this,
            boardSize = boardSize,
            cards = memoryGame.cards,
            object : MemoryBoardAdapter.CardClickListener {
                override fun onCardClick(position: Int) {
                    updateGameWithFlip(position)
                }
            })
        rvBoard.adapter = adapter
    }

    private fun updateGameWithFlip(position: Int) {
        if (memoryGame.alreadyWon()) {
            Snackbar.make(clRoot, "You've already won!", Snackbar.LENGTH_LONG).show()
            return
        }
        if (memoryGame.cardAlreadyMatched(position)) {
            Snackbar.make(clRoot, "The card is already matched!", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (memoryGame.alreadyFacedUp(position)) {
            Snackbar.make(clRoot, "The card can't be flipped back!", Snackbar.LENGTH_SHORT).show()
            return
        }
        if (memoryGame.flipCard(position)) {
            val color = ArgbEvaluator().evaluate(
                memoryGame.numPairsFound.toFloat() / boardSize.getNumPairs(),
                ContextCompat.getColor(this, R.color.progress_none),
                ContextCompat.getColor(this, R.color.progress_full)
            ) as Int
            tvNumPairs.setTextColor(color)
            tvNumPairs.text = "Pairs: ${memoryGame.numPairsFound} / ${boardSize.getNumPairs()}"

            if (memoryGame.alreadyWon()) {
                Snackbar.make(clRoot, "Congrats! You won the game.", Snackbar.LENGTH_LONG).show()
            }
        }
        tvNumMoves.text = "Moves: ${memoryGame.getNumMoves()}"
        adapter.notifyDataSetChanged()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != CUSTOM_GAME_CODE || resultCode != Activity.RESULT_OK || data == null) {
            return
        }
        val gameName = data.getStringExtra(CREATED_GAME_NAME)
        if (gameName != null) {
            downloadGame(gameName)
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun downloadGame(gameName: String) {
        db.collection("games").document(gameName).get()
            .addOnSuccessListener { document ->
                val userImagesList = document.toObject(UserImagesList::class.java)
                if (userImagesList?.images == null) {
                    Toast.makeText(
                        this,
                        "There is no such game named '$gameName'",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                val numCards = userImagesList.images.size * 2
                boardSize = BoardSize.getBoardSize(numCards)
                customGameName = gameName
                customGameImages = userImagesList.images
                for (imageUrl in userImagesList.images) {
                    Picasso.get().load(imageUrl).fetch()
                }
                Snackbar.make(
                    clRoot,
                    "You're now playing '$customGameName'",
                    Snackbar.LENGTH_LONG
                ).show()

                customGameName = gameName
                setUpGame()
            }
    }

    private fun showDownloadDialog() {
        val downloadGameView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_download_board, null)
        showAlertDialog(
            title = "Download your friend's game.",
            view = downloadGameView
        ) {
            val etDownloadGame = downloadGameView.findViewById<EditText>(R.id.etDownloadGame)
            val gameToDownload = etDownloadGame.text.toString().trim()
            downloadGame(gameToDownload)
        }
    }
}