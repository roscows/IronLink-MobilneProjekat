package com.example.ironlink.ui.auth

import androidx.lifecycle.ViewModel
import com.example.ironlink.data.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun register(
        email: String,
        password: String,
        fullName: String,
        profileImageUrl: String? = null
    ) {
        try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val userId = result.user?.uid ?: return

            val user = User(
                uid = userId,
                fullName = fullName,
                email = email,
                points = 0,
                profileImageUrl = profileImageUrl
            )

            firestore.collection("users")
                .document(userId)
                .set(user)
                .await()

        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun login(email: String, password: String) {
        try {
            auth.signInWithEmailAndPassword(email, password).await()
        } catch (e: Exception) {
            throw e
        }
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUser() = auth.currentUser

    suspend fun isUserDataAvailable(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        return try {
            val document = firestore.collection("users").document(userId).get().await()
            document.exists()
        } catch (e: Exception) {
            false
        }
    }
}