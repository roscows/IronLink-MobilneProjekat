package com.example.ironlink.data

data class User(
    val uid: String = "",
    val fullName: String = "",
    val email: String = "",
    val points: Int = 0,
    val profileImageUrl: String? = null
)