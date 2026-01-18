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
        // TODO: Add validation back when implementing real authentication
        // For now, allow empty credentials to proceed directly to the app
        _user.value = User(
            uid = "demo",
            email = email.ifBlank { "demo@example.com" },
            displayName = "Demo User"
        )
    }

    override suspend fun signUp(email: String, password: String) {
        signIn(email, password)
    }

    override suspend fun signOut() {
        _user.value = null
    }

    override suspend fun validateSession(): Boolean = _user.value != null
}
