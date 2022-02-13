package com.juri.memory.presentation.custom_game

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.juri.memory.R
import com.juri.memory.domain.game.BoardSize
import com.juri.memory.common.Constants.CHOSEN_BOARD_SIZE
import com.juri.memory.common.Constants.CREATED_GAME_NAME
import com.juri.memory.common.Constants.CUSTOM_PHOTO_PICK_CODE
import com.juri.memory.common.Constants.MAX_GAME_LENGTH
import com.juri.memory.common.Constants.MIN_GAME_LENGTH
import com.juri.memory.common.Constants.READ_STORAGE_PERMISSION
import com.juri.memory.common.Constants.READ_STORAGE_PERMISSION_CODE
import com.juri.memory.utils.BitmapScaler
import com.juri.memory.utils.isPermissionGranted
import com.juri.memory.utils.requestPermissions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.ByteArrayOutputStream

class CustomActivity : AppCompatActivity() {

    private lateinit var boardSize: BoardSize
    private var numImagesRequired: Int = -1

    private lateinit var rvImagePicker: RecyclerView
    private lateinit var etGameName: EditText
    private lateinit var btnSave: Button
    private lateinit var adapter: ImagePickerAdapter
    private lateinit var pbUploading: ProgressBar

    private val chosenImageUri = mutableListOf<Uri>()

    private val storage = Firebase.storage
    private val db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom)

        rvImagePicker = findViewById(R.id.rvImagePicker)
        etGameName = findViewById(R.id.etGameName)
        btnSave = findViewById(R.id.btnSave)
        pbUploading = findViewById(R.id.pbUploading)
        boardSize = intent.getSerializableExtra(CHOSEN_BOARD_SIZE) as BoardSize

        btnSave.setOnClickListener {
            saveDataToFirebase()
        }

        etGameName.filters = arrayOf(InputFilter.LengthFilter(MAX_GAME_LENGTH))
        etGameName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                btnSave.isEnabled = enableTheSaveButton()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        adapter = ImagePickerAdapter(
            this,
            chosenImageUri,
            boardSize,
            object : ImagePickerAdapter.ImageClickListener {
                override fun onPlaceHolderClick() {
                    if (isPermissionGranted(
                            this@CustomActivity,
                            READ_STORAGE_PERMISSION
                        )
                    ) {
                        launchGalleryToPick()
                    } else {
                        requestPermissions(
                            this@CustomActivity,
                            READ_STORAGE_PERMISSION,
                            READ_STORAGE_PERMISSION_CODE
                        )
                    }
                }
            }
        )
        rvImagePicker.adapter = adapter

        rvImagePicker.setHasFixedSize(true)
        rvImagePicker.layoutManager = GridLayoutManager(this, boardSize.getWidth())

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        numImagesRequired = boardSize.getNumPairs()
        supportActionBar?.title = "Choose pics (0 / $numImagesRequired)"
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == READ_STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                launchGalleryToPick()
            } else {
                Toast.makeText(
                    this,
                    "In order to create a custom game, you need to provide access to the gallery.",
                    Toast.LENGTH_LONG
                )
                    .show()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CUSTOM_PHOTO_PICK_CODE || resultCode != Activity.RESULT_OK || data == null) {
            return
        }
        val singleUri = data.data
        val uris = data.clipData

        if (uris != null) {
            for (i in 0 until uris.itemCount) {
                val uri = uris.getItemAt(i)
                if (chosenImageUri.size < numImagesRequired) {
                    chosenImageUri.add(uri.uri)
                }
            }
        } else if (singleUri != null) {
            chosenImageUri.add(singleUri)
        }
        adapter.notifyDataSetChanged()
        supportActionBar?.title = "Choose pics (${chosenImageUri.size} / $numImagesRequired)"
        btnSave.isEnabled = enableTheSaveButton()
    }

    private fun enableTheSaveButton(): Boolean {
        if (chosenImageUri.size != numImagesRequired) {
            return false
        }
        if (etGameName.text.length < MIN_GAME_LENGTH) {
            return false
        }
        return true
    }

    private fun launchGalleryToPick() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(
            Intent.createChooser(intent, "Choose pics"),
            CUSTOM_PHOTO_PICK_CODE
        )
    }

    private fun getImageByteArray(photoUri: Uri): ByteArray {
        val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(contentResolver, photoUri)
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(contentResolver, photoUri)
        }
        val scaledBitmap = BitmapScaler.scaleToFitHeight(originalBitmap, 250)
        val byteOutputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 60, byteOutputStream)
        return byteOutputStream.toByteArray()
    }

    private fun saveDataToFirebase() {
        btnSave.isEnabled = false
        val customGameName = etGameName.text.toString()
        db.collection("games").document(customGameName).get()
            .addOnSuccessListener { document ->
                if (document != null && document.data != null) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Name already taken")
                        .setMessage("A game named $customGameName already exists. Please pick another one.")
                        .setPositiveButton("Okay", null)
                        .show()
                    btnSave.isEnabled = true
                } else {
                    uploadImages(customGameName)
                }
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Something went wrong.",
                    Toast.LENGTH_SHORT
                ).show()
                btnSave.isEnabled = true
            }
    }

    private fun uploadImages(gameName: String) {
        pbUploading.visibility = View.VISIBLE
        var didEncounterError = false
        val uploadedImageUrls = mutableListOf<String>()

        for ((index, photoUri) in chosenImageUri.withIndex()) {
            val imageByteArray = getImageByteArray(photoUri)
            val filePath = "images/$gameName/${System.currentTimeMillis()}-$index.jpg"
            val photoReference = storage.reference.child(filePath)
            photoReference.putBytes(imageByteArray)
                .continueWithTask {
                    photoReference.downloadUrl
                }
                .addOnCompleteListener { downloadUrlTask ->
                    if (!downloadUrlTask.isSuccessful) {
                        didEncounterError = true
                        return@addOnCompleteListener
                    }
                    if (didEncounterError) {
                        pbUploading.visibility = View.GONE
                        Toast.makeText(
                            this,
                            "Failed to save photos.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addOnCompleteListener
                    }
                    val downloadUrl = downloadUrlTask.result.toString()
                    uploadedImageUrls.add(downloadUrl)
                    pbUploading.progress = uploadedImageUrls.size * 100 / chosenImageUri.size

                    if (uploadedImageUrls.size == chosenImageUri.size) {
                        handleImagesUploaded(gameName, uploadedImageUrls)
                    }
                }
        }
    }

    private fun handleImagesUploaded(
        gameName: String, imageUrls: MutableList<String>
    ) {
        db.collection("games").document(gameName)
            .set(mapOf("images" to imageUrls))
            .addOnCompleteListener { gameCreationTask ->
                pbUploading.visibility = View.GONE
                if (!gameCreationTask.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Failed to create game.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                MaterialAlertDialogBuilder(this)
                    .setMessage("Upload complete! Your $gameName is ready to play.")
                    .setPositiveButton("Sounds good") { _, _ ->
                        val resultData = Intent()
                        resultData.putExtra(CREATED_GAME_NAME, gameName)
                        setResult(Activity.RESULT_OK, resultData)
                        finish()
                    }.show()
            }
    }
}