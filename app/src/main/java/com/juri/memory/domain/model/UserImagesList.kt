package com.juri.memory.domain.model

import com.google.firebase.firestore.PropertyName

data class UserImagesList(
    @PropertyName("images") val images: List<String>? = null
)
