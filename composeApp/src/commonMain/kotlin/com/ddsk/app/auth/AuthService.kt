package com.ddsk.app.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class User(
    val uid: String,
    val email: String,
    val displayName: String?
)

interface AuthService {
    val currentUser: StateFlow<User?>

    suspend fun signIn(email: String, password: String)
    suspend fun signUp(email: String, password: String)
    suspend fun signOut()
    suspend fun validateSession(): Boolean
}

class FirebaseAuthService : AuthService {
    // This will be implemented with dev.gitlive:firebase-auth
    // For now, we stub it to allow the project to build/check architecture

    private val _user = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _user

    override suspend fun signIn(email: String, password: String) {
        // Implementation TODO
    }

    override suspend fun signUp(email: String, password: String) {
        // Implementation TODO
    }

    override suspend fun signOut() {
        _user.value = null
    }

    override suspend fun validateSession(): Boolean {
        return _user.value != null
    }
}
