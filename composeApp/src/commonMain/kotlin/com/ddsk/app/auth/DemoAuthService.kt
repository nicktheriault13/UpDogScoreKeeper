package com.ddsk.app.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Demo-mode auth used on platforms where Firebase isn't wired.
 */
class DemoAuthService : AuthService {

    private val _user = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _user

    override suspend fun signIn(email: String, password: String) {
        require(email.isNotBlank()) { "Email is required" }
        require(password.isNotBlank()) { "Password is required" }
        _user.value = User(uid = "demo", email = email, displayName = "Demo")
    }

    override suspend fun signUp(email: String, password: String) {
        signIn(email, password)
    }

    override suspend fun signOut() {
        _user.value = null
    }

    override suspend fun validateSession(): Boolean = _user.value != null
}
